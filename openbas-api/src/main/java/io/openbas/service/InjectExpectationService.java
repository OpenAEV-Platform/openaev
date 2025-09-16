package io.openbas.service;

import static io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.service.InjectExpectationUtils.*;
import static io.openbas.utils.AgentUtils.getPrimaryAgents;
import static io.openbas.utils.ExpectationUtils.*;
import static io.openbas.utils.inject_expectation_result.InjectExpectationResultUtils.*;
import static io.openbas.utils.inject_expectation_result.InjectExpectationResultUtils.computeScore;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.database.specification.InjectExpectationSpecification;
import io.openbas.execution.ExecutableInject;
import io.openbas.expectation.ExpectationPropertiesConfig;
import io.openbas.expectation.ExpectationType;
import io.openbas.model.Expectation;
import io.openbas.rest.collector.service.CollectorService;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exercise.form.ExpectationUpdateInput;
import io.openbas.rest.inject.form.InjectExpectationUpdateInput;
import io.openbas.utils.ExpectationUtils;
import io.openbas.utils.TargetType;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
  private final CollectorService collectorService;
  @Resource private ExpectationPropertiesConfig expectationPropertiesConfig;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;

  @Resource protected ObjectMapper mapper;

  // -- CRUD --

  public InjectExpectation findInjectExpectation(@NotBlank final String injectExpectationId) {
    return this.injectExpectationRepository
        .findById(injectExpectationId)
        .orElseThrow(ElementNotFoundException::new);
  }

  // -- UPDATE FROM UI --

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
        propagateTechnicalExpectation(injectExpectation, isAgentless);
        return injectExpectation;
        // Computation on agent or asset agentless
      } else {
        computeInjectExpectationForAgentOrAssetAgentless(injectExpectation, input);
        InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
        propagateTechnicalExpectation(updated, isAgentless);
        return updated;
      }
    }
    return injectExpectation;
  }

  // -- DELETE RESULT FROM UI --

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
      propagateTechnicalExpectation(updated, isAgentless);
    }

    return updated;
  }

  //  -- HUMAN RESPONSE --

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

  private List<InjectExpectation> propagateToTeam(
      @NotNull final InjectExpectation injectExpectation, @Nullable final String result) {
    List<InjectExpectation> expectationsForPlayers =
        getExpectationsPlayersForTeam(injectExpectation);
    List<InjectExpectation> expectationForTeams = getExpectationTeams(injectExpectation);
    computeScores(
        expectationsForPlayers,
        expectationForTeams,
        injectExpectation,
        (score) -> buildForPlayerManualValidation(result, score));
    return expectationForTeams;
  }

  // -- TECHNICAL --

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

  private void propagateTechnicalExpectation(
      @NotNull final InjectExpectation injectExpectation, final boolean isAgentless) {
    List<InjectExpectation> expectations = new ArrayList<>();
    // 1) Agent -> Asset
    if (!isAgentless) {
      expectations.addAll(propagateToAsset(injectExpectation));
    }

    // 2) Asset -> Asset Group
    expectations.addAll(propagateToAssetGroup(injectExpectation));
    this.injectExpectationRepository.saveAll(expectations);

    // Security coverage job creation
    List<Exercise> exercises = new ArrayList<>();
    exercises.add(injectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
  }

  private List<InjectExpectation> propagateToAsset(
      @NotNull final InjectExpectation injectExpectation) {
    List<InjectExpectation> expectationsForAgents =
        getExpectationsAgentsForAsset(injectExpectation);
    List<InjectExpectation> expectationsForAssets = getExpectationsAssets(injectExpectation);
    computeScores(expectationsForAgents, expectationsForAssets, injectExpectation, null);
    return expectationsForAssets;
  }

  private List<InjectExpectation> propagateToAssetGroup(
      @NotNull final InjectExpectation injectExpectation) {
    if (injectExpectation.getAssetGroup() != null) {
      List<InjectExpectation> expectationsForAssets =
          getExpectationsAssetsForAssetGroup(injectExpectation);
      List<InjectExpectation> expectationForAssetGroups =
          getExpectationAssetGroups(injectExpectation);
      computeScores(expectationsForAssets, expectationForAssetGroups, injectExpectation, null);
      return expectationForAssetGroups;
    }
    return new ArrayList<>();
  }

  // -- UPDATE FROM EXTERNAL SOURCE : COLLECTORS --

  public InjectExpectation updateInjectExpectation(
      @NotBlank String expectationId, @Valid @NotNull InjectExpectationUpdateInput input) {
    InjectExpectation injectExpectation = this.findInjectExpectation(expectationId);
    Collector collector = this.collectorService.collector(input.getCollectorId());

    computeTechnicalExpectation(injectExpectation, collector, input);

    return injectExpectation;
  }

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
      computeTechnicalExpectation(injectExpectation, collector, input);
    }
  }

  public void computeTechnicalExpectation(
      InjectExpectation injectExpectation,
      Collector collector,
      InjectExpectationUpdateInput input) {
    // Update inject expectation at agent level
    injectExpectation =
        this.computeInjectExpectationForAgentOrAssetAgentless(injectExpectation, input, collector);
    InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);
    propagateTechnicalExpectation(updated, false);
  }

  // -- COMPUTE RESULTS FROM INJECT EXPECTATIONS --

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

  public void updateAll(@NotNull List<InjectExpectation> injectExpectations) {
    this.injectExpectationRepository.saveAll(injectExpectations);
  }

  // -- FETCH INJECT EXPECTATIONS --

  public Page<InjectExpectation> expectationsNotFill() {
    return this.injectExpectationRepository.findAll(
        (root, query, criteriaBuilder) -> {
          if (query.getResultType() != Long.class) {
            root.fetch("agent", JoinType.LEFT);
          }
          return criteriaBuilder.and(
              criteriaBuilder.equal(
                  criteriaBuilder.function("json_array_length", Integer.class, root.get("results")),
                  Integer.valueOf(0)),
              criteriaBuilder.or(
                  criteriaBuilder.isNotNull(root.get("agent")),
                  criteriaBuilder.isNull(root.get("score"))));
        },
        PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "createdAt")));
  }

  // -- PREVENTION --

  public List<InjectExpectation> preventionExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.where(
            InjectExpectationSpecification.type(PREVENTION)
                .and(InjectExpectationSpecification.agentNotNull())
                .and(InjectExpectationSpecification.assetNotNull())
                .and(
                    InjectExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<InjectExpectation> preventionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<InjectExpectation> preventionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  // -- DETECTION --

  public List<InjectExpectation> detectionExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.where(
            InjectExpectationSpecification.type(DETECTION)
                .and(InjectExpectationSpecification.agentNotNull())
                .and(InjectExpectationSpecification.assetNotNull())
                .and(
                    InjectExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<InjectExpectation> detectionExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<InjectExpectation> detectionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  // -- MANUAL

  public List<InjectExpectation> manualExpectationsNotExpired(final Integer expirationTime) {
    return this.injectExpectationRepository.findAll(
        Specification.where(
            InjectExpectationSpecification.type(MANUAL)
                .and(InjectExpectationSpecification.agentNotNull())
                .and(InjectExpectationSpecification.assetNotNull())
                .and(
                    InjectExpectationSpecification.from(
                        Instant.now().minus(expirationTime, ChronoUnit.MINUTES)))));
  }

  public List<InjectExpectation> manualExpectationsNotFill(@NotBlank final String sourceId) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResult(e.getResults(), sourceId))
        .toList();
  }

  public List<InjectExpectation> manualExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> hasNoResults(e.getResults()))
        .toList();
  }

  // -- BY TARGET TYPE

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
   * Add a date signature to all inject expectations by agent.
   *
   * @param injectId the injectId for which to add the end date signature
   * @param agentId the agentId for which to add the end date signature
   * @param date the date to set as the signature value
   * @param signatureType the type of signature to add (start date or end date)
   */
  private void addDateSignatureToInjectExpectationsByAgent(
      @NotBlank final String injectId,
      @NotBlank final String agentId,
      @NotBlank final Instant date,
      @NotBlank final String signatureType) {
    List<InjectExpectation> injectExpectations =
        this.injectExpectationRepository.findAllByInjectAndAgent(injectId, agentId);

    injectExpectations.forEach(
        expectation -> {
          List<InjectExpectationSignature> signatures = expectation.getSignatures();
          signatures.add(new InjectExpectationSignature(signatureType, date.toString()));
        });

    injectExpectationRepository.saveAll(injectExpectations);
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
  public void addStartDateSignatureToInjectExpectationsByAgent(
      @NotBlank final String injectId,
      @NotBlank final String agentId,
      @NotBlank final Instant date) {
    addDateSignatureToInjectExpectationsByAgent(
        injectId, agentId, date, EXPECTATION_SIGNATURE_TYPE_START_DATE);
  }

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

  // -- BUILD AND SAVE INJECT EXPECTATION --

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
      final String exerciseId = executableInject.getInjection().getExercise().getId();

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

  private void setupExpectationResults(@NotNull final List<InjectExpectation> injectExpectations) {
    List<Collector> collectors = collectorService.securityPlatformCollectors();
    injectExpectations.stream()
        .filter(ie -> List.of(PREVENTION, DETECTION).contains(ie.getType()))
        .forEach(
            injectExpectation -> injectExpectation.setResults(setUpFromCollectors(collectors)));
  }
}
