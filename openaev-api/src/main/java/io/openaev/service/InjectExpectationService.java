package io.openaev.service;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.InjectExpectationUtils.computeScores;
import static io.openaev.service.InjectExpectationUtils.expectationConverter;
import static io.openaev.utils.AgentUtils.getPrimaryAgents;
import static io.openaev.utils.ExpectationUtils.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.helper.InjectExpectationRepositoryHelper;
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
import io.openaev.utils.ExpectationUtils;
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
  public static final String FAILED = "Failed";
  public static final String PENDING = "Pending";
  public static final String COLLECTOR = "collector";
  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectExpectationRepositoryHelper injectExpectationRepositoryHelper;
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
  public InjectExpectation findInjectExpectation(@NotBlank final String injectExpectationId) {
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
  public InjectExpectation updateInjectExpectation(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    InjectExpectation injectExpectation = this.findInjectExpectation(expectationId);

    if (HUMAN_EXPECTATION.contains(injectExpectation.getType())) {
      String result =
          ExpectationType.label(
              injectExpectation.getType(), injectExpectation.getExpectedScore(), input.getScore());
      computeInjectExpectationForHumanResponse(injectExpectation, input, result);
      InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
      propagateHumanResponseExpectation(updated, result);
      return updated;
    } else if (List.of(DETECTION, PREVENTION).contains(injectExpectation.getType())) {
      // Block down computation on asset group
      if (isAssetGroupExpectation(injectExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Allow down computation on asset
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(injectExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(injectExpectation) && !isAgentless) {
        List<InjectExpectation> expectationsForAgents =
            getExpectationsAgentsForAsset(injectExpectation);
        expectationsForAgents.forEach(
            e -> computeInjectExpectationForAgentOrAssetAgentless(e, input));
        this.injectExpectationRepository.saveAll(expectationsForAgents);
        propagateTechnicalExpectation(injectExpectation, isAgentless, null);
        return injectExpectation;
        // Computation on agent or asset agentless
      } else {
        computeInjectExpectationForAgentOrAssetAgentless(injectExpectation, input);
        InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
        propagateTechnicalExpectation(updated, isAgentless, null);
        return updated;
      }
    }
    return injectExpectation;
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
  public InjectExpectation deleteInjectExpectationResult(
      @NotBlank final String expectationId, @NotBlank final String sourceId) {
    InjectExpectation injectExpectation =
        this.injectExpectationRepository.findById(expectationId).orElseThrow();
    deleteResult(injectExpectation, sourceId);
    InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
    if (HUMAN_EXPECTATION.contains(injectExpectation.getType())) {
      propagateHumanResponseExpectation(updated, null);
    } else if (List.of(DETECTION, PREVENTION).contains(injectExpectation.getType())) {
      // Block down computation
      // Not asset group
      if (isAssetGroupExpectation(injectExpectation)) {
        throw new IllegalArgumentException("Not possible to update Asset Group directly");
      }
      // Not Endpoint if no agentless
      Endpoint endpoint = (Endpoint) Hibernate.unproxy(injectExpectation.getAsset());
      List<Agent> agents = getPrimaryAgents(endpoint);
      boolean isAgentless = agents.isEmpty();
      if (isAssetExpectation(injectExpectation) && !isAgentless) {
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
   * @param injectExpectation the expectation to compute
   * @param input the update input containing the score
   * @param result the result label
   */
  private void computeInjectExpectationForHumanResponse(
      @NotNull InjectExpectation injectExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotBlank final String result) {
    // Keep only one result
    injectExpectation.getResults().clear();
    addResult(injectExpectation, input, result);
    final Double score = computeScore(injectExpectation.getResults(), injectExpectation);
    injectExpectation.setScore(score);
  }

  /**
   * Computes an inject expectation for a human response from a collector.
   *
   * @param injectExpectation the expectation to compute
   * @param input the update input containing the response
   * @param collector the collector submitting the response
   * @return the updated inject expectation
   */
  public InjectExpectation computeInjectExpectationForHumanResponse(
      @NotNull InjectExpectation injectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    // Keep only one result
    injectExpectation.getResults().clear();
    addResult(injectExpectation, input, collector);
    final Double score = computeScore(injectExpectation.getResults(), injectExpectation);
    injectExpectation.setScore(score);
    return injectExpectation;
  }

  /**
   * Propagates a human response expectation update to related expectations.
   *
   * <p>If the expectation belongs to a player, propagates to the team. If the expectation belongs
   * to a team, propagates to all players.
   *
   * @param injectExpectation the updated expectation
   * @param result the result label to propagate
   */
  private void propagateHumanResponseExpectation(
      @NotNull InjectExpectation injectExpectation, @Nullable final String result) {
    // If the updated expectation was a player expectation, We have to update the team expectation
    // using player expectations (based on validation type)
    List<InjectExpectation> expectations = new ArrayList<>();
    if (injectExpectation.getUser() != null) {
      expectations.addAll(propagateToTeam(injectExpectation, result));
    } else {
      expectations.addAll(propagateToPlayers(injectExpectation, result));
    }
    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(injectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates a team expectation update to all player expectations.
   *
   * @param injectExpectation the team expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated player expectations
   */
  private List<InjectExpectation> propagateToPlayers(
      @NotNull final InjectExpectation injectExpectation, @Nullable final String result) {
    // If I update the expectation team: What happens with children? -> update expectation score
    // for all children -> set score from InjectExpectation
    List<InjectExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(injectExpectation);
    for (InjectExpectation expectationsForPlayer : expectationsForPlayers) {
      expectationsForPlayer.getResults().clear();
      if (result != null) {
        expectationsForPlayer
            .getResults()
            .add(buildForTeamManualValidation(result, injectExpectation.getScore()));
      }
      expectationsForPlayer.setScore(injectExpectation.getScore());
    }
    return expectationsForPlayers;
  }

  /**
   * Propagates a player expectation update to the team expectation.
   *
   * @param injectExpectation the player expectation that was updated
   * @param result the result label to propagate
   * @return the list of updated team expectations
   */
  private List<InjectExpectation> propagateToTeam(
      @NotNull final InjectExpectation injectExpectation, @Nullable final String result) {
    List<InjectExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(injectExpectation);
    List<InjectExpectation> expectationForTeams = getExpectationTeams(injectExpectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        injectExpectation,
        score -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }

  // -- TECHNICAL --

  /**
   * Computes a technical expectation for an agent or agentless asset
   *
   * @param injectExpectation the expectation to compute
   * @param input the update input containing the score
   */
  private void computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final ExpectationUpdateInput input) {
    String result =
        ExpectationType.label(
            injectExpectation.getType(), injectExpectation.getExpectedScore(), input.getScore());
    addResult(injectExpectation, input, result);
    final Double score = computeScore(injectExpectation.getResults(), injectExpectation);
    injectExpectation.setScore(score);
  }

  /**
   * Propagates a technical expectation update up the hierarchy (agent to asset to asset group).
   *
   * @param injectExpectation the expectation that was updated
   * @param isAgentless whether the asset has no agent
   * @param addResult optional function to create a result from a score
   */
  private void propagateTechnicalExpectation(
      @NotNull final InjectExpectation injectExpectation,
      final boolean isAgentless,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<InjectExpectation> expectations = new ArrayList<>();
    // 1) Agent -> Asset
    if (!isAgentless) {
      expectations.addAll(propagateToAsset(injectExpectation, addResult));
    }

    // 2) Asset -> Asset Group
    expectations.addAll(propagateToAssetGroup(injectExpectation, addResult));

    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(injectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  /**
   * Propagates an agent expectation update to the asset expectation.
   *
   * @param injectExpectation the agent expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset expectations
   */
  private List<InjectExpectation> propagateToAsset(
      @NotNull final InjectExpectation injectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    List<InjectExpectation> expectationsForAgents =
        getExpectationsAgentsForAsset(injectExpectation);
    List<InjectExpectation> expectationsForAssets = getExpectationsAssets(injectExpectation);
    computeScores(expectationsForAgents, expectationsForAssets, injectExpectation, addResult);
    return expectationsForAssets;
  }

  /**
   * Propagates an asset expectation update to the asset group expectation.
   *
   * @param injectExpectation the asset expectation that was updated
   * @param addResult optional function to create a result from a score
   * @return the list of updated asset group expectations, or empty list if no asset group
   */
  private List<InjectExpectation> propagateToAssetGroup(
      @NotNull final InjectExpectation injectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    if (injectExpectation.getAssetGroup() != null) {
      List<InjectExpectation> expectationsForAssets =
          getExpectationsAssetsForAssetGroup(injectExpectation);
      List<InjectExpectation> expectationForAssetGroups =
          getExpectationAssetGroups(injectExpectation);
      computeScores(expectationsForAssets, expectationForAssetGroups, injectExpectation, addResult);
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
  public InjectExpectation updateInjectExpectation(
      @NotBlank String expectationId, @Valid @NotNull InjectExpectationUpdateInput input) {
    InjectExpectation injectExpectation = this.findInjectExpectation(expectationId);
    Collector collector = this.collectorService.collector(input.getCollectorId());

    computeTechnicalExpectation(injectExpectation, collector, input, false);

    return injectExpectation;
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

    List<InjectExpectation> injectExpectations =
        fromIterable(this.injectExpectationRepository.findAllById(inputs.keySet()));
    Map<String, InjectExpectation> expectationsToUpdate =
        injectExpectations.stream().collect(Collectors.toMap(InjectExpectation::getId, e -> e));

    Collector collector =
        this.collectorService.collector(
            inputs.values().stream()
                .findFirst()
                .orElseThrow(ElementNotFoundException::new)
                .getCollectorId());

    // Update inject expectation at agent level
    for (Map.Entry<String, InjectExpectationUpdateInput> entry : inputs.entrySet()) {
      String injectExpectationId = entry.getKey();
      InjectExpectationUpdateInput input = entry.getValue();

      InjectExpectation injectExpectation = expectationsToUpdate.get(injectExpectationId);
      if (injectExpectation == null) {
        log.error("Inject expectation not found for ID: {}", injectExpectationId);
        continue;
      }
      computeTechnicalExpectation(injectExpectation, collector, input, false);
    }
  }

  /**
   * Computes a technical expectation (detection/prevention) from collector input.
   *
   * @param injectExpectation the expectation to compute
   * @param collector the collector submitting the result
   * @param input the update input
   * @param shouldPropagateLastInjectExpectationResult whether to propagate the last result
   */
  public void computeTechnicalExpectation(
      InjectExpectation injectExpectation,
      Collector collector,
      InjectExpectationUpdateInput input,
      boolean shouldPropagateLastInjectExpectationResult) {
    // Update inject expectation at agent level
    injectExpectation =
        this.computeInjectExpectationForAgentOrAssetAgentless(injectExpectation, input, collector);
    InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
    propagateTechnicalExpectation(
        updated,
        false,
        shouldPropagateLastInjectExpectationResult
            ? score -> updated.getResults().getLast()
            : null);
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
  public InjectExpectation computeInjectExpectationForAgentOrAssetAgentless(
      @NotNull final InjectExpectation expectation,
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
  public void updateAll(@NotNull List<InjectExpectation> injectExpectations) {
    this.injectExpectationRepository.saveAll(injectExpectations);
  }

  // -- FETCH INJECT EXPECTATIONS --

  /**
   * Retrieves a page of inject expectations that have not been filled (no score and no results or
   * has an agent).
   *
   * @return a page of unfilled inject expectations ordered by creation date
   */
  public Page<InjectExpectation> expectationsNotFill() {
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
  public List<InjectExpectation> expectationsNotFilledAndNotExpiredBySourceId(
      @NotNull InjectExpectation.EXPECTATION_TYPE type,
      @NotNull Integer expirationTime,
      @NotBlank String sourceId) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository
        .findAll(
            Specification.where(
                InjectExpectationSpecification.type(type)
                    .and(InjectExpectationSpecification.agentNotNull())
                    .and(InjectExpectationSpecification.assetNotNull())
                    .and(InjectExpectationSpecification.from(expirationThreshold))))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  /**
   * Retrieves expectations of a given type that have no results and are not expired.
   *
   * @param type the expectation type to filter by
   * @param expirationTime the expiration threshold in minutes
   * @return a list of matching inject expectations
   */
  public List<InjectExpectation> expectationsNotFilledAndNotExpired(
      @NotNull InjectExpectation.EXPECTATION_TYPE type, @NotNull Integer expirationTime) {

    Instant expirationThreshold = Instant.now().minus(expirationTime, ChronoUnit.MINUTES);

    return injectExpectationRepository
        .findAll(
            Specification.where(
                InjectExpectationSpecification.type(type)
                    .and(InjectExpectationSpecification.agentNotNull())
                    .and(InjectExpectationSpecification.assetNotNull())
                    .and(InjectExpectationSpecification.from(expirationThreshold))))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  // -- PREVENTION --

  /**
   * Retrieves prevention expectations that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired prevention expectations
   */
  public List<InjectExpectation> preventionExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<InjectExpectation>unrestricted()
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
  public List<InjectExpectation> preventionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  /**
   * Retrieves prevention expectations without any results.
   *
   * @return a list of prevention expectations without results
   */
  public List<InjectExpectation> preventionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  /**
   * Retrieves prevention expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired prevention expectations without results
   */
  public List<InjectExpectation> preventionExpectationsNotFillAndNotExpired(
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
  public List<InjectExpectation> preventionExpectationsNotFilledAndNotExpired(
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
  public List<InjectExpectation> detectionExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<InjectExpectation>unrestricted()
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
  public List<InjectExpectation> detectionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  /**
   * Retrieves detection expectations without any results.
   *
   * @return a list of detection expectations without results
   */
  public List<InjectExpectation> detectionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  /**
   * Retrieves detection expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired detection expectations without results
   */
  public List<InjectExpectation> detectionExpectationsNotFillAndNotExpired(
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
  public List<InjectExpectation> detectionExpectationsNotFilledAndNotExpired(
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
  public List<InjectExpectation> manualExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.<InjectExpectation>unrestricted()
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
  public List<InjectExpectation> manualExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  /**
   * Retrieves manual expectations without any results.
   *
   * @return a list of manual expectations without results
   */
  public List<InjectExpectation> manualExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(
            Specification.<InjectExpectation>unrestricted()
                .and(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  /**
   * Retrieves manual expectations without results that have not expired.
   *
   * @param expirationTime the expiration threshold in minutes
   * @return a list of non-expired manual expectations without results
   */
  public List<InjectExpectation> manualExpectationsNotFillAndNotExpired(
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
  public List<InjectExpectation> findMergedExpectationsByInjectAndTargetAndTargetType(
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
  public List<InjectExpectation> findMergedExpectationsByInjectAndTargetAndTargetType(
      @NotBlank final String injectId,
      @NotBlank final String targetId,
      @NotBlank final String parentTargetId,
      @NotBlank final String targetType) {
    try {
      TargetType targetTypeEnum = TargetType.valueOf(targetType);
      return switch (targetTypeEnum) {
        case TEAMS -> injectExpectationRepository.findAllByInjectAndTeam(injectId, targetId);
        case PLAYERS ->
            injectExpectationRepository.findAllByInjectAndTeamAndPlayer(
                injectId, parentTargetId, targetId);
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
      List<InjectExpectation> injectExpectations, String assetId) {
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
                injectId, assetId, InjectExpectation.EXPECTATION_TYPE.valueOf(expectationType)),
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
  private List<InjectExpectation> mergeExpectationResultsByExpectationType(
      List<InjectExpectation> expectations) {
    List<String> notCopiedSourceTypes = List.of(COLLECTOR);

    HashMap<InjectExpectation.EXPECTATION_TYPE, InjectExpectation> electedExpectations =
        new HashMap<>();
    for (InjectExpectation expectation : expectations) {
      if (!electedExpectations.containsKey(expectation.getType())) {
        electedExpectations.put(expectation.getType(), expectation);
        continue;
      }

      for (InjectExpectationResult expectationResult : expectation.getResults()) {
        if (!notCopiedSourceTypes.contains(expectationResult.getSourceType())) {
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
                  Collections.max(
                      electedExpectations.get(expectation.getType()).getResults().stream()
                          .map(InjectExpectationResult::getScore)
                          .toList()));
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

    List<InjectExpectation> injectExpectations = new ArrayList<>();
    if (!teams.isEmpty()) {
      List<InjectExpectation> injectExpectationsByUserAndTeam;
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
                .map(InjectExpectation::getTeam)
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
      setupExpectationResults(injectExpectations);
      injectExpectationRepository.saveAll(injectExpectations);
    }
  }

  /**
   * Sets up initial results for detection and prevention expectations from security platform
   * collectors.
   *
   * @param injectExpectations the list of expectations to set up
   */
  private void setupExpectationResults(@NotNull final List<InjectExpectation> injectExpectations) {
    List<Collector> collectors = collectorService.securityPlatformCollectors();
    injectExpectations.stream()
        .filter(ie -> List.of(PREVENTION, DETECTION).contains(ie.getType()))
        .forEach(
            injectExpectation -> injectExpectation.setResults(setUpFromCollectors(collectors)));
  }
}
