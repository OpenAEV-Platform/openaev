package io.openaev.service;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.EXPECTATIONS_VULNERABILITY_COLLECTOR_ID;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.expectation.DetectionExpectation.detectionExpectationForAssetGroup;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.expectation.ManualExpectation.manualExpectationForAssetGroup;
import static io.openaev.expectation.PreventionExpectation.preventionExpectationForAssetGroup;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.InjectExpectationUtils.computeScores;
import static io.openaev.service.InjectExpectationUtils.expectationConverter;
import static io.openaev.utils.AgentUtils.getActiveAgents;
import static io.openaev.utils.AgentUtils.getPrimaryAgents;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openaev.utils.ExpectationSignatureUtils.convertToInjectExpectationSignatures;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAssetGroup;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.*;
import static java.time.Instant.now;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.WorkflowUpdateEvent;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.SessionHelper;
import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.specification.InjectExpectationSpecification;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.DetectionExpectation;
import io.openaev.expectation.Expectation;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.expectation.ExpectationType;
import io.openaev.expectation.ManualExpectation;
import io.openaev.expectation.PreventionExpectation;
import io.openaev.expectation.VulnerabilityExpectation;
import io.openaev.injectors.common.model.BaseInjectContent;
import io.openaev.output_processor.CVEOutputProcessor;
import io.openaev.rest.atomic_testing.form.InjectExpectationAgentOutput;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.expectation.ExpectationBehavior;
import io.openaev.utils.TargetType;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InjectExpectationService {

  public static final String SUCCESS = "Success";
  public static final String PENDING = "Pending";
  public static final String COLLECTOR = "collector";
  public static final String SECURITY_PLATFORM = "security-platform";
  private static final String EXPECTATION_SOURCE_TYPE_AUTOMATIC = "automatic";

  /**
   * Upper bound for the collector-polled "not filled" queries. Collectors poll periodically (oldest
   * first), so anything beyond the bound is returned on a subsequent poll.
   */
  private static final int NOT_FILLED_FETCH_LIMIT = 10_000;

  private final InjectExpectationRepository injectExpectationRepository;
  private final CollectorService collectorService;
  private final SecurityPlatformRepository securityPlatformRepository;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final InjectExpectationLockService injectExpectationLockService;
  private final AssetGroupService assetGroupService;
  private final InjectService injectService;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final Optional<AuditLogger> auditLogger;

  @Resource protected ObjectMapper mapper;

  private final List<ExpectationBehavior> behaviors;

  private ExpectationBehavior resolveFor(BaseInjectExpectation expectation) {
    return behaviors.stream()
        .filter(b -> b.supports(expectation))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No behavior found for " + expectation.getClass().getSimpleName()));
  }

  // -- BEHAVIOR-BASED EXPECTATION CREATION --

  /**
   * Creates and persists inject expectations for each target and for each kind of expectations
   *
   * <p>Dead code — not wired into any executor yet. Part of the {@code InjectExpectation}
   * refactoring (Vertical 2).
   *
   * @param executableInject the executable inject to process
   * @throws JsonProcessingException if the inject content cannot be parsed
   */
  @Transactional(rollbackFor = Exception.class)
  public void computeAndSaveExpectationsUsingBehaviors(
      ExecutableInject executableInject,
      List<Expectation> expectationsFromInjectContent,
      @Nullable String implantType)
      throws JsonProcessingException {

    if (expectationsFromInjectContent.isEmpty()) {
      return;
    }

    List<BaseInjectExpectation> injectExpectationsToApply =
        expectationsFromInjectContent.stream()
            .map(
                expectation ->
                    expectationConverter(
                        executableInject, expectation, expectationPropertiesConfig))
            .toList();

    injectExpectationsToApply.forEach(
        expectationTemplate -> {
          ExpectationBehavior behavior = resolveFor(expectationTemplate);
          behavior.initializeAndSaveInjectExpectationsFromExecutableInject(
              executableInject, expectationTemplate, implantType);
        });
  }

  /**
   * Updates an inject expectation
   *
   * <p>Dead code — not wired into any executor yet. Part of the {@code InjectExpectation}
   * refactoring (Vertical 2).
   *
   * @param expectationId
   * @param input
   * @return
   */
  @Transactional(rollbackFor = Exception.class)
  public BaseInjectExpectation updateInjectExpectationUsingBehaviors(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    BaseInjectExpectation injectExpectation = this.findInjectExpectation(expectationId);
    if (injectExpectation == null) {
      throw new ElementNotFoundException("Inject expectation not found for id: " + expectationId);
    }

    ExpectationBehavior behavior = resolveFor(injectExpectation);
    List<? extends BaseInjectExpectation> updatedLeaves =
        behavior.applyResultToLeaves(injectExpectation, input);
    List<? extends BaseInjectExpectation> updatedParents =
        behavior.recomputeParentScores(injectExpectation);

    List<BaseInjectExpectation> allUpdated = new ArrayList<>(updatedLeaves);
    allUpdated.addAll(updatedParents);
    injectExpectationRepository.saveAll(allUpdated);

    return injectExpectation;
  }

  // -- CRUD --

  /**
   * Finds an inject expectation by its ID.
   *
   * @param injectExpectationId the ID of the inject expectation to find
   * @return the found inject expectation
   * @throws ElementNotFoundException if no expectation is found with the given ID
   */
  public BaseInjectExpectation findInjectExpectation(@NotBlank final String injectExpectationId) {
    return this.injectExpectationRepository
        .findById(injectExpectationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * Retrieves all inject expectations for a given inject ID, scoped to the current tenant.
   *
   * <p>Pre-loads agent / asset / assetGroup relations of technical expectations via a JOIN FETCH
   * JPQL query before issuing the native query, so the Hibernate session cache serves already
   * hydrated instances and avoids N+1 lazy loads when callers access those relations.
   *
   * @param injectId the inject ID
   * @return the list of expectations for the inject
   */
  @Transactional(readOnly = true)
  public List<BaseInjectExpectation> findAllByInjectId(@NotBlank final String injectId) {
    String tenantId = TenantContext.getCurrentTenant();
    return this.injectExpectationRepository.findTechnicalByInjectIdWithRelations(
        injectId, tenantId);
  }

  // -- UPDATE FROM UI --

  /**
   * Updates an inject expectation
   *
   * @param expectationId the ID of the expectation to update
   * @param input the update input containing the new data
   * @return the updated inject expectation
   * @throws IllegalArgumentException if trying to update an Asset Group expectation directly
   */
  @WorkflowUpdateEvent(expectationIds = "#expectationId")
  public BaseInjectExpectation updateInjectExpectation(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    BaseInjectExpectation baseInjectExpectation = this.findInjectExpectation(expectationId);

    if (baseInjectExpectation instanceof TableTopInjectExpectation tableTopInjectExpectation) {
      String result =
          ExpectationType.label(
              tableTopInjectExpectation.getType(),
              tableTopInjectExpectation.getExpectedScore(),
              input.getScore());
      computeInjectExpectationForHumanResponse(tableTopInjectExpectation, input, result);
      TableTopInjectExpectation updated =
          this.injectExpectationRepository.save(tableTopInjectExpectation);
      propagateHumanResponseExpectation(updated, result);
      return updated;

    } else if (baseInjectExpectation instanceof TechnicalInjectExpectation technicalExpectation
        && List.of(
                DETECTION,
                PREVENTION,
                // NB: the explicit ExpectationType.VULNERABILITY static import shadows the
                // EXPECTATION_TYPE.* wildcard one, hence the qualified reference.
                BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY)
            .contains(baseInjectExpectation.getType())) {
      // Block down computation on asset group
      if (isAssetGroupExpectation(technicalExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Allow down computation on asset. Non-endpoint assets (AI targets, ...) have no agents and
      // are treated as agentless.
      Asset unproxied = (Asset) Hibernate.unproxy(technicalExpectation.getAsset());
      List<Agent> agents =
          (unproxied instanceof Endpoint endpoint) ? getPrimaryAgents(endpoint) : List.of();
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(technicalExpectation) && !isAgentless) {
        List<TechnicalInjectExpectation> expectationsForAgents =
            getAgentsExpectationsForAsset(technicalExpectation);
        expectationsForAgents.forEach(
            e -> computeInjectExpectationForAgentOrAssetAgentless(e, input));
        this.injectExpectationRepository.saveAll(expectationsForAgents);
        propagateTechnicalExpectation(technicalExpectation, isAgentless, null);
        return technicalExpectation;
        // Computation on agent or asset agentless
      } else {
        computeInjectExpectationForAgentOrAssetAgentless(technicalExpectation, input);
        TechnicalInjectExpectation updated =
            this.injectExpectationRepository.save(technicalExpectation);
        propagateTechnicalExpectation(updated, isAgentless, null);
        return updated;
      }
    }
    return baseInjectExpectation;
  }

  // -- DELETE RESULT FROM UI --

  /**
   * Deletes a specific result from an inject expectation.
   *
   * <p>For a detection/prevention expectation at asset level whose asset has agents, the rows
   * displayed in the UI are the aggregation of the agents' security platform results: the deletion
   * cascades to every agent expectation of the asset (removing that source's result on each), then
   * the asset and asset group scores are recomputed from the remaining agent results.
   *
   * @param expectationId the ID of the expectation
   * @param sourceId the ID of the source result to delete
   * @return the updated inject expectation
   * @throws IllegalArgumentException if trying to delete from an Asset Group
   */
  public BaseInjectExpectation deleteInjectExpectationResult(
      @NotBlank final String expectationId, @NotBlank final String sourceId) {
    BaseInjectExpectation baseInjectExpectation =
        this.injectExpectationRepository.findById(expectationId).orElseThrow();

    if (baseInjectExpectation instanceof TechnicalInjectExpectation technicalInjectExpectation
        && List.of(
                DETECTION,
                PREVENTION,
                // NB: the explicit ExpectationType.VULNERABILITY static import shadows the
                // EXPECTATION_TYPE.* wildcard one, hence the qualified reference.
                BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY)
            .contains(baseInjectExpectation.getType())) {
      // Block down computation on asset group
      if (isAssetGroupExpectation(technicalInjectExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Non-endpoint assets (AI targets, ...) have no agents and are treated as agentless.
      Asset unproxied = (Asset) Hibernate.unproxy(technicalInjectExpectation.getAsset());
      List<Agent> agents =
          (unproxied instanceof Endpoint endpoint) ? getPrimaryAgents(endpoint) : List.of();
      boolean isAgentless = agents.isEmpty();

      deleteResult(technicalInjectExpectation, sourceId);
      BaseInjectExpectation updated =
          this.injectExpectationRepository.save(technicalInjectExpectation);

      if (isAssetExpectation(technicalInjectExpectation) && !isAgentless) {
        // Asset-level delete on an asset with agents: remove the source's result from every
        // agent expectation of the asset (same down computation as updateInjectExpectation),
        // the propagation below then recomputes the asset score from its agents.
        List<TechnicalInjectExpectation> expectationsForAgents =
            getAgentsExpectationsForAsset(technicalInjectExpectation);
        expectationsForAgents.forEach(e -> deleteResult(e, sourceId));
        this.injectExpectationRepository.saveAll(expectationsForAgents);
      }
      propagateTechnicalExpectation(technicalInjectExpectation, isAgentless, null);
      return updated;
    }

    deleteResult(baseInjectExpectation, sourceId);
    BaseInjectExpectation updated = this.injectExpectationRepository.save(baseInjectExpectation);
    if (updated instanceof TableTopInjectExpectation tableTopInjectExpectation) {
      propagateHumanResponseExpectation(tableTopInjectExpectation, null);
    }
    return updated;
  }

  //  -- HUMAN RESPONSE --

  /**
   * Computes an inject expectation for a human response
   *
   * @param baseInjectExpectation the expectation to compute
   * @param input the update input containing the score
   * @param result the result label
   */
  private void computeInjectExpectationForHumanResponse(
      @NotNull BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotBlank final String result) {
    // Keep only one result
    baseInjectExpectation.getResults().clear();
    addResult(baseInjectExpectation, input, result);
    final Double score = computeScore(baseInjectExpectation.getResults(), baseInjectExpectation);
    baseInjectExpectation.setScore(score);
  }

  /**
   * Computes an inject expectation for a human response from a collector.
   *
   * @param baseInjectExpectation the expectation to compute
   * @param input the update input containing the response
   * @param collector the collector submitting the response
   * @return the updated inject expectation
   */
  public BaseInjectExpectation computeInjectExpectationForHumanResponse(
      @NotNull BaseInjectExpectation baseInjectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    // Keep only one result
    baseInjectExpectation.getResults().clear();
    addResult(baseInjectExpectation, input, collector);
    final Double score = computeScore(baseInjectExpectation.getResults(), baseInjectExpectation);
    baseInjectExpectation.setScore(score);
    return baseInjectExpectation;
  }

  /**
   * Propagates a human response expectation update to related expectations.
   *
   * <p>If the expectation belongs to a player, propagates to the team. If the expectation belongs
   * to a team, propagates to all players.
   *
   * @param tableTopExpectation the updated expectation
   * @param result the result label to propagate
   */
  private void propagateHumanResponseExpectation(
      @NotNull TableTopInjectExpectation tableTopExpectation, @Nullable final String result) {
    // If the updated expectation was a player expectation, We have to update the team expectation
    // using player expectations (based on validation type)
    List<BaseInjectExpectation> expectations = new ArrayList<>();
    if (tableTopExpectation.getUser() != null) {
      expectations.addAll(propagateToTeam(tableTopExpectation, result));
    } else {
      expectations.addAll(propagateToPlayers(tableTopExpectation, result));
    }
    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(tableTopExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates a team expectation update to all player expectations.
   *
   * @param tableTopInjectExpectation the team expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated player expectations
   */
  private List<TableTopInjectExpectation> propagateToPlayers(
      @NotNull final TableTopInjectExpectation tableTopInjectExpectation,
      @Nullable final String result) {
    // If I update the expectation team: What happens with children? -> update expectation score
    // for all children -> set score from BaseInjectExpectation
    List<TableTopInjectExpectation> expectationsForPlayers =
        getPlayersExpectationsForTeam(tableTopInjectExpectation);

    for (BaseInjectExpectation expectationsForPlayer : expectationsForPlayers) {
      expectationsForPlayer.getResults().clear();
      if (result != null) {
        expectationsForPlayer
            .getResults()
            .add(buildForTeamManualValidation(result, tableTopInjectExpectation.getScore()));
      }
      expectationsForPlayer.setScore(tableTopInjectExpectation.getScore());
    }
    return expectationsForPlayers;
  }

  /**
   * Propagates a player expectation update to the team expectation.
   *
   * @param tableTopInjectExpectation the player expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated team expectations
   */
  private List<TableTopInjectExpectation> propagateToTeam(
      @NotNull final TableTopInjectExpectation tableTopInjectExpectation,
      @Nullable final String result) {
    List<TableTopInjectExpectation> expectationsForPlayers =
        getPlayersExpectationsForTeam(tableTopInjectExpectation);
    List<TableTopInjectExpectation> expectationForTeams =
        getTeamsExpectations(tableTopInjectExpectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        tableTopInjectExpectation,
        score -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }

  // -- TECHNICAL --

  /**
   * Computes a technical expectation for an agent or agentless asset
   *
   * @param baseInjectExpectation the expectation to compute
   * @param input the update input containing the score
   */
  private void computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            baseInjectExpectation.getType(),
            baseInjectExpectation.getExpectedScore(),
            input.getScore());
    addResult(baseInjectExpectation, input, result);
    final Double score = computeScore(baseInjectExpectation.getResults(), baseInjectExpectation);
    baseInjectExpectation.setScore(score);
  }

  /**
   * Propagates a technical expectation update up the hierarchy (agent to asset to asset group).
   *
   * @param technicalInjectExpectation the expectation that was updated
   * @param isAgentless whether the asset has no agent
   * @param addResult optional function to create a result from a score
   */
  private void propagateTechnicalExpectation(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation,
      final boolean isAgentless,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<BaseInjectExpectation> expectations = new ArrayList<>();
    // 1) Agent -> Asset
    if (!isAgentless) {
      expectations.addAll(propagateToAsset(technicalInjectExpectation, addResult));
    }

    // 2) Asset -> Asset Group
    expectations.addAll(propagateToAssetGroup(technicalInjectExpectation, addResult));

    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(technicalInjectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates an agent expectation update to the asset expectation.
   *
   * @param technicalInjectExpectation the agent expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset expectations
   */
  private List<TechnicalInjectExpectation> propagateToAsset(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<TechnicalInjectExpectation> expectationsForAgents =
        getAgentsExpectationsForAsset(technicalInjectExpectation);
    List<TechnicalInjectExpectation> expectationsForAssets =
        getAssetsExpectations(technicalInjectExpectation);
    computeScores(
        expectationsForAgents, expectationsForAssets, technicalInjectExpectation, addResult);
    return expectationsForAssets;
  }

  /**
   * Propagates an asset expectation update to the asset group expectation.
   *
   * @param technicalInjectExpectation the asset expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset group expectations, or empty list if no asset group
   */
  private List<TechnicalInjectExpectation> propagateToAssetGroup(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    if (technicalInjectExpectation.getAssetGroup() != null) {
      List<TechnicalInjectExpectation> expectationsForAssets =
          getExpectationsAssetsForAssetGroup(technicalInjectExpectation);
      List<TechnicalInjectExpectation> expectationForAssetGroups =
          getExpectationAssetGroups(technicalInjectExpectation);
      computeScores(
          expectationsForAssets, expectationForAssetGroups, technicalInjectExpectation, addResult);
      return expectationForAssetGroups;
    }
    return new ArrayList<>();
  }

  // -- PARENT EXPECTATIONS (SCORE DERIVED FROM CHILDREN) --

  /**
   * Whether the expectation is a PARENT whose score is derived from its children (an asset-level
   * expectation whose asset has agent expectations, or an asset-group-level expectation) rather
   * than answered directly by a collector or a user.
   *
   * @param expectation the technical expectation to check
   * @return {@code true} if the expectation score must be rolled up from children
   */
  public boolean isParentTechnicalExpectation(
      @NotNull final TechnicalInjectExpectation expectation) {
    return isAssetGroupExpectation(expectation)
        || (isAssetExpectation(expectation)
            && !getAgentsExpectationsForAsset(expectation).isEmpty());
  }

  /**
   * Recomputes the score of a PARENT technical expectation (asset level with agent children, or
   * asset group level) from its current children, without writing any direct result on it.
   *
   * <p>Used by the expectations expiration manager: a parent expectation must ALWAYS reflect its
   * children. When the parent score is still null at expiration time but its agents were already
   * answered by a security platform (e.g. Microsoft Defender answered PREVENTED/DETECTED), the
   * parent must roll up to that green verdict instead of being independently forced to a failed
   * score - otherwise the asset shows "Not prevented" while its only agent shows "Prevented",
   * corrupting the asset/asset-group verdicts and every statistic built on them.
   *
   * <p>If the children are still unanswered, the parent score stays null (pending): it will be
   * resolved by a later propagation or a later expiration run, never wrongly failed.
   *
   * @param parent the parent expectation to recompute
   * @return the recomputed parent expectations (already saved), empty when the expectation is not a
   *     recomputable parent
   */
  public List<BaseInjectExpectation> recomputeParentTechnicalExpectation(
      @NotNull final TechnicalInjectExpectation parent) {
    List<BaseInjectExpectation> recomputed = new ArrayList<>();
    if (isAssetGroupExpectation(parent)) {
      recomputed.addAll(propagateToAssetGroup(parent, null));
    } else if (isAssetExpectation(parent) && !getAgentsExpectationsForAsset(parent).isEmpty()) {
      recomputed.addAll(propagateToAsset(parent, null));
    }
    if (!recomputed.isEmpty()) {
      this.injectExpectationRepository.saveAll(recomputed);
      Exercise exercise = parent.getInject().getExercise();
      if (exercise != null) {
        securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
            List.of(exercise));
      }
    }
    return recomputed;
  }

  // -- UPDATE FROM EXTERNAL SOURCE : COLLECTORS --

  /**
   * Updates an inject expectation from an external collector source.
   *
   * @param expectationId the ID of the expectation to update
   * @param input the update input from the collector
   * @return the updated inject expectation
   */
  @WorkflowUpdateEvent(expectationIds = "#expectationId")
  public BaseInjectExpectation updateInjectExpectation(
      @NotBlank String expectationId, @Valid @NotNull InjectExpectationUpdateInput input) {
    BaseInjectExpectation baseInjectExpectation = this.findInjectExpectation(expectationId);
    if (!(baseInjectExpectation instanceof TechnicalInjectExpectation technicalExpectation)) {
      throw new IllegalArgumentException("Updates are only supported for technical expectations");
    }
    Collector collector = this.collectorService.collector(input.getCollectorId());
    computeTechnicalExpectation(technicalExpectation, collector, input, false);
    logAutomaticExpectationResult(technicalExpectation, collector);
    return technicalExpectation;
  }

  /**
   * Variant of {@link #updateInjectExpectation} for verdicts attributed to a security platform
   * entry instead of a collector (e.g. assessment injectors like Nuclei fulfilling VULNERABILITY
   * expectations themselves). Mirrors the collector path: write the source result, recompute the
   * score, then propagate up the asset / asset group chain.
   *
   * @param expectationId the ID of the expectation to update
   * @param input the update input (result and success flag)
   * @param securityPlatform the platform the verdict is attributed to
   * @return the updated inject expectation
   */
  @WorkflowUpdateEvent(expectationIds = "#expectationId")
  public BaseInjectExpectation updateInjectExpectationFromSecurityPlatform(
      @NotBlank String expectationId,
      @Valid @NotNull InjectExpectationUpdateInput input,
      @NotNull SecurityPlatform securityPlatform) {
    BaseInjectExpectation baseInjectExpectation = this.findInjectExpectation(expectationId);
    if (!(baseInjectExpectation instanceof TechnicalInjectExpectation technicalExpectation)) {
      throw new IllegalArgumentException("Updates are only supported for technical expectations");
    }
    addResult(technicalExpectation, input, securityPlatform);
    // Same combination contract as the collector path (computeInjectExpectationForAgentOrAsset
    // Agentless): a direct write on a parent row must not clobber - nor be clobbered by - the
    // children-derived verdict.
    technicalExpectation.setScore(
        combineWithChildrenVerdict(
            technicalExpectation,
            computeScore(technicalExpectation.getResults(), technicalExpectation)));
    TechnicalInjectExpectation updated =
        this.injectExpectationRepository.save(technicalExpectation);
    // Same propagation contract as computeTechnicalExpectation: agentless expectations only
    // propagate asset -> group, agent expectations roll up the full chain.
    propagateTechnicalExpectation(updated, updated.getAgent() == null, null);
    logAutomaticExpectationResult(updated, securityPlatform.getId(), securityPlatform.getName());
    return technicalExpectation;
  }

  /**
   * Performs a bulk update of multiple inject expectations.
   *
   * @param inputs a map of expectation IDs to their update inputs
   */
  public void bulkUpdateInjectExpectation(
      @Valid @NotNull Map<String, InjectExpectationUpdateInput> inputs) {
    if (inputs.isEmpty()) {
      return;
    }

    List<TechnicalInjectExpectation> injectExpectations =
        fromIterable(this.injectExpectationRepository.findAllById(inputs.keySet())).stream()
            .filter(expectation -> expectation instanceof TechnicalInjectExpectation)
            .map(TechnicalInjectExpectation.class::cast)
            .toList();
    Set<String> foundIds =
        injectExpectations.stream().map(BaseInjectExpectation::getId).collect(Collectors.toSet());
    inputs.keySet().stream()
        .filter(id -> !foundIds.contains(id))
        .forEach(id -> log.error("Inject expectation not found for ID: {}", id));

    Collector collector =
        this.collectorService.collector(
            inputs.values().stream()
                .findFirst()
                .orElseThrow(ElementNotFoundException::new)
                .getCollectorId());

    bulkComputeTechnicalExpectations(injectExpectations, inputs, collector, false);
  }

  /**
   * Computes a technical expectation (detection/prevention) from collector input.
   *
   * @param technicalInjectExpectation the expectation to compute
   * @param collector the collector submitting the result
   * @param input the update input
   * @param shouldPropagateLastInjectExpectationResult whether to propagate the last result
   */
  public void computeTechnicalExpectation(
      TechnicalInjectExpectation technicalInjectExpectation,
      Collector collector,
      InjectExpectationUpdateInput input,
      boolean shouldPropagateLastInjectExpectationResult) {
    // Update inject expectation at agent level
    technicalInjectExpectation =
        this.computeInjectExpectationForAgentOrAssetAgentless(
            technicalInjectExpectation, input, collector);
    TechnicalInjectExpectation updated =
        this.injectExpectationRepository.save(technicalInjectExpectation);
    // When the collector fills an ASSET-level (agentless) expectation - AI targets, or agentless
    // endpoints - there is no agent layer below it: agent->asset propagation would recompute the
    // asset score from zero children and immediately wipe the score we just set. Only asset->group
    // propagation must run. When the collector fills an AGENT expectation, roll the score up the
    // full agent->asset->group chain.
    boolean isAgentless = updated.getAgent() == null;
    propagateTechnicalExpectation(
        updated,
        isAgentless,
        shouldPropagateLastInjectExpectationResult
            ? score -> updated.getResults().getLast()
            : null);
  }

  /**
   * Batched variant of {@link #computeTechnicalExpectation}: applies all agent-level updates and
   * saves them in a single batch, then runs the parent propagation once per distinct (inject, type,
   * asset) and (inject, type, asset group) tuple instead of once per item, and creates the security
   * coverage job once per distinct simulation.
   *
   * <p>When {@code shouldPropagateLastInjectExpectationResult} is true, the result copied to a
   * completed parent is the one of the group's representative expectation (all items of a group
   * carry equivalent results in this code path, e.g. expiration results).
   *
   * @param expectations the agent-level expectations to update
   * @param inputsById the update inputs keyed by expectation ID
   * @param collector the collector submitting the results
   * @param shouldPropagateLastInjectExpectationResult whether to copy the triggering result to
   *     parents when their score completes
   */
  @WorkflowUpdateEvent(expectationIds = "#expectations.![id]")
  public void bulkComputeTechnicalExpectations(
      @NotNull final List<TechnicalInjectExpectation> expectations,
      @NotNull final Map<String, InjectExpectationUpdateInput> inputsById,
      @NotNull final Collector collector,
      final boolean shouldPropagateLastInjectExpectationResult) {
    // 1) Agent-level updates, one batched save
    List<TechnicalInjectExpectation> updatedExpectations = new ArrayList<>(expectations.size());
    for (TechnicalInjectExpectation expectation : expectations) {
      InjectExpectationUpdateInput input = inputsById.get(expectation.getId());
      if (input == null) {
        continue;
      }
      updatedExpectations.add(
          computeInjectExpectationForAgentOrAssetAgentless(expectation, input, collector));
    }
    if (updatedExpectations.isEmpty()) {
      return;
    }
    List<TechnicalInjectExpectation> saved =
        fromIterable(this.injectExpectationRepository.saveAll(updatedExpectations));

    for (TechnicalInjectExpectation expectation : saved) {
      logAutomaticExpectationResult(expectation, collector);
    }

    // 2) Propagation deduplicated per parent: recomputing an asset (or asset group) score reads
    // all its children, so one pass per distinct parent is equivalent to one pass per item
    Map<String, TechnicalInjectExpectation> assetPropagations = new LinkedHashMap<>();
    Map<String, TechnicalInjectExpectation> assetGroupPropagations = new LinkedHashMap<>();
    for (TechnicalInjectExpectation updated : saved) {
      // Agent -> asset rollup only applies when the updated leaf is an AGENT expectation. An
      // agentless asset expectation (AI target, agentless endpoint) IS the leaf: recomputing it
      // from its (nonexistent) agent children would wipe the score just written, so skip it here
      // and let the asset -> group step below roll it up.
      if (updated.getAsset() != null && updated.getAgent() != null) {
        assetPropagations.putIfAbsent(
            updated.getInject().getId()
                + "|"
                + updated.getType()
                + "|"
                + updated.getAsset().getId(),
            updated);
      }
      if (updated.getAssetGroup() != null) {
        assetGroupPropagations.putIfAbsent(
            updated.getInject().getId()
                + "|"
                + updated.getType()
                + "|"
                + updated.getAssetGroup().getId(),
            updated);
      }
    }
    List<BaseInjectExpectation> parents = new ArrayList<>();
    // Asset scores first: asset group propagation reads the recomputed asset expectations
    for (TechnicalInjectExpectation reference : assetPropagations.values()) {
      parents.addAll(
          propagateToAsset(
              reference,
              shouldPropagateLastInjectExpectationResult
                  ? score -> reference.getResults().getLast()
                  : null));
    }
    for (TechnicalInjectExpectation reference : assetGroupPropagations.values()) {
      parents.addAll(
          propagateToAssetGroup(
              reference,
              shouldPropagateLastInjectExpectationResult
                  ? score -> reference.getResults().getLast()
                  : null));
    }
    this.injectExpectationRepository.saveAll(parents);

    // 3) Security coverage job once per distinct simulation
    List<Exercise> exercises =
        saved.stream()
            .map(expectation -> expectation.getInject().getExercise())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (!exercises.isEmpty()) {
      securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
    }
  }

  // -- COMPUTE RESULTS FROM INJECT EXPECTATIONS --

  /**
   * Computes an inject expectation for an agent or agentless asset from collector input.
   *
   * @param technicalInjectExpectation the expectation to compute
   * @param input the update input
   * @param collector the collector submitting the result
   * @return the updated inject expectation
   */
  public TechnicalInjectExpectation computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    addResult(technicalInjectExpectation, input, collector);
    final Double score =
        computeScore(technicalInjectExpectation.getResults(), technicalInjectExpectation);
    technicalInjectExpectation.setScore(
        combineWithChildrenVerdict(technicalInjectExpectation, score));
    return technicalInjectExpectation;
  }

  /**
   * Guards a direct (collector-written) score against clobbering a PARENT expectation's
   * children-derived verdict. An asset-level expectation whose asset has agent children carries a
   * score rolled up from those agents (e.g. Microsoft Defender answered the agents green); a
   * collector answering the parent row directly (e.g. an LLM firewall expiring its own pending
   * check with "Not Detected") must never overwrite that rollup with a score computed from the
   * parent's own results only.
   *
   * <p>For DETECTION / PREVENTION a direct SUCCESS wins (any security platform proving
   * prevention/detection counts) and anything else defers to the children - including staying
   * pending (null) while the agents are unanswered, so a direct absence-of-signal answer never
   * cements a wrong verdict early.
   *
   * <p>VULNERABILITY is decided on its own row instead, in BOTH directions, because the verdict
   * comes from scanning the asset itself: "Not vulnerable" is a real answer there, not the absence
   * of one. Deferring it to the children would hang the row until expiry, since an agentless
   * injector never fills the agent expectations - those exist only because the endpoint happens to
   * run an agent. A proven VULNERABLE result still wins over a "Not vulnerable" one from another
   * source (worst case wins, hence {@code reconcileWithDirectVulnerableVerdict} rather than the
   * max-based own score), and a child that reports a finding later still overrides the row through
   * the rollup path, which only pins direct VULNERABLE verdicts.
   *
   * @param expectation the expectation whose score was just computed from its own results
   * @param ownScore the score computed from the expectation's own results
   * @return the score to persist
   */
  private Double combineWithChildrenVerdict(
      @NotNull final TechnicalInjectExpectation expectation, @Nullable final Double ownScore) {
    if (expectation.getAgent() != null) {
      return ownScore; // agent leaf: no children rollup to protect
    }
    if (expectation.getAsset() == null) {
      // Asset-group level: no children rollup to protect here (propagation recomputes it from
      // the asset children), but a direct VULNERABLE verdict written on the group row itself
      // (e.g. by Nuclei) must survive the own-results max: the results list may also carry
      // success-polarity rows ("Expired" placeholders, legacy expiration-manager stamps) that
      // would otherwise win the max and flip the group to "Not vulnerable".
      if (BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY.equals(expectation.getType())) {
        Double directVulnerable =
            InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(expectation, null);
        if (directVulnerable != null) {
          return directVulnerable;
        }
      }
      return ownScore;
    }
    List<TechnicalInjectExpectation> agentChildren = getAgentsExpectationsForAsset(expectation);
    if (agentChildren.isEmpty()) {
      return ownScore; // true agentless leaf (AI target, agentless endpoint)
    }
    Double expectedScore = expectation.getExpectedScore();
    if (expectedScore == null) {
      return ownScore;
    }
    if (BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY.equals(expectation.getType())) {
      Double directVulnerable =
          InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(expectation, null);
      if (directVulnerable != null) {
        return directVulnerable;
      }
      // No finding: conclude only once every expected source has answered - ownScore is null while
      // one is still missing, which is exactly the pending state to keep.
      if (ownScore != null) {
        return ownScore;
      }
      return InjectExpectationUtils.computeChildrenScore(
          expectation.isExpectationGroup(), expectedScore, agentChildren);
    }
    if (ownScore != null && ownScore >= expectedScore) {
      return ownScore;
    }
    return InjectExpectationUtils.computeChildrenScore(
        expectation.isExpectationGroup(), expectedScore, agentChildren);
  }

  // -- FINAL UPDATE --

  /**
   * Saves all inject expectations in a batch operation.
   *
   * @param injectExpectations the list of expectations to save
   */
  public void updateAll(@NotNull List<BaseInjectExpectation> injectExpectations) {
    this.injectExpectationRepository.saveAll(injectExpectations);
  }

  // -- FETCH INJECT EXPECTATIONS --

  /**
   * Retrieves unfilled inject expectations (no score and either no results or bound to an agent)
   * and expired Returns a bounded batch for incremental processing.
   *
   * @param tenantId the tenant to scope the query to
   * @param limit maximum number of expectations to return
   * @return a list of unfilled inject expectations ordered by creation date (oldest first)
   */
  public List<BaseInjectExpectation> expectationsNotFillAndExpired(String tenantId, int limit) {
    return this.injectExpectationRepository.findExpectationsNotFilledAndExpired(tenantId, limit);
  }

  // -- EXPECTATIONS BY TYPE --

  /**
   * Retrieves expectations of a given type that have not been filled by a specific source and are
   * not expired.
   *
   * @param type the expectation type to filter by
   * @param expirationTime the expiration threshold in minutes
   * @param sourceId the source ID to check for existing results
   * @return a list of matching inject expectations
   */
  public List<BaseInjectExpectation> expectationsNotFilledAndNotExpiredBySourceId(
      @NotBlank String tenantId,
      @NotNull BaseInjectExpectation.EXPECTATION_TYPE type,
      @NotNull Integer expirationTime,
      @NotBlank String sourceId) {

    Instant expirationThreshold = now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository.findAgentExpectationsNotFilledForSourceCreatedAfter(
        tenantId, type.name(), sourceId, expirationThreshold, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves expectations of a given type that have no results and are not expired.
   *
   * @param type the expectation type to filter by
   * @param expirationTime the expiration threshold in minutes
   * @return a list of matching inject expectations
   */
  public List<BaseInjectExpectation> expectationsNotFilledAndNotExpired(
      @NotBlank String tenantId,
      @NotNull BaseInjectExpectation.EXPECTATION_TYPE type,
      @NotNull Integer expirationTime) {

    Instant expirationThreshold = now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository.findAgentExpectationsNotFilledCreatedAfter(
        tenantId, type.name(), expirationThreshold, NOT_FILLED_FETCH_LIMIT);
  }

  // -- PREVENTION --

  /**
   * Retrieves prevention expectations that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired prevention expectations
   */
  public List<BaseInjectExpectation> preventionExpectationsNotExpired(
      final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<BaseInjectExpectation>unrestricted()
            .and(
                InjectExpectationSpecification.type(PREVENTION)
                    .and(InjectExpectationSpecification.agentNotNull())
                    .and(InjectExpectationSpecification.assetNotNull())
                    .and(
                        InjectExpectationSpecification.from(
                            now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves prevention expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of prevention expectations without results from the source
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFill(
      @NotBlank final String tenantId, @NotBlank final String sourceId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilledForSource(
        tenantId, PREVENTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves prevention expectations without any results.
   *
   * @param tenantId the tenant ID to scope the query
   * @return a list of prevention expectations without results
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFill(
      @NotBlank final String tenantId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilled(
        tenantId, PREVENTION.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves prevention expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired prevention expectations without results
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFillAndNotExpired(
      @NotBlank String tenantId, @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(tenantId, PREVENTION, expirationTime);
  }

  /**
   * Retrieves prevention expectations without results from a specific source that have not expired.
   *
   * @param tenantId the tenant ID to scope the query
   * @param expirationTime the expiration threshold in minutes
   * @param sourceId the source ID to check for existing results
   * @return a list of non-expired prevention expectations without results from the source
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFilledAndNotExpired(
      @NotBlank String tenantId, @NotNull Integer expirationTime, @NotBlank String sourceId) {
    return expectationsNotFilledAndNotExpiredBySourceId(
        tenantId, PREVENTION, expirationTime, sourceId);
  }

  // -- DETECTION --

  /**
   * Retrieves detection expectations that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired detection expectations
   */
  public List<BaseInjectExpectation> detectionExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<BaseInjectExpectation>unrestricted()
            .and(
                InjectExpectationSpecification.type(DETECTION)
                    .and(InjectExpectationSpecification.agentNotNull())
                    .and(InjectExpectationSpecification.assetNotNull())
                    .and(
                        InjectExpectationSpecification.from(
                            now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves detection expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of detection expectations without results from the source
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFill(
      @NotBlank final String tenantId, @NotBlank final String sourceId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilledForSource(
        tenantId, DETECTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Agentless DETECTION/PREVENTION expectations not yet filled by the given source. Used by AI
   * defense collectors (LLM firewall / guardrail) for AI adversarial injects, whose targets are AI
   * models/agents rather than endpoints with an installed agent.
   *
   * @param sourceId the collector source ID
   * @return agentless detection + prevention expectations without a result from the source
   */
  public List<BaseInjectExpectation> aiDefenseExpectationsNotFill(
      @NotBlank final String tenantId, @NotBlank final String sourceId) {
    // Combine agentless DETECTION + PREVENTION expectations, keep a single stable global order
    // (oldest first) and cap the total so a polling collector receives a bounded, fairly ordered
    // page across both expectation types rather than two separately-capped lists.
    List<BaseInjectExpectation> expectations = new ArrayList<>();
    expectations.addAll(
        this.injectExpectationRepository.findAgentlessExpectationsNotFilledForSource(
            tenantId, DETECTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT));
    expectations.addAll(
        this.injectExpectationRepository.findAgentlessExpectationsNotFilledForSource(
            tenantId, PREVENTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT));
    expectations.sort(
        Comparator.comparing(
            BaseInjectExpectation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    return expectations.stream().limit(NOT_FILLED_FETCH_LIMIT).toList();
  }

  /**
   * Retrieves detection expectations without any results.
   *
   * @return a list of detection expectations without results
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFill(@NotBlank final String tenantId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilled(
        tenantId, DETECTION.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves detection expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired detection expectations without results
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFillAndNotExpired(
      @NotBlank String tenantId, @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(tenantId, DETECTION, expirationTime);
  }

  /**
   * Retrieves detection expectations without results from a specific source that have not expired.
   *
   * @param tenantId the tenant ID to scope the query
   * @param expirationTime the expiration threshold in minutes
   * @param sourceId the source ID to check for existing results
   * @return a list of non-expired detection expectations without results from the source
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFilledAndNotExpired(
      @NotBlank String tenantId, @NotNull Integer expirationTime, @NotBlank String sourceId) {

    return expectationsNotFilledAndNotExpiredBySourceId(
        tenantId, DETECTION, expirationTime, sourceId);
  }

  // -- MANUAL

  /**
   * Retrieves manual expectations that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired manual expectations
   */
  public List<BaseInjectExpectation> manualExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<BaseInjectExpectation>unrestricted()
            .and(
                InjectExpectationSpecification.type(MANUAL)
                    .and(InjectExpectationSpecification.agentNotNull())
                    .and(InjectExpectationSpecification.assetNotNull())
                    .and(
                        InjectExpectationSpecification.from(
                            now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves manual expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of manual expectations without results from the source
   */
  public List<BaseInjectExpectation> manualExpectationsNotFill(
      @NotBlank final String tenantId, @NotBlank final String sourceId) {
    return this.injectExpectationRepository.findExpectationsNotFilledForSource(
        tenantId, MANUAL.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves manual expectations without any results.
   *
   * @param tenantId the tenant ID to scope the query
   * @return a list of manual expectations without results
   */
  public List<BaseInjectExpectation> manualExpectationsNotFill(@NotBlank final String tenantId) {
    return this.injectExpectationRepository.findExpectationsNotFilled(
        tenantId, MANUAL.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves manual expectations without results that have not expired.
   *
   * @param tenantId the tenant ID to scope the query
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired manual expectations without results
   */
  public List<BaseInjectExpectation> manualExpectationsNotFillAndNotExpired(
      @NotBlank String tenantId, @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(tenantId, MANUAL, expirationTime);
  }

  // -- BY TARGET TYPE

  /**
   * Finds and merges expectations by inject, target, and target type.
   *
   * @param injectId the inject ID
   * @param targetId the target ID
   * @param targetType the type of target (TEAMS, ASSETS_GROUPS, PLAYERS, AGENT, ASSETS)
   * @return a list of merged expectations by expectation type
   */
  public List<BaseInjectExpectation> findMergedExpectationsByInjectAndTargetAndTargetType(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotBlank final String targetType) {
    try {
      TargetType targetTypeEnum = TargetType.valueOf(targetType);
      return mergeExpectationResultsByExpectationType(
          switch (targetTypeEnum) {
            case TEAMS, ASSETS_GROUPS ->
                this.findMergedExpectationsByInjectAndTargetAndTargetType(
                    injectId, targetId, "not applicable", targetType);
            case PLAYERS ->
                injectExpectationRepository.findAllByInjectAndPlayer(injectId, targetId);
            case AGENT -> injectExpectationRepository.findAllByInjectAndAgent(injectId, targetId);
            // AI targets are plain assets (asset_category driven), so their expectations are
            // resolved through the asset lookup.
            case ASSETS, AI_TARGETS ->
                injectExpectationRepository.findAllByInjectAndAsset(injectId, targetId);
            default ->
                throw new RuntimeException(
                    "Target type "
                        + targetType
                        + " not implemented for this method findMergedExpectationsByInjectAndTargetAndTargetType");
          });
    } catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }

  /**
   * Finds expectations by inject, target, parent target, and target type.
   *
   * @param injectId the inject ID
   * @param targetId the target ID
   * @param parentTargetId the parent target ID (e.g., team ID for players)
   * @param targetType the type of target (TEAMS, PLAYERS, AGENT, ASSETS, ASSETS_GROUPS)
   * @return a list of matching expectations
   */
  public List<? extends BaseInjectExpectation> findMergedExpectationsByInjectAndTargetAndTargetType(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotBlank final String parentTargetId,
      @NotBlank final String targetType) {
    try {
      TargetType targetTypeEnum = TargetType.valueOf(targetType);
      return switch (targetTypeEnum) {
        case TEAMS -> injectExpectationRepository.findAllByInjectAndTeam(injectId, targetId);
        case PLAYERS -> injectExpectationRepository.findAllByInjectAndPlayer(injectId, targetId);
        case AGENT -> injectExpectationRepository.findAllByInjectAndAgent(injectId, targetId);
        // AI targets are plain assets (asset_category driven), resolved through the asset lookup.
        case ASSETS, AI_TARGETS ->
            enrichAssetExpectationsWithAgentSecurityPlatforms(
                injectId,
                targetId,
                injectExpectationRepository.findAllByInjectAndAsset(injectId, targetId));
        case ASSETS_GROUPS ->
            enrichAssetGroupExpectationsWithChildrenSecurityPlatforms(
                injectId,
                targetId,
                injectExpectationRepository.findAllByInjectAndAssetGroup(injectId, targetId));
        default ->
            throw new RuntimeException(
                "Target type "
                    + targetType
                    + " not implemented for this method findMergedExpectationsByInjectAndTargetAndTargetType");
      };
    } catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }

  /**
   * Makes the endpoint (asset) target-results view show the same security platforms as the agents
   * view. Asset-level detection/prevention expectations are scored by aggregating their agents and
   * therefore do not carry the per-security-platform pending/answered result rows that the agent
   * expectations hold. For display we merge, per expectation type, the union of the child agents'
   * security-platform (collector) results onto a DETACHED clone of each asset expectation, so the
   * change never persists. Non-technical or agentless assets are returned unchanged.
   *
   * @param injectId the inject ID
   * @param assetId the asset (endpoint) ID
   * @param assetExpectations the asset-level expectations for the inject
   * @return detached asset expectations enriched with their agents' security-platform results
   */
  private List<BaseInjectExpectation> enrichAssetExpectationsWithAgentSecurityPlatforms(
      final String injectId,
      final String assetId,
      final List<BaseInjectExpectation> assetExpectations) {
    List<BaseInjectExpectation> agentExpectations =
        injectExpectationRepository.findAllAgentExpectationsByInjectAndAsset(injectId, assetId);
    if (agentExpectations.isEmpty()) {
      // Agentless asset (AI target, agentless endpoint): the asset expectation is filled directly.
      return assetExpectations;
    }
    return enrichExpectationsWithChildrenSecurityPlatforms(
        assetExpectations,
        agentExpectations,
        // Prefer an answered result over a pending one for the same source.
        expectation ->
            (existing, candidate) -> existing.getResult() != null ? existing : candidate);
  }

  /**
   * Makes the asset-group target-results view show the security platforms of its underlying assets.
   * Asset-group detection/prevention expectations are scored by rolling up their asset children
   * and, on the collector path, never carry the per-security-platform result rows that the agent /
   * asset expectations hold (only direct writes - e.g. an assessment injector like Nuclei
   * concluding the group row itself - do). For display we merge, per expectation type, the union of
   * the children's (agent and asset levels) security-platform results onto a DETACHED clone of each
   * group expectation, so the change never persists. Per platform, the displayed result reflects
   * the platform's overall verdict under the group's validation rule (see {@link
   * #pickGroupPlatformVerdict}). Groups without children rows are returned unchanged.
   *
   * @param injectId the inject ID
   * @param assetGroupId the asset group ID
   * @param assetGroupExpectations the group-level expectations for the inject
   * @return detached group expectations enriched with their children's security-platform results
   */
  private List<BaseInjectExpectation> enrichAssetGroupExpectationsWithChildrenSecurityPlatforms(
      final String injectId,
      final String assetGroupId,
      final List<BaseInjectExpectation> assetGroupExpectations) {
    List<BaseInjectExpectation> childExpectations =
        injectExpectationRepository.findAllChildExpectationsByInjectAndAssetGroup(
            injectId, assetGroupId);
    if (childExpectations.isEmpty()) {
      return assetGroupExpectations;
    }
    return enrichExpectationsWithChildrenSecurityPlatforms(
        assetGroupExpectations,
        childExpectations,
        expectation ->
            (existing, candidate) -> pickGroupPlatformVerdict(expectation, existing, candidate));
  }

  /**
   * Merges the children's security-platform (collector) results onto a DETACHED clone of each
   * parent expectation of the same type, de-duplicated by source. The parent's OWN direct results
   * (e.g. a security platform that answered the parent level directly) are kept in the union: a
   * result persisted on the row must stay visible, silently dropping it would hide what drove the
   * score. Display-only: the persistent entities are never modified.
   *
   * @param parentExpectations the parent expectations to enrich (asset or asset-group level)
   * @param childExpectations the children carrying the security-platform results
   * @param mergePreferenceFor picks, per parent expectation, which of two results of the same
   *     source the merged view keeps
   * @return detached parent expectations enriched with their children's security-platform results
   */
  private static List<BaseInjectExpectation> enrichExpectationsWithChildrenSecurityPlatforms(
      final List<BaseInjectExpectation> parentExpectations,
      final List<BaseInjectExpectation> childExpectations,
      final Function<BaseInjectExpectation, BinaryOperator<InjectExpectationResult>>
          mergePreferenceFor) {
    // Union of security-platform / collector results per expectation type, de-duplicated by source.
    Map<BaseInjectExpectation.EXPECTATION_TYPE, List<InjectExpectationResult>> resultsByType =
        new HashMap<>();
    for (BaseInjectExpectation childExpectation : childExpectations) {
      List<InjectExpectationResult> platformResults =
          childExpectation.getResults().stream()
              .filter(
                  r ->
                      COLLECTOR.equals(r.getSourceType())
                          || SECURITY_PLATFORM.equals(r.getSourceType()))
              .toList();
      resultsByType
          .computeIfAbsent(childExpectation.getType(), k -> new ArrayList<>())
          .addAll(platformResults);
    }
    return parentExpectations.stream()
        .map(
            expectation -> {
              List<InjectExpectationResult> platformResults =
                  resultsByType.get(expectation.getType());
              if (platformResults == null || platformResults.isEmpty()) {
                return expectation;
              }
              BaseInjectExpectation clone = expectation.clone();
              BinaryOperator<InjectExpectationResult> mergePreference =
                  mergePreferenceFor.apply(expectation);
              Map<String, InjectExpectationResult> bySource = new LinkedHashMap<>();
              // Children's results first, then the parent expectation's OWN direct results.
              Stream.concat(
                      platformResults.stream(),
                      expectation.getResults().stream()
                          .filter(
                              r ->
                                  COLLECTOR.equals(r.getSourceType())
                                      || SECURITY_PLATFORM.equals(r.getSourceType())))
                  .forEach(r -> bySource.merge(mergeSourceKey(r), r, mergePreference));
              // A VULNERABILITY row a genuine platform already answered needs no expiration entry
              // in the merged display either: the union pulls the children's expiration rows (the
              // vulnerability default "Not vulnerable") onto the parent view, where they render as
              // redundant - or contradictory - entries next to the real scan verdict. Same rule as
              // the write path (InjectExpectationUtils.computeScores): the expiration default is
              // the absence-of-signal fallback, a real answer supersedes it.
              if (BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY.equals(
                  expectation.getType())) {
                boolean hasGenuineAnswer =
                    bySource.values().stream()
                        .anyMatch(
                            r ->
                                !ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(
                                        r.getSourceId())
                                    && r.getResult() != null
                                    && !r.getResult().isBlank()
                                    && !EXPIRED.equals(r.getResult()));
                if (hasGenuineAnswer) {
                  bySource
                      .values()
                      .removeIf(
                          r ->
                              ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(
                                  r.getSourceId()));
                }
              }
              clone.setResults(new ArrayList<>(bySource.values()));
              return clone;
            })
        .toList();
  }

  /**
   * Per-platform verdict aggregation for the asset-group view: the same security platform typically
   * answered several assets of the group with different verdicts (e.g. detected on one endpoint,
   * missed on another). The displayed row must reflect the platform's OVERALL verdict under the
   * group's validation rule: with the default "all assets must validate" rule one failure fails the
   * platform overall (keep the worst-scored result), with the "at least one asset" rule one success
   * validates it (keep the best-scored result). An answered result always beats a pending one.
   *
   * @param groupExpectation the group expectation whose validation rule drives the choice
   * @param existing the result currently kept for this source
   * @param candidate the competing result of the same source
   * @return the result the merged view keeps
   */
  private static InjectExpectationResult pickGroupPlatformVerdict(
      final BaseInjectExpectation groupExpectation,
      final InjectExpectationResult existing,
      final InjectExpectationResult candidate) {
    boolean existingAnswered = isAnsweredResult(existing);
    boolean candidateAnswered = isAnsweredResult(candidate);
    if (existingAnswered != candidateAnswered) {
      return existingAnswered ? existing : candidate;
    }
    Double existingScore = existing.getScore();
    Double candidateScore = candidate.getScore();
    if (existingScore == null || candidateScore == null) {
      return existing;
    }
    if (groupExpectation.isExpectationGroup()) {
      return candidateScore > existingScore ? candidate : existing;
    }
    return candidateScore < existingScore ? candidate : existing;
  }

  private static boolean isAnsweredResult(final InjectExpectationResult result) {
    return result.getResult() != null && !result.getResult().isBlank();
  }

  private static String mergeSourceKey(InjectExpectationResult result) {
    if (result.getSourceId() != null && !result.getSourceId().isBlank()) {
      return result.getSourceId();
    }
    // Legacy-read compatibility only: old rows may miss sourceId.
    return result.getSourceName();
  }

  /**
   * Converts a list of inject expectations to agent output DTOs.
   *
   * @param injectExpectations the expectations to convert
   * @param assetId the asset ID to include in each output
   * @return a list of agent output DTOs
   */
  private static List<InjectExpectationAgentOutput> toInjectExpectationAgentsOutput(
      List<TechnicalInjectExpectation> injectExpectations, String assetId) {
    return injectExpectations.stream()
        .map(
            ie ->
                InjectExpectationAgentOutput.builder()
                    .type(ie.getType())
                    .id(ie.getId())
                    .name(ie.getName())
                    .results(ie.getResults())
                    .score(ie.getScore())
                    .status(ie.getResponse())
                    .expirationTime(ie.getExpirationTime())
                    .createdAt(ie.getCreatedAt())
                    .expectationGroup(ie.isExpectationGroup())
                    .agentId(ie.getAgent().getId())
                    .agentName(ie.getAgent().getExecutedByUser())
                    .assetId(assetId)
                    .injectId(ie.getInject().getId())
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Finds merged expectations with agent details for a given inject and asset.
   *
   * @param injectId the inject ID
   * @param assetId the asset ID
   * @param expectationType the expectation type to filter by
   * @return a list of agent outputs sorted by agent name
   */
  public List<InjectExpectationAgentOutput> findMergedExpectationsWithAgentsByInjectAndAsset(
      String injectId, String assetId, String expectationType) {
    List<InjectExpectationAgentOutput> injectExpectationAgentOutputs =
        toInjectExpectationAgentsOutput(
            injectExpectationRepository.findAllWithAgentsByInjectAndAsset(
                injectId, assetId, BaseInjectExpectation.EXPECTATION_TYPE.valueOf(expectationType)),
            assetId);
    injectExpectationAgentOutputs.sort(
        Comparator.comparing(InjectExpectationAgentOutput::getAgentName));
    return injectExpectationAgentOutputs;
  }

  // -- STRUCTURED OUTPUT SIGNATURES --

  /**
   * Applies signatures emitted by structured output on matching technical expectations.
   *
   * <p>The target is resolved using this priority: agent, then asset, then asset group.
   *
   * <p>If signatures were never initialized for an expectation, existing signatures are cleared
   * once, then new signatures are appended. Otherwise, signatures are only appended.
   *
   * <p>Only Detection and prevention expectations are supported for structured output signatures.
   * Other types will be ignored with a warning.
   *
   * @param injectId the inject ID
   * @param agentId optional agent ID target
   * @param assetId optional asset ID target
   * @param assetGroupId optional asset group ID target
   * @param expectationType the expectation type (DETECTION or PREVENTION)
   * @param signatures signatures to append
   */
  public void appendExpectationSignatures(
      @NotBlank String injectId,
      @Nullable String agentId,
      @Nullable String assetId,
      @Nullable String assetGroupId,
      @NotNull BaseInjectExpectation.EXPECTATION_TYPE expectationType,
      @NotNull List<ExpectationSignature> signatures) {
    if (signatures.isEmpty()) {
      return;
    }
    if (!List.of(DETECTION, PREVENTION).contains(expectationType)) {
      log.warn(
          "Signature structured output is only supported for DETECTION and PREVENTION expectations (injectId={}, agentId={}, assetId={}, assetGroupId={}, expectationType={})",
          injectId,
          agentId,
          assetId,
          assetGroupId,
          expectationType);
      return;
    }

    List<TechnicalInjectExpectation> expectations =
        findTechnicalExpectationsForTarget(injectId, agentId, assetId, assetGroupId).stream()
            .filter(expectation -> expectation.getType().equals(expectationType))
            .toList();

    if (expectations.isEmpty()) {
      log.warn(
          "No inject expectation found for structured signatures (injectId={}, agentId={}, assetId={}, assetGroupId={}, expectationType={})",
          injectId,
          agentId,
          assetId,
          assetGroupId,
          expectationType);
      return;
    }

    for (TechnicalInjectExpectation expectation : expectations) {
      injectExpectationLockService.applySignaturesForExpectationWithLock(
          expectation.getId(), convertToInjectExpectationSignatures(signatures, expectation));
    }
  }

  private List<TechnicalInjectExpectation> findTechnicalExpectationsForTarget(
      @NotBlank String injectId,
      @Nullable String agentId,
      @Nullable String assetId,
      @Nullable String assetGroupId) {
    if (agentId != null) {
      return filterTechnicalExpectations(
          injectExpectationRepository.findAllByInjectAndAgent(injectId, agentId));
    }
    if (assetId != null) {
      return filterTechnicalExpectations(
          injectExpectationRepository.findAllByInjectAndAsset(injectId, assetId));
    }
    if (assetGroupId != null) {
      return filterTechnicalExpectations(
          injectExpectationRepository.findAllByInjectAndAssetGroup(injectId, assetGroupId));
    }
    return Collections.emptyList();
  }

  // Agent/asset targets can also carry manual expectations: keep only the
  // technical ones for the signature-application paths.
  private static List<TechnicalInjectExpectation> filterTechnicalExpectations(
      List<BaseInjectExpectation> expectations) {
    return expectations.stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .toList();
  }

  /**
   * Add a date signature to all inject expectations by agent.
   *
   * @param injectId the injectId for which to add the end date signature
   * @param agentId the agentId for which to add the end date signature
   * @param date the date to set as the signature value
   * @param signatureType the type of signature to add
   */
  private void addDateSignatureToInjectExpectationsByAgent(
      @NotBlank final String injectId,
      @NotBlank final String agentId,
      @NotBlank final Instant date,
      @NotBlank final String signatureType) {
    // Load the technical expectations for the inject/agent, append the signature, then persist
    // the changes. Agent rows can also carry MANUAL expectations, which must not receive
    // start/end date signatures (they are matched against technical detection/prevention data).
    List<TechnicalInjectExpectation> injectExpectations =
        filterTechnicalExpectations(
            injectExpectationRepository.findAllByInjectAndAgent(injectId, agentId));
    if (!injectExpectations.isEmpty()) {
      injectExpectations.forEach(
          injectExpectation -> {
            InjectExpectationSignature signature =
                new InjectExpectationSignature(
                    injectExpectation, signatureType, date.toString(), now());
            injectExpectation.getSignatures().add(signature);
          });
      injectExpectationRepository.saveAll(injectExpectations);
    }
  }

  /**
   * Create a new End Date InjectExpectationSignature by a given agent.
   *
   * @param injectId the injectId for which to add the end date signature
   * @param agentId the agentId for which to add the end date signature
   * @param date the date to set as the end date signature
   */
  public void addEndDateSignatureToInjectExpectationsByAgent(
      @NotBlank final String injectId,
      @NotBlank final String agentId,
      @NotBlank final Instant date) {
    addDateSignatureToInjectExpectationsByAgent(
        injectId, agentId, date, EXPECTATION_SIGNATURE_TYPE_END_DATE);
  }

  /**
   * Create a new Start Date InjectExpectationSignature by a given agent.
   *
   * @param injectId the injectId for which to add the start date signature
   * @param agentId the agentId for which to add the start date signature
   * @param date the date to set as the start date signature
   */
  @Transactional
  public void addStartDateSignatureToInjectExpectationsByAgent(
      @NotBlank final String injectId,
      @NotBlank final String agentId,
      @NotBlank final Instant date) {
    addDateSignatureToInjectExpectationsByAgent(
        injectId, agentId, date, EXPECTATION_SIGNATURE_TYPE_START_DATE);
  }

  /**
   * Merges expectation results by expectation type, keeping one expectation per type.
   *
   * <p>Results from collector sources are not copied to the merged expectation. The score is set to
   * the maximum score among all results.
   *
   * @param expectations the list of expectations to merge
   * @return a list with one expectation per type containing merged results
   */
  private List<BaseInjectExpectation> mergeExpectationResultsByExpectationType(
      List<? extends BaseInjectExpectation> expectations) {
    List<String> notCopiedSourceTypes = List.of(COLLECTOR);

    HashMap<BaseInjectExpectation.EXPECTATION_TYPE, BaseInjectExpectation> electedExpectations =
        new HashMap<>();
    for (BaseInjectExpectation expectation : expectations) {
      if (!electedExpectations.containsKey(expectation.getType())) {
        electedExpectations.put(expectation.getType(), expectation);
        continue;
      }

      for (InjectExpectationResult expectationResult : expectation.getResults()) {
        if (!notCopiedSourceTypes.contains(expectationResult.getSourceType())
            && expectationResult.getResult() != null
            && expectationResult.getScore() != null) {
          electedExpectations
              .get(expectation.getType())
              .setResults(
                  Stream.concat(
                          electedExpectations.get(expectation.getType()).getResults().stream(),
                          Stream.of(expectationResult))
                      .toList());
          electedExpectations
              .get(expectation.getType())
              .setScore(
                  electedExpectations.get(expectation.getType()).getResults().stream()
                      .map(InjectExpectationResult::getScore)
                      .filter(Objects::nonNull)
                      .max(Double::compareTo)
                      .orElse(null));
        }
      }
    }
    return electedExpectations.values().stream().toList();
  }

  /**
   * Fetch a distinct list of inject IDs from a list of expectation IDs.
   *
   * @param expectationIds expectations IDs for which we want to retrieve the inject IDs
   * @return a set of inject IDs
   */
  public Set<String> findDistinctInjectIdsByInjectExpectationIds(Set<String> expectationIds) {
    return this.injectExpectationRepository.findDistinctInjectIdsByInjectExpectationIds(
        expectationIds);
  }

  // -- BUILD AND SAVE INJECT EXPECTATION --

  /**
   * Builds and saves inject expectations for an executable inject.
   *
   * <p>Creates expectations for teams, players, assets, and asset groups based on the inject
   * configuration. For scheduled injects or atomic testing, expectations are created for all
   * enabled players in each team.
   *
   * @param executableInject the inject to create expectations for
   * @param expectations the list of expectation definitions
   */
  @Transactional
  public void buildAndSaveInjectExpectations(
      ExecutableInject executableInject, List<Expectation> expectations) {
    doBuildAndSaveInjectExpectations(executableInject, expectations);
  }

  private void doBuildAndSaveInjectExpectations(
      ExecutableInject executableInject, List<Expectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return;
    }

    final boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();
    final boolean isScheduledInject = !executableInject.isDirect();
    final boolean isChainingExecution = executableInject.isChainingExecution();

    if (!isScheduledInject && !isAtomicTesting && !isChainingExecution) {
      return;
    }

    // Create the expectations
    final List<Team> teams = executableInject.getTeams();
    final List<Asset> assets = executableInject.getAssets();
    final List<AssetGroup> assetGroups = executableInject.getAssetGroups();

    List<BaseInjectExpectation> injectExpectations = new ArrayList<>();
    if (!teams.isEmpty()) {
      List<BaseInjectExpectation> injectExpectationsByUserAndTeam;
      // If atomicTesting, We create expectation for every player and every team
      if (isAtomicTesting) {
        injectExpectations =
            teams.stream()
                .flatMap(
                    team ->
                        expectations.stream()
                            .map(
                                expectation ->
                                    expectationConverter(
                                        team,
                                        executableInject,
                                        expectation,
                                        expectationPropertiesConfig)))
                .collect(Collectors.toList());

        injectExpectationsByUserAndTeam =
            teams.stream()
                .flatMap(
                    team ->
                        team.getUsers().stream()
                            .flatMap(
                                user ->
                                    expectations.stream()
                                        .map(
                                            expectation ->
                                                expectationConverter(
                                                    team,
                                                    user,
                                                    executableInject,
                                                    expectation,
                                                    expectationPropertiesConfig))))
                .toList();
      } else {
        final String exerciseId = executableInject.getInjection().getExercise().getId();
        // Create expectations for every enabled player in every team
        injectExpectationsByUserAndTeam =
            teams.stream()
                .flatMap(
                    team ->
                        team.getExerciseTeamUsers().stream()
                            .filter(
                                exerciseTeamUser ->
                                    exerciseTeamUser.getExercise().getId().equals(exerciseId))
                            .flatMap(
                                exerciseTeamUser ->
                                    expectations.stream()
                                        .map(
                                            expectation ->
                                                expectationConverter(
                                                    team,
                                                    exerciseTeamUser.getUser(),
                                                    executableInject,
                                                    expectation,
                                                    expectationPropertiesConfig))))
                .toList();

        // Create a set of teams that have at least one enabled player
        Set<Team> teamsWithEnabledPlayers =
            injectExpectationsByUserAndTeam.stream()
                .map(TableTopInjectExpectation.class::cast)
                .map(TableTopInjectExpectation::getTeam)
                .collect(Collectors.toSet());

        // Add only the expectations where the team has at least one enabled player
        injectExpectations =
            teamsWithEnabledPlayers.stream()
                .flatMap(
                    team ->
                        expectations.stream()
                            .map(
                                expectation ->
                                    expectationConverter(
                                        team,
                                        executableInject,
                                        expectation,
                                        expectationPropertiesConfig)))
                .collect(Collectors.toList());
      }
      injectExpectations.addAll(injectExpectationsByUserAndTeam);
    } else if (!assets.isEmpty()
        || !assetGroups.isEmpty()
        || expectations.stream().anyMatch(InjectExpectationService::carriesOwnTarget)) {
      // Technical expectations carry their own asset / asset group (they were built from the
      // resolved AssetToExecute list, which includes content-referenced AI targets that are NOT
      // attached to the inject as an asset / asset group relation). Gating only on the inject's
      // asset / group relations dropped every AI Red Team expectation on the floor - an AI target
      // reached through content.ai_target produced zero expectation rows. Convert whenever at
      // least one computed expectation carries a target of its own, regardless of how that target
      // was attached - but keep skipping target-less expectations (e.g. a manual email expectation
      // executed directly with no team), which have nothing to attach to.
      injectExpectations =
          expectations.stream()
              .map(
                  expectation ->
                      expectationConverter(
                          executableInject, expectation, expectationPropertiesConfig))
              .collect(Collectors.toList());
    }

    if (!injectExpectations.isEmpty()) {
      String tenantId = executableInject.getInjection().getInject().getTenant().getId();
      setupDefaultExpectationResults(injectExpectations, tenantId);
      injectExpectationRepository.saveAll(injectExpectations);
    }
  }

  /**
   * Whether a computed expectation carries its own validation target (asset or asset group), i.e.
   * it can be persisted even when the inject has no asset / asset group relation - the case of
   * content-referenced AI targets.
   */
  private static boolean carriesOwnTarget(Expectation expectation) {
    return switch (expectation) {
      case DetectionExpectation e -> e.getAsset() != null || e.getAssetGroup() != null;
      case PreventionExpectation e -> e.getAsset() != null || e.getAssetGroup() != null;
      case VulnerabilityExpectation e -> e.getAsset() != null || e.getAssetGroup() != null;
      default -> false;
    };
  }

  /**
   * Initializes the result field for each BaseInjectExpectation in the given list.
   *
   * <p>Correct initialization is critical: a simulation is considered finished when all
   * BaseInjectExpectation.results.result entries have a non-null result value.
   *
   * <p>For technical expectations (PREVENTION, DETECTION, VULNERABILITY), results are only set when
   * an agent is assigned
   *
   * <p>So in this function for all expected result we will set
   * BaseInjectExpectation.results[*].result = null
   *
   * @param injectExpectations the list of expectations to initialize
   * @param tenantId the tenant ID to scope collector lookup
   */
  private void setupDefaultExpectationResults(
      @NotNull final List<BaseInjectExpectation> injectExpectations,
      @NotBlank final String tenantId) {
    List<Collector> collectors = collectorService.securityPlatformCollectors(tenantId);

    injectExpectations.forEach(
        ie -> {
          if (ie instanceof TechnicalInjectExpectation tech) {
            if (tech.getAgent() == null) {
              return;
            }
            if (ie instanceof PreventionInjectExpectation
                || ie instanceof DetectionInjectExpectation) {
              // Focus the pending results on the collectors of the expected security platform
              // types only. Empty/null = every connected security platform (legacy behaviour).
              List<Collector> expectedCollectors =
                  filterCollectorsForExpectation(collectors, tech.getExpectedSecurityPlatforms());
              applyExpirationOrderingGuarantee(tech, expectedCollectors);
              ie.setResults(setUpFromCollectors(expectedCollectors));
            } else if (ie instanceof VulnerabilityInjectExpectation) {
              ie.setResults(List.of(buildDefaultForVulnerabilityManagerInFailed()));
            }

          } else if (ie instanceof TableTopInjectExpectation tableTop) {
            if (tableTop.getUser() == null) {
              return;
            }
            if (ie instanceof ManualInjectExpectation) {
              ie.setResults(List.of(buildDefaultForPlayerManualValidation()));
            } else if (ie instanceof ChallengeInjectExpectation) {
              // TODO : The UI needs to be fixed: when the score and result are initialized to
              // null, the user can no longer validate the flag.
              // ie.setResults(List.of(ChallengeExpectationUtils.buildDefaultChallengeInjectExpectationResult()));
            } else if (ie instanceof ArticleInjectExpectation) {
              ie.setResults(List.of(buildDefaultForMediaPressure()));
            }
          }
        });
  }

  /**
   * Restricts the tenant's security-platform collectors to those matching an expectation's expected
   * platform types. When the expectation declares no expected type, every collector is kept (legacy
   * behaviour). Expected types that have no connected collector are logged so the misconfiguration
   * is visible instead of the expectation silently hanging until expiration.
   *
   * @param collectors all connected security-platform collectors of the tenant
   * @param expectedTypes the expectation's expected security platform types (may be null/empty)
   * @return the collectors expected to answer this expectation
   */
  static List<Collector> filterCollectorsForExpectation(
      final List<Collector> collectors,
      final List<SecurityPlatform.SECURITY_PLATFORM_TYPE> expectedTypes) {
    if (expectedTypes == null || expectedTypes.isEmpty()) {
      return collectors;
    }
    List<Collector> matching =
        collectors.stream()
            .filter(
                c ->
                    c.getSecurityPlatform() != null
                        && expectedTypes.contains(
                            c.getSecurityPlatform().getSecurityPlatformType()))
            .toList();
    Set<SecurityPlatform.SECURITY_PLATFORM_TYPE> connectedTypes =
        collectors.stream()
            .map(Collector::getSecurityPlatform)
            .filter(Objects::nonNull)
            .map(SecurityPlatform::getSecurityPlatformType)
            .collect(Collectors.toSet());
    expectedTypes.stream()
        .filter(type -> !connectedTypes.contains(type))
        .forEach(
            type ->
                log.warn(
                    "Expectation expects security platform type {} but no connected collector of that type exists; it will only be finalized by the expiration manager",
                    type));
    return matching;
  }

  /**
   * Expiration ordering guarantee: when specific security platforms are expected, make sure the
   * expectation's expiration is long enough for the real collectors to answer first (at least two
   * of their poll cycles), so the expiration manager only ever acts as a fallback for genuinely
   * unanswered expectations.
   *
   * @param expectation the technical expectation being seeded
   * @param expectedCollectors the collectors expected to answer it
   */
  static void applyExpirationOrderingGuarantee(
      final TechnicalInjectExpectation expectation, final List<Collector> expectedCollectors) {
    if (expectedCollectors.isEmpty()) {
      return;
    }
    long maxPeriodSeconds =
        expectedCollectors.stream().mapToLong(Collector::getPeriod).max().orElse(0L);
    long floor = maxPeriodSeconds * 2L;
    if (expectation.getExpirationTime() == null || expectation.getExpirationTime() < floor) {
      expectation.setExpirationTime(floor);
    }
  }

  /**
   * Function used to check if the output contains vulnerabilities and update the related inject
   * expectations with the result.
   *
   * <p>For implant executions (agent != null), the fetched expectations are all scoped to the
   * executing agent — a single asset — so the global "any CVE found" verdict is that asset's
   * verdict.
   *
   * <p>For injector executions (agent == null, e.g. Nuclei scanning several assets at once), the
   * structured output covers ALL scanned assets: a CVE found on one asset must NOT mark the sibling
   * assets vulnerable. Each asset-level expectation gets its own verdict from the CVE items
   * attributed to it (via {@code asset_id}, falling back to {@code host} matching), and asset-group
   * expectations roll up from those per-asset verdicts.
   *
   * @param ctx the execution processing context containing the inject and agent information
   * @param jsonNode the JSON node containing the output to check for vulnerabilities
   */
  public void matchesVulnerabilityExpectations(ExecutionProcessingContext ctx, JsonNode jsonNode) {
    boolean anyVulnerable =
        jsonNode != null
            && !jsonNode.isMissingNode()
            && jsonNode.isContainerNode()
            && !jsonNode.isEmpty();

    Inject inject = ctx.inject();
    Agent agent = ctx.agent();

    List<VulnerabilityInjectExpectation> expectations =
        fetchVulnerabilityExpectations(inject, agent);

    if (expectations.isEmpty()) {
      return;
    }

    // Assessment injectors (e.g. Nuclei) run the vulnerability assessment themselves and declare
    // a security platform entry: attribute the verdict to that platform so it renders as a real
    // per-platform row, like detection/prevention. Injectors without a platform keep the legacy
    // attribution to the generic Expectations Vulnerability Manager.
    Optional<SecurityPlatform> securityPlatform = resolveInjectorSecurityPlatform(inject);

    if (agent != null) {
      // Implant execution: every fetched expectation is bound to this agent, hence to a single
      // asset — the global verdict IS this asset's verdict.
      expectations.forEach(
          expectation -> applyVulnerabilityVerdict(expectation, anyVulnerable, securityPlatform));
      return;
    }

    VulnerableAssetResolution resolution = resolveVulnerableAssetIds(jsonNode, inject);
    Set<String> vulnerableAssetIds = resolution.assetIds();
    // Injector outputs may carry findings without any per-asset attribution (legacy formats, or a
    // CVE item whose host matches no targeted asset): fall back to the legacy blanket verdict for
    // those, even when sibling items are attributed, since a false positive on a sibling asset
    // beats silently losing the finding.
    boolean blanketVulnerable =
        anyVulnerable && (vulnerableAssetIds.isEmpty() || resolution.hasUnattributedFinding());

    List<VulnerabilityInjectExpectation> assetExpectations =
        expectations.stream().filter(expectation -> expectation.getAsset() != null).toList();
    List<VulnerabilityInjectExpectation> assetGroupExpectations =
        expectations.stream()
            .filter(
                expectation ->
                    expectation.getAsset() == null && expectation.getAssetGroup() != null)
            .toList();

    // The verdict of an asset-level expectation is a pure function of its asset: vulnerable when
    // at least one CVE item is attributed to it.
    Predicate<VulnerabilityInjectExpectation> isAssetVulnerable =
        expectation ->
            blanketVulnerable || vulnerableAssetIds.contains(expectation.getAsset().getId());

    for (VulnerabilityInjectExpectation expectation : assetExpectations) {
      applyVulnerabilityVerdict(expectation, isAssetVulnerable.test(expectation), securityPlatform);
    }

    // Group verdict rolls up from the children verdicts (same rule as score propagation in
    // computeScores) and is written with the same source attribution, so the group row
    // immediately shows WHO assessed it instead of staying an unattributed "Vulnerable".
    for (VulnerabilityInjectExpectation groupExpectation : assetGroupExpectations) {
      List<Boolean> childVerdicts =
          assetExpectations.stream()
              .filter(
                  expectation ->
                      expectation.getAssetGroup() != null
                          && expectation
                              .getAssetGroup()
                              .getId()
                              .equals(groupExpectation.getAssetGroup().getId()))
              .map(isAssetVulnerable::test)
              .toList();
      boolean groupVulnerable;
      if (childVerdicts.isEmpty()) {
        groupVulnerable = anyVulnerable;
      } else if (groupExpectation.isExpectationGroup()) {
        // "At least one asset must succeed": the group stays clean unless ALL assets are
        // vulnerable.
        groupVulnerable = childVerdicts.stream().allMatch(Boolean::booleanValue);
      } else {
        // Default "all assets must succeed": one vulnerable asset makes the group vulnerable.
        groupVulnerable = childVerdicts.stream().anyMatch(Boolean::booleanValue);
      }
      applyVulnerabilityVerdict(groupExpectation, groupVulnerable, securityPlatform);
    }
  }

  /**
   * Writes a vulnerability verdict on a single expectation, attributed either to the injector's
   * security platform (assessment injectors such as Nuclei) or to the generic Expectations
   * Vulnerability Manager collector, then lets the standard update path recompute the score and
   * propagate up the asset / asset group chain.
   *
   * @param expectation the vulnerability expectation to conclude
   * @param vulnerable whether the target of this expectation was found vulnerable
   * @param securityPlatform the injector's security platform, when one is declared
   */
  private void applyVulnerabilityVerdict(
      VulnerabilityInjectExpectation expectation,
      boolean vulnerable,
      Optional<SecurityPlatform> securityPlatform) {
    InjectExpectationUpdateInput.InjectExpectationUpdateInputBuilder input =
        InjectExpectationUpdateInput.builder()
            .result(vulnerable ? VULNERABILITY.failureLabel : VULNERABILITY.successLabel)
            .isSuccess(!vulnerable);
    if (securityPlatform.isPresent()) {
      updateInjectExpectationFromSecurityPlatform(
          expectation.getId(), input.build(), securityPlatform.get());
    } else {
      updateInjectExpectation(
          expectation.getId(), input.collectorId(EXPECTATIONS_VULNERABILITY_COLLECTOR_ID).build());
    }
  }

  /**
   * Resolves which targeted assets the CVE items of the structured output are attributed to.
   *
   * <p>Attribution mirrors finding attribution ({@link
   * io.openaev.output_processor.CVEOutputProcessor}): the {@code asset_id} field is authoritative;
   * when absent, the {@code host} values are matched against the inject's targeted assets (the host
   * string containing a targeted hostname/IP counts as a match, like {@code
   * FindingService#resolveAssetFromStructuredOutput}).
   *
   * @param cveNode the structured output node (array of CVE items)
   * @param inject the inject being processed
   * @return the ids of the assets found vulnerable, and whether any CVE item could not be
   *     attributed to any targeted asset at all
   */
  private VulnerableAssetResolution resolveVulnerableAssetIds(
      @Nullable JsonNode cveNode, Inject inject) {
    if (cveNode == null || !cveNode.isArray()) {
      return new VulnerableAssetResolution(Set.of(), false);
    }
    Set<String> vulnerableAssetIds = new HashSet<>();
    boolean hasUnattributedFinding = false;
    Map<String, Endpoint> valueTargetedAssetsMap = null;
    for (JsonNode cveItem : cveNode) {
      boolean attributed =
          collectTextValues(cveItem.get(CVEOutputProcessor.ASSET_ID), vulnerableAssetIds);
      if (attributed) {
        continue;
      }
      Set<String> hosts = new HashSet<>();
      collectTextValues(cveItem.get(CVEOutputProcessor.HOST), hosts);
      if (hosts.isEmpty()) {
        hasUnattributedFinding = true;
        continue;
      }
      if (valueTargetedAssetsMap == null) {
        valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);
      }
      boolean hostMatched = false;
      for (Map.Entry<String, Endpoint> entry : valueTargetedAssetsMap.entrySet()) {
        if (hosts.stream().anyMatch(host -> host.contains(entry.getKey()))) {
          vulnerableAssetIds.add(entry.getValue().getId());
          hostMatched = true;
        }
      }
      if (!hostMatched) {
        hasUnattributedFinding = true;
      }
    }
    return new VulnerableAssetResolution(vulnerableAssetIds, hasUnattributedFinding);
  }

  /**
   * Result of the per-asset attribution of the CVE items of a structured output.
   *
   * @param assetIds the ids of the targeted assets attributed at least one CVE item
   * @param hasUnattributedFinding true when at least one CVE item carries no usable attribution (no
   *     {@code asset_id}, and no {@code host} matching a targeted asset)
   */
  private record VulnerableAssetResolution(Set<String> assetIds, boolean hasUnattributedFinding) {}

  /**
   * Collects the non-blank text value(s) of a JSON node (plain text or array of texts) into the
   * given set.
   *
   * @param node the JSON node to read (may be null)
   * @param into the set collecting the values
   * @return true when at least one value was collected
   */
  private static boolean collectTextValues(@Nullable JsonNode node, Set<String> into) {
    if (node == null || node.isNull()) {
      return false;
    }
    boolean collected = false;
    if (node.isArray()) {
      for (JsonNode item : node) {
        collected |= collectTextValues(item, into);
      }
      return collected;
    }
    String text = node.asText(null);
    if (text != null && !text.isBlank()) {
      into.add(text);
      return true;
    }
    return false;
  }

  /**
   * Resolves the security platform declared by the injector executing the given inject.
   *
   * <p>Assessment injectors (e.g. Nuclei) register a security platform whose {@code
   * asset_external_reference} is their injector type; when such a platform exists, vulnerability
   * verdicts are attributed to it instead of the generic Expectations Vulnerability Manager.
   *
   * @param inject the inject being processed
   * @return the injector's security platform, when one is declared
   */
  private Optional<SecurityPlatform> resolveInjectorSecurityPlatform(@NotNull final Inject inject) {
    return Optional.ofNullable(inject.getType())
        .flatMap(securityPlatformRepository::findByExternalReference);
  }

  /**
   * Function used to fetch inject expectations of type VULNERABILITY for a given inject and agent.
   *
   * @param inject the inject for which to fetch the expectations
   * @param agent the agent for which to fetch the expectations
   * @return the list of inject expectations of type VULNERABILITY for the given inject and agent
   */
  private static List<VulnerabilityInjectExpectation> fetchVulnerabilityExpectations(
      Inject inject, Agent agent) {
    String agentId = agent != null ? agent.getId() : null;
    return inject.getExpectations().stream()
        .filter(exp -> exp instanceof VulnerabilityInjectExpectation)
        .map(exp -> (VulnerabilityInjectExpectation) exp)
        .filter(
            exp -> {
              Agent expAgent = exp.getAgent();
              if (agentId == null) {
                // For injector executions (agent == null), match expectations not bound to any
                // agent
                return expAgent == null;
              }
              return expAgent != null && agentId.equals(expAgent.getId());
            })
        .toList();
  }

  /**
   * Converts the inject content payload to a typed object.
   *
   * <p>The content is read from {@link Inject#getContent()} and deserialized with Jackson using the
   * provided target class.
   *
   * @param injection the executable inject containing the source content
   * @param converter the target class used for conversion
   * @return the converted content instance
   * @param <T> the target content type
   * @throws JsonProcessingException if the JSON content cannot be converted to the requested type
   */
  public <T> T contentConvert(
      @NotNull final ExecutableInject injection, @NotNull final Class<T> converter)
      throws JsonProcessingException {
    Inject inject = injection.getInjection().getInject();
    ObjectNode content = inject.getContent();
    return this.mapper.treeToValue(content, converter);
  }

  @Transactional(rollbackFor = Exception.class)
  public void computeAndSaveExpectations(
      ExecutableInject injection,
      Inject inject,
      String implantType,
      List<AssetToExecute> assetToExecutes)
      throws JsonProcessingException {
    BaseInjectContent content = contentConvert(injection, BaseInjectContent.class);

    // Execution-time fallback: injects created before their contract declared predefined
    // expectations (e.g. Nuclei injects in existing simulations) carry no expectations FIELD in
    // their stored content, so resetting and relaunching the simulation would silently create
    // none. Read the predefined expectations from the injector contract instead, exactly like
    // inject creation and the chaining engine do. An EXPLICIT empty list is a different thing:
    // it means the user deliberately removed every expectation from the inject, and that choice
    // is never overridden here - expectation drift realignment is the opt-in way to restore the
    // contract template.
    if (content.getExpectations().isEmpty()
        && contentNeverCarriedExpectations(inject)
        && inject.getInjectorContract().isPresent()) {
      ObjectNode storedContent = inject.getContent();
      ObjectNode enrichedContent =
          injectorContractContentUtils.setExpectations(
              inject.getInjectorContract().get(),
              storedContent != null ? storedContent.deepCopy() : null);
      if (enrichedContent != null) {
        content = this.mapper.treeToValue(enrichedContent, BaseInjectContent.class);
      }
    }
    final BaseInjectContent resolvedContent = content;

    List<Expectation> expectations = new ArrayList<>();

    assetToExecutes.forEach(
        assetToExecute ->
            computeExpectationsForAssetAndAgents(
                expectations, resolvedContent, assetToExecute, inject, implantType));

    List<AssetGroup> assetGroups = injection.getAssetGroups();
    assetGroups.forEach(
        (assetGroup ->
            computeExpectationsForAssetGroup(expectations, resolvedContent, assetGroup)));

    doBuildAndSaveInjectExpectations(injection, expectations);
  }

  /**
   * Whether the stored inject content never carried the expectations field at all: the inject was
   * created before its injector contract declared predefined expectations, so it follows the
   * contract template dynamically at execution time (same semantics as the expectation drift
   * detection). An explicit empty array is NOT "never carried": it means the user deliberately
   * removed every expectation from the inject, and that customization must be respected.
   */
  private static boolean contentNeverCarriedExpectations(@NotNull final Inject inject) {
    ObjectNode storedContent = inject.getContent();
    if (storedContent == null) {
      return true;
    }
    JsonNode expectationsNode =
        storedContent.get(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    return expectationsNode == null || expectationsNode.isNull();
  }

  /** In case of direct assetToExecute, we have an individual expectation for the assetToExecute */
  private void computeExpectationsForAssetAndAgents(
      @NotNull final List<Expectation> expectations,
      @NotNull final BaseInjectContent content,
      @NotNull final AssetToExecute assetToExecute,
      final Inject inject,
      String implantType) {

    if (!content.getExpectations().isEmpty()) {

      Map<String, Endpoint> valueTargetedAssetsMap = injectService.getValueTargetedAssetMap(inject);

      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION ->
                            getPreventionExpectationsByAsset(
                                implantType,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject)
                                .stream();
                        case DETECTION ->
                            getDetectionExpectationsByAsset(
                                implantType,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject)
                                .stream();
                        case VULNERABILITY ->
                            getVulnerabilityExpectationsByAsset(
                                implantType,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                valueTargetedAssetsMap,
                                inject)
                                .stream();
                        case MANUAL ->
                            getManualExpectationsByAsset(
                                implantType,
                                assetToExecute,
                                getActiveAgents(assetToExecute.asset(), inject),
                                expectation,
                                inject)
                                .stream();
                        default -> Stream.of();
                      })
              .toList());
    }
  }

  /**
   * In case of asset group if expectation group -> we have an expectation for the group and one for
   * each asset if not expectation group -> we have an individual expectation for each asset
   */
  private void computeExpectationsForAssetGroup(
      @NotNull final List<Expectation> expectations,
      @NotNull final BaseInjectContent content,
      @NotNull final AssetGroup assetGroup) {
    if (!content.getExpectations().isEmpty()) {
      List<Asset> assets = this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  expectation ->
                      switch (expectation.getType()) {
                        case PREVENTION -> {
                          // Verify that at least one asset in the group has been executed
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              prevExpectation ->
                                                  BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION
                                                      == prevExpectation.type())
                                          .anyMatch(
                                              prevExpectation ->
                                                  ((PreventionExpectation) prevExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((PreventionExpectation) prevExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                preventionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case DETECTION -> {
                          // Verify that at least one asset in the group has been executed
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              detExpectation ->
                                                  BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
                                                      == detExpectation.type())
                                          .anyMatch(
                                              detExpectation ->
                                                  ((DetectionExpectation) detExpectation).getAsset()
                                                          != null
                                                      && ((DetectionExpectation) detExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                detectionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case VULNERABILITY -> {
                          // Verify that at least one asset in the group has been executed
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              vulExpectation ->
                                                  BaseInjectExpectation.EXPECTATION_TYPE
                                                          .VULNERABILITY
                                                      == vulExpectation.type())
                                          .anyMatch(
                                              vulExpectation ->
                                                  ((VulnerabilityExpectation) vulExpectation)
                                                              .getAsset()
                                                          != null
                                                      && ((VulnerabilityExpectation) vulExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                vulnerabilityExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case MANUAL -> {
                          // Verify that at least one asset in the group has been executed
                          if (assets.stream()
                              .anyMatch(
                                  asset ->
                                      expectations.stream()
                                          .filter(
                                              manExpectation ->
                                                  BaseInjectExpectation.EXPECTATION_TYPE.MANUAL
                                                      == manExpectation.type())
                                          .anyMatch(
                                              manExpectation ->
                                                  ((ManualExpectation) manExpectation).getAsset()
                                                          != null
                                                      && ((ManualExpectation) manExpectation)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                manualExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.getExpirationTime(),
                                    expectation.isExpectationGroup()));
                          }
                          yield Stream.of();
                        }
                        default -> Stream.of();
                      })
              .toList());
    }
  }

  // -- AUDIT LOGGING --

  private String toExpectationAuditResultStatus(@NotNull BaseInjectExpectation expectation) {
    BaseInjectExpectation.EXPECTATION_STATUS response = expectation.getResponse();
    if (response == null) {
      return "pending";
    }
    return switch (response) {
      case SUCCESS -> "met";
      case PENDING -> "pending";
      default -> "not_met";
    };
  }

  private void logExpectationResultEvent(
      @NotNull BaseInjectExpectation expectation,
      @Nullable InjectExpectationResult sourceResult,
      @NotNull AuditEventOrigin origin,
      @NotBlank String sourceType) {
    auditLogger.ifPresent(
        logger -> {
          String injectId =
              expectation.getInject() != null ? expectation.getInject().getId() : null;
          String expectationResult = toExpectationAuditResultStatus(expectation);
          String source =
              sourceResult != null && sourceResult.getSourceName() != null
                  ? sourceResult.getSourceName()
                  : SessionHelper.currentUser().getId();
          String sourceId = sourceResult != null ? sourceResult.getSourceId() : null;
          String detectionTimestamp = sourceResult != null ? sourceResult.getDate() : null;

          Map<String, Object> contextData = new LinkedHashMap<>();
          contextData.put("inject_id", injectId);
          contextData.put("expectation_id", expectation.getId());
          contextData.put("expectation_type", expectation.getType().name());
          contextData.put("result", expectationResult);
          contextData.put("source", source);
          contextData.put("source_id", sourceId);
          contextData.put("source_type", sourceType);
          contextData.put("execution_timestamp", Instant.now().toString());
          contextData.put("detection_timestamp", detectionTimestamp);

          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.EXECUTION)
                  .eventScope(AuditEventScope.EXPECTATION_RESULT)
                  .eventStatus(EventStatus.SUCCESS)
                  .resourceType(ResourceType.INJECT)
                  .resourceId(injectId)
                  .message(
                      "Expectation '%s' result: %s"
                          .formatted(expectation.getType().name(), expectationResult))
                  .contextData(contextData)
                  .origin(origin)
                  .build());
        });
  }

  /**
   * Emits the standard expectation-result audit event for automatic/system updates attributed to
   * the provided collector.
   *
   * @param expectation the expectation whose result has just been computed
   * @param collector the collector used as audit source attribution
   */
  public void logAutomaticExpectationResult(
      @NotNull final BaseInjectExpectation expectation, @NotNull final Collector collector) {
    logAutomaticExpectationResult(expectation, collector.getId(), collector.getName());
  }

  private void logAutomaticExpectationResult(
      @NotNull final BaseInjectExpectation expectation,
      @NotBlank final String sourceId,
      @Nullable final String sourceName) {
    InjectExpectationResult sourceResult = findResultBySourceId(expectation.getResults(), sourceId);
    if (sourceResult == null) {
      sourceResult =
          InjectExpectationResult.builder()
              .sourceId(sourceId)
              .sourceName(sourceName)
              .date(Instant.now().toString())
              .build();
    }
    logExpectationResultEvent(
        expectation, sourceResult, AuditEventOrigin.SYSTEM, EXPECTATION_SOURCE_TYPE_AUTOMATIC);
  }
}
