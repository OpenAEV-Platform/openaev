package io.openaev.service;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.InjectExpectationUtils.computeScores;
import static io.openaev.service.InjectExpectationUtils.expectationConverter;
import static io.openaev.utils.AgentUtils.getPrimaryAgents;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.specification.InjectExpectationSpecification;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.expectation.ExpectationType;
import io.openaev.model.Expectation;
import io.openaev.rest.atomic_testing.form.InjectExpectationAgentOutput;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.utils.TargetType;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  /**
   * Upper bound for the collector-polled "not filled" queries. Collectors poll periodically (oldest
   * first), so anything beyond the bound is returned on a subsequent poll.
   */
  private static final int NOT_FILLED_FETCH_LIMIT = 10_000;

  private final InjectExpectationRepository injectExpectationRepository;
  private final CollectorService collectorService;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;

  @Resource protected ObjectMapper mapper;

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

  // -- UPDATE FROM UI --

  /**
   * Updates an inject expectation
   *
   * @param expectationId the ID of the expectation to update
   * @param input the update input containing the new data
   * @return the updated inject expectation
   * @throws IllegalArgumentException if trying to update an Asset Group expectation directly
   */
  public BaseInjectExpectation updateInjectExpectation(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    BaseInjectExpectation BaseInjectExpectation = this.findInjectExpectation(expectationId);

    if (HUMAN_EXPECTATION.contains(BaseInjectExpectation.getType())) {
      String result =
          ExpectationType.label(
              BaseInjectExpectation.getType(),
              BaseInjectExpectation.getExpectedScore(),
              input.getScore());
      computeInjectExpectationForHumanResponse(BaseInjectExpectation, input, result);
      BaseInjectExpectation updated = this.injectExpectationRepository.save(BaseInjectExpectation);
      propagateHumanResponseExpectation(updated, result);
      return updated;
    } else if (List.of(DETECTION, PREVENTION).contains(BaseInjectExpectation.getType())) {
      // Block down computation on asset group
      if (isAssetGroupExpectation(BaseInjectExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Allow down computation on asset
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(BaseInjectExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(BaseInjectExpectation) && !isAgentless) {
        List<BaseInjectExpectation> expectationsForAgents =
            getExpectationsAgentsForAsset(BaseInjectExpectation);
        expectationsForAgents.forEach(
            e -> computeInjectExpectationForAgentOrAssetAgentless(e, input));
        this.injectExpectationRepository.saveAll(expectationsForAgents);
        propagateTechnicalExpectation(BaseInjectExpectation, isAgentless, null);
        return BaseInjectExpectation;
        // Computation on agent or asset agentless
      } else {
        computeInjectExpectationForAgentOrAssetAgentless(BaseInjectExpectation, input);
        BaseInjectExpectation updated =
            this.injectExpectationRepository.save(BaseInjectExpectation);
        propagateTechnicalExpectation(updated, isAgentless, null);
        return updated;
      }
    }
    return BaseInjectExpectation;
  }

  // -- DELETE RESULT FROM UI --

  /**
   * Deletes a specific result from an inject expectation.
   *
   * @param expectationId the ID of the expectation
   * @param sourceId the ID of the source result to delete
   * @return the updated inject expectation
   * @throws IllegalArgumentException if trying to delete from an Asset Group or Asset with Agent
   */
  public BaseInjectExpectation deleteInjectExpectationResult(
      @NotBlank final String expectationId, @NotBlank final String sourceId) {
    BaseInjectExpectation BaseInjectExpectation =
        this.injectExpectationRepository.findById(expectationId).orElseThrow();
    deleteResult(BaseInjectExpectation, sourceId);
    BaseInjectExpectation updated = this.injectExpectationRepository.save(BaseInjectExpectation);
    if (HUMAN_EXPECTATION.contains(BaseInjectExpectation.getType())) {
      propagateHumanResponseExpectation(updated, null);
    } else if (List.of(DETECTION, PREVENTION).contains(BaseInjectExpectation.getType())) {
      // Block down computation
      // Not asset group
      if (isAssetGroupExpectation(BaseInjectExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Not Endpoint if no agentless
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(BaseInjectExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(BaseInjectExpectation) && !isAgentless) {
        throw new IllegalArgumentException(
            "Not possible to update Asset directly on Asset with Agent");
      }
      propagateTechnicalExpectation(updated, isAgentless, null);
    }

    return updated;
  }

  //  -- HUMAN RESPONSE --

  /**
   * Computes an inject expectation for a human response
   *
   * @param BaseInjectExpectation the expectation to compute
   * @param input the update input containing the score
   * @param result the result label
   */
  private void computeInjectExpectationForHumanResponse(
      @NotNull BaseInjectExpectation BaseInjectExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotBlank final String result) {
    // Keep only one result
    BaseInjectExpectation.getResults().clear();
    addResult(BaseInjectExpectation, input, result);
    final Double score = computeScore(BaseInjectExpectation.getResults(), BaseInjectExpectation);
    BaseInjectExpectation.setScore(score);
  }

  /**
   * Computes an inject expectation for a human response from a collector.
   *
   * @param BaseInjectExpectation the expectation to compute
   * @param input the update input containing the response
   * @param collector the collector submitting the response
   * @return the updated inject expectation
   */
  public BaseInjectExpectation computeInjectExpectationForHumanResponse(
      @NotNull BaseInjectExpectation BaseInjectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    // Keep only one result
    BaseInjectExpectation.getResults().clear();
    addResult(BaseInjectExpectation, input, collector);
    final Double score = computeScore(BaseInjectExpectation.getResults(), BaseInjectExpectation);
    BaseInjectExpectation.setScore(score);
    return BaseInjectExpectation;
  }

  /**
   * Propagates a human response expectation update to related expectations.
   *
   * <p>If the expectation belongs to a player, propagates to the team. If the expectation belongs
   * to a team, propagates to all players.
   *
   * @param BaseInjectExpectation the updated expectation
   * @param result the result label to propagate
   */
  private void propagateHumanResponseExpectation(
      @NotNull BaseInjectExpectation BaseInjectExpectation, @Nullable final String result) {
    // If the updated expectation was a player expectation, We have to update the team expectation
    // using player expectations (based on validation type)
    List<BaseInjectExpectation> expectations = new ArrayList<>();
    if (BaseInjectExpectation.getUser() != null) {
      expectations.addAll(propagateToTeam(BaseInjectExpectation, result));
    } else {
      expectations.addAll(propagateToPlayers(BaseInjectExpectation, result));
    }
    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(BaseInjectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates a team expectation update to all player expectations.
   *
   * @param BaseInjectExpectation the team expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated player expectations
   */
  private List<BaseInjectExpectation> propagateToPlayers(
      @NotNull final BaseInjectExpectation BaseInjectExpectation, @Nullable final String result) {
    // If I update the expectation team: What happens with children? -> update expectation score
    // for all children -> set score from BaseInjectExpectation
    List<BaseInjectExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(BaseInjectExpectation);
    for (BaseInjectExpectation expectationsForPlayer : expectationsForPlayers) {
      expectationsForPlayer.getResults().clear();
      if (result != null) {
        expectationsForPlayer
            .getResults()
            .add(buildForTeamManualValidation(result, BaseInjectExpectation.getScore()));
      }
      expectationsForPlayer.setScore(BaseInjectExpectation.getScore());
    }
    return expectationsForPlayers;
  }

  /**
   * Propagates a player expectation update to the team expectation.
   *
   * @param BaseInjectExpectation the player expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated team expectations
   */
  private List<BaseInjectExpectation> propagateToTeam(
      @NotNull final BaseInjectExpectation BaseInjectExpectation, @Nullable final String result) {
    List<BaseInjectExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(BaseInjectExpectation);
    List<BaseInjectExpectation> expectationForTeams = getExpectationTeams(BaseInjectExpectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        BaseInjectExpectation,
        score -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }

  // -- TECHNICAL --

  /**
   * Computes a technical expectation for an agent or agentless asset
   *
   * @param BaseInjectExpectation the expectation to compute
   * @param input the update input containing the score
   */
  private void computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final BaseInjectExpectation BaseInjectExpectation,
      @NotNull final ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            BaseInjectExpectation.getType(),
            BaseInjectExpectation.getExpectedScore(),
            input.getScore());
    addResult(BaseInjectExpectation, input, result);
    final Double score = computeScore(BaseInjectExpectation.getResults(), BaseInjectExpectation);
    BaseInjectExpectation.setScore(score);
  }

  /**
   * Propagates a technical expectation update up the hierarchy (agent to asset to asset group).
   *
   * @param BaseInjectExpectation the expectation that was updated
   * @param isAgentless whether the asset has no agent
   * @param addResult optional function to create a result from a score
   */
  private void propagateTechnicalExpectation(
      @NotNull final BaseInjectExpectation BaseInjectExpectation,
      final boolean isAgentless,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<BaseInjectExpectation> expectations = new ArrayList<>();
    // 1) Agent -> Asset
    if (!isAgentless) {
      expectations.addAll(propagateToAsset(BaseInjectExpectation, addResult));
    }

    // 2) Asset -> Asset Group
    expectations.addAll(propagateToAssetGroup(BaseInjectExpectation, addResult));

    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(BaseInjectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates an agent expectation update to the asset expectation.
   *
   * @param BaseInjectExpectation the agent expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset expectations
   */
  private List<BaseInjectExpectation> propagateToAsset(
      @NotNull final BaseInjectExpectation BaseInjectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<BaseInjectExpectation> expectationsForAgents =
        getExpectationsAgentsForAsset(BaseInjectExpectation);
    List<BaseInjectExpectation> expectationsForAssets =
        getExpectationsAssets(BaseInjectExpectation);
    computeScores(expectationsForAgents, expectationsForAssets, BaseInjectExpectation, addResult);
    return expectationsForAssets;
  }

  /**
   * Propagates an asset expectation update to the asset group expectation.
   *
   * @param BaseInjectExpectation the asset expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset group expectations, or empty list if no asset group
   */
  private List<BaseInjectExpectation> propagateToAssetGroup(
      @NotNull final BaseInjectExpectation BaseInjectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    if (BaseInjectExpectation.getAssetGroup() != null) {
      List<BaseInjectExpectation> expectationsForAssets =
          getExpectationsAssetsForAssetGroup(BaseInjectExpectation);
      List<BaseInjectExpectation> expectationForAssetGroups =
          getExpectationAssetGroups(BaseInjectExpectation);
      computeScores(
          expectationsForAssets, expectationForAssetGroups, BaseInjectExpectation, addResult);
      return expectationForAssetGroups;
    }
    return new ArrayList<>();
  }

  // -- UPDATE FROM EXTERNAL SOURCE : COLLECTORS --

  /**
   * Updates an inject expectation from an external collector source.
   *
   * @param expectationId the ID of the expectation to update
   * @param input the update input from the collector
   * @return the updated inject expectation
   */
  public BaseInjectExpectation updateInjectExpectation(
      @NotBlank String expectationId, @Valid @NotNull InjectExpectationUpdateInput input) {
    BaseInjectExpectation BaseInjectExpectation = this.findInjectExpectation(expectationId);
    Collector collector = this.collectorService.collector(input.getCollectorId());

    computeTechnicalExpectation(BaseInjectExpectation, collector, input, false);

    return BaseInjectExpectation;
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

    List<BaseInjectExpectation> injectExpectations =
        fromIterable(this.injectExpectationRepository.findAllById(inputs.keySet()));
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
   * @param BaseInjectExpectation the expectation to compute
   * @param collector the collector submitting the result
   * @param input the update input
   * @param shouldPropagateLastInjectExpectationResult whether to propagate the last result
   */
  public void computeTechnicalExpectation(
      BaseInjectExpectation BaseInjectExpectation,
      Collector collector,
      InjectExpectationUpdateInput input,
      boolean shouldPropagateLastInjectExpectationResult) {
    // Update inject expectation at agent level
    BaseInjectExpectation =
        this.computeInjectExpectationForAgentOrAssetAgentless(
            BaseInjectExpectation, input, collector);
    BaseInjectExpectation updated = this.injectExpectationRepository.save(BaseInjectExpectation);
    propagateTechnicalExpectation(
        updated,
        false,
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
  public void bulkComputeTechnicalExpectations(
      @NotNull final List<BaseInjectExpectation> expectations,
      @NotNull final Map<String, InjectExpectationUpdateInput> inputsById,
      @NotNull final Collector collector,
      final boolean shouldPropagateLastInjectExpectationResult) {
    // 1) Agent-level updates, one batched save
    List<BaseInjectExpectation> updatedExpectations = new ArrayList<>(expectations.size());
    for (BaseInjectExpectation expectation : expectations) {
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
    List<BaseInjectExpectation> saved =
        fromIterable(this.injectExpectationRepository.saveAll(updatedExpectations));

    // 2) Propagation deduplicated per parent: recomputing an asset (or asset group) score reads
    // all its children, so one pass per distinct parent is equivalent to one pass per item
    Map<String, BaseInjectExpectation> assetPropagations = new LinkedHashMap<>();
    Map<String, BaseInjectExpectation> assetGroupPropagations = new LinkedHashMap<>();
    for (BaseInjectExpectation updated : saved) {
      if (updated.getAsset() != null) {
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
    for (BaseInjectExpectation reference : assetPropagations.values()) {
      parents.addAll(
          propagateToAsset(
              reference,
              shouldPropagateLastInjectExpectationResult
                  ? score -> reference.getResults().getLast()
                  : null));
    }
    for (BaseInjectExpectation reference : assetGroupPropagations.values()) {
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
   * @param expectation the expectation to compute
   * @param input the update input
   * @param collector the collector submitting the result
   * @return the updated inject expectation
   */
  public BaseInjectExpectation computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final BaseInjectExpectation expectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    addResult(expectation, input, collector);
    final Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
    return expectation;
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
   * Retrieves a page of inject expectations that have not been filled (no score and no results or
   * has an agent).
   *
   * @return a page of unfilled inject expectations ordered by creation date
   */
  public Page<BaseInjectExpectation> expectationsNotFill() {
    return this.injectExpectationRepository.findAll(
        (root, query, criteriaBuilder) ->
            criteriaBuilder.and(
                criteriaBuilder.isNull(root.get("score")),
                criteriaBuilder.or(
                    criteriaBuilder.equal(
                        criteriaBuilder.function(
                            "json_array_length", Integer.class, root.get("results")),
                        0),
                    criteriaBuilder.isNotNull(root.get("agent")))),
        PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "createdAt")));
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
      @NotNull BaseInjectExpectation.EXPECTATION_TYPE type,
      @NotNull Integer expirationTime,
      @NotBlank String sourceId) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository.findAgentExpectationsNotFilledForSourceCreatedAfter(
        type.name(), sourceId, expirationThreshold, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves expectations of a given type that have no results and are not expired.
   *
   * @param type the expectation type to filter by
   * @param expirationTime the expiration threshold in minutes
   * @return a list of matching inject expectations
   */
  public List<BaseInjectExpectation> expectationsNotFilledAndNotExpired(
      @NotNull BaseInjectExpectation.EXPECTATION_TYPE type, @NotNull Integer expirationTime) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository.findAgentExpectationsNotFilledCreatedAfter(
        type.name(), expirationThreshold, NOT_FILLED_FETCH_LIMIT);
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
                            Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves prevention expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of prevention expectations without results from the source
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilledForSource(
        PREVENTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves prevention expectations without any results.
   *
   * @return a list of prevention expectations without results
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFill() {
    return this.injectExpectationRepository.findAgentExpectationsNotFilled(
        PREVENTION.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves prevention expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired prevention expectations without results
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(PREVENTION, expirationTime);
  }

  /**
   * Retrieves prevention expectations without results from a specific source that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @param sourceId the source ID to check for existing results
   * @return a list of non-expired prevention expectations without results from the source
   */
  public List<BaseInjectExpectation> preventionExpectationsNotFilledAndNotExpired(
      @NotNull Integer expirationTime, @NotBlank String sourceId) {
    return expectationsNotFilledAndNotExpiredBySourceId(PREVENTION, expirationTime, sourceId);
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
                            Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves detection expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of detection expectations without results from the source
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository.findAgentExpectationsNotFilledForSource(
        DETECTION.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves detection expectations without any results.
   *
   * @return a list of detection expectations without results
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFill() {
    return this.injectExpectationRepository.findAgentExpectationsNotFilled(
        DETECTION.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves detection expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired detection expectations without results
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(DETECTION, expirationTime);
  }

  /**
   * Retrieves detection expectations without results from a specific source that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @param sourceId the source ID to check for existing results
   * @return a list of non-expired detection expectations without results from the source
   */
  public List<BaseInjectExpectation> detectionExpectationsNotFilledAndNotExpired(
      @NotNull Integer expirationTime, @NotBlank String sourceId) {

    return expectationsNotFilledAndNotExpiredBySourceId(DETECTION, expirationTime, sourceId);
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
                            Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  /**
   * Retrieves manual expectations without results from a specific source.
   *
   * @param sourceId the source ID to check for existing results
   * @return a list of manual expectations without results from the source
   */
  public List<BaseInjectExpectation> manualExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository.findExpectationsNotFilledForSource(
        MANUAL.name(), sourceId, NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves manual expectations without any results.
   *
   * @return a list of manual expectations without results
   */
  public List<BaseInjectExpectation> manualExpectationsNotFill() {
    return this.injectExpectationRepository.findExpectationsNotFilled(
        MANUAL.name(), NOT_FILLED_FETCH_LIMIT);
  }

  /**
   * Retrieves manual expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired manual expectations without results
   */
  public List<BaseInjectExpectation> manualExpectationsNotFillAndNotExpired(
      @NotNull Integer expirationTime) {
    return expectationsNotFilledAndNotExpired(MANUAL, expirationTime);
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
            case ASSETS -> injectExpectationRepository.findAllByInjectAndAsset(injectId, targetId);
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
  public List<BaseInjectExpectation> findMergedExpectationsByInjectAndTargetAndTargetType(
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
        case ASSETS -> injectExpectationRepository.findAllByInjectAndAsset(injectId, targetId);
        case ASSETS_GROUPS ->
            injectExpectationRepository.findAllByInjectAndAssetGroup(injectId, targetId);
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
   * Converts a list of inject expectations to agent output DTOs.
   *
   * @param injectExpectations the expectations to convert
   * @param assetId the asset ID to include in each output
   * @return a list of agent output DTOs
   */
  private static List<InjectExpectationAgentOutput> toInjectExpectationAgentsOutput(
      List<BaseInjectExpectation> injectExpectations, String assetId) {
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
    // Insert the signature for all agent and inject in one query
    injectExpectationRepository.insertSignature(signatureType, date.toString(), injectId, agentId);
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
      List<BaseInjectExpectation> expectations) {
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
    if (expectations == null || expectations.isEmpty()) {
      return;
    }

    final boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();
    final boolean isScheduledInject = !executableInject.isDirect();

    if (!isScheduledInject && !isAtomicTesting) {
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
                .map(BaseInjectExpectation::getTeam)
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
    } else if (!assets.isEmpty() || !assetGroups.isEmpty()) {
      injectExpectations =
          expectations.stream()
              .map(
                  expectation ->
                      expectationConverter(
                          executableInject, expectation, expectationPropertiesConfig))
              .collect(Collectors.toList());
    }

    if (!injectExpectations.isEmpty()) {
      setupDefaultExpectationResults(injectExpectations);
      injectExpectationRepository.saveAll(injectExpectations);
    }
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
   */
  private void setupDefaultExpectationResults(
      @NotNull final List<BaseInjectExpectation> injectExpectations) {
    List<Collector> collectors = collectorService.securityPlatformCollectors();

    injectExpectations.forEach(
        ie -> {
          switch (ie.getType()) {
            case PREVENTION, DETECTION -> {
              if (ie.getAgent() != null) {
                ie.setResults(setUpFromCollectors(collectors));
              }
            }
            case VULNERABILITY -> {
              if (ie.getAgent() != null) {
                ie.setResults(List.of(buildDefaultForVulnerabilityManagerInFailed()));
              }
            }
            case MANUAL -> {
              if (ie.getUser() != null) {
                ie.setResults(List.of(buildDefaultForPlayerManualValidation()));
              }
            }
            // TODO : The UI needs to be fixed: when the score and result are initialized to null,
            // the user can no longer validate the flag.
            // the user can not validate the flag anymore
            //                case CHALLENGE -> {
            //                  if (ie.getUser() != null) {
            //
            // ie.setResults(List.of(ChallengeExpectationUtils.buildDefaultChallengeInjectExpectationResult()));
            //                  }
            //                }
            case ARTICLE -> {
              if (ie.getUser() != null) {
                ie.setResults(List.of(buildDefaultForMediaPressure()));
              }
            }
            default -> {}
          }
        });
  }

  /**
   * Function used to check if the output contains vulnerabilities and update the related inject
   * expectations with the result.
   *
   * @param ctx the execution processing context containing the inject and agent information
   * @param jsonNode the JSON node containing the output to check for vulnerabilities
   */
  public void matchesVulnerabilityExpectations(ExecutionProcessingContext ctx, JsonNode jsonNode) {
    boolean vulnerable =
        jsonNode != null
            && !jsonNode.isMissingNode()
            && jsonNode.isContainerNode()
            && !jsonNode.isEmpty();

    Inject inject = ctx.inject();
    Agent agent = ctx.agent();

    List<BaseInjectExpectation> expectations = fetchVulnerabilityExpectations(inject, agent);

    if (expectations.isEmpty()) {
      return;
    }

    InjectExpectationResult result = buildForVulnerabilityManagerInFailed();

    String label = vulnerable ? VULNERABILITY.failureLabel : VULNERABILITY.successLabel;

    setResultExpectationVulnerable(expectations, result, label);

    validateResultForAsset(expectations, result);
    injectExpectationRepository.saveAll(expectations);
  }

  /**
   * Function used to fetch inject expectations of type VULNERABILITY for a given inject and agent.
   *
   * @param inject the inject for which to fetch the expectations
   * @param agent the agent for which to fetch the expectations
   * @return the list of inject expectations of type VULNERABILITY for the given inject and agent
   */
  private static List<BaseInjectExpectation> fetchVulnerabilityExpectations(
      Inject inject, Agent agent) {
    String agentId = agent != null ? agent.getId() : null;
    return inject.getExpectations().stream()
        .filter(exp -> BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY == exp.getType())
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
   * Function used to set the result of inject expectations of type VULNERABILITY with a label and a
   * score.
   *
   * @param injectExpectations the list of inject expectations to update
   * @param injectExpectationResult the result to set for the inject expectations
   */
  public void validateResultForAsset(
      List<BaseInjectExpectation> injectExpectations,
      InjectExpectationResult injectExpectationResult) {
    injectExpectations.forEach(
        BaseInjectExpectation ->
            updateInjectExpectation(
                BaseInjectExpectation.getId(),
                InjectExpectationUpdateInput.builder()
                    .collectorId(injectExpectationResult.getSourceId())
                    .result(injectExpectationResult.getResult())
                    .isSuccess(injectExpectationResult.getScore() != 0.0)
                    .build()));
  }
}
