package io.openbas.service;

import static io.openbas.collectors.expectations_expiration_manager.utils.ExpectationUtils.computeFailedMessage;
import static io.openbas.collectors.expectations_expiration_manager.utils.ExpectationUtils.computeSuccessMessage;
import static io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.service.InjectExpectationUtils.*;
import static io.openbas.utils.ExpectationUtils.*;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.*;
import io.openbas.database.repository.CollectorRepository;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.specification.InjectExpectationSpecification;
import io.openbas.execution.ExecutableInject;
import io.openbas.model.Expectation;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exercise.form.ExpectationUpdateInput;
import io.openbas.rest.inject.form.InjectExpectationUpdateInput;
import io.openbas.utils.TargetType;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
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
  private final InjectRepository injectRepository;
  private final AssetGroupService assetGroupService;
  private final EndpointService endpointService;
  private final CollectorRepository collectorRepository;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;

  @Resource protected ObjectMapper mapper;

  // -- CRUD --

  public Optional<InjectExpectation> findInjectExpectation(
      @NotBlank final String injectExpectationId) {
    return this.injectExpectationRepository.findById(injectExpectationId);
  }

  // -- UPDATE FROM UI --

  public InjectExpectation updateInjectExpectation(
      @NotBlank final String expectationId, @NotNull final ExpectationUpdateInput input) {
    InjectExpectation injectExpectation =
        this.injectExpectationRepository.findById(expectationId).orElseThrow();
    Optional<InjectExpectationResult> exists =
        injectExpectation.getResults().stream()
            .filter(r -> input.getSourceId().equals(r.getSourceId()))
            .findAny();

    String result;
    if (HUMAN_EXPECTATION.contains(injectExpectation.getType())) {
      result = input.getScore() >= injectExpectation.getExpectedScore() ? SUCCESS : FAILED;
      injectExpectation.getResults().clear();
      exists = Optional.empty();
    } else if (DETECTION.equals(injectExpectation.getType())) {
      if (input.getScore() >= injectExpectation.getExpectedScore()) {
        result = "Detected";
      } else if (input.getScore() > 0) {
        result = "Partially Detected";
      } else {
        result = "Not Detected";
      }
    } else if (PREVENTION.equals(injectExpectation.getType())) {
      if (input.getScore() >= injectExpectation.getExpectedScore()) {
        result = "Prevented";
      } else if (input.getScore() > 0) {
        result = "Partially Prevented";
      } else {
        result = "Not Prevented";
      }
    } else {
      result = "";
    }
    if (exists.isPresent()) {
      exists.get().setResult(result);
      exists.get().setScore(input.getScore());
      exists.get().setDate(now().toString());
    } else {
      injectExpectation
          .getResults()
          .add(
              buildInjectExpectationResult(
                  input.getSourceId(),
                  input.getSourceType(),
                  input.getSourceName(),
                  result,
                  input.getScore()));
    }
    if (injectExpectation.getScore() == null) {
      injectExpectation.setScore(input.getScore());
    } else {
      if (input.getScore() > injectExpectation.getScore()
          || HUMAN_EXPECTATION.contains(injectExpectation.getType())) {
        injectExpectation.setScore(input.getScore());
      } else {
        injectExpectation.setScore(
            Collections.max(
                injectExpectation.getResults().stream()
                    .map(InjectExpectationResult::getScore)
                    .filter(Objects::nonNull)
                    .toList()));
      }
    }
    injectExpectation.setUpdatedAt(now());
    InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);

    boolean isAssetGroupExpectation = updated.getAssetGroup() != null && updated.getAsset() == null;
    boolean isAssetExpectation = updated.getAsset() != null && updated.getAgent() == null;

    if (isAssetGroupExpectation) {
      // Update InjectExpectations for Assets linked to this asset group
      updateInjectExpectationAsset(input, updated, result);
    } else if (isAssetExpectation) {
      // Update InjectExpectations for Agents linked to this asset
      updateInjectExpectationAgent(input, updated, result);
    }

    // If the expectation is type manual, We should update expectations for teams and players
    if (HUMAN_EXPECTATION.contains(injectExpectation.getType()) && updated.getTeam() != null) {
      computeExpectationsForTeamsAndPlayer(updated, result);
    }

    List<Exercise> exercises = new ArrayList<>();
    exercises.add(updated.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);

    return updated;
  }

  private void updateInjectExpectation(
      ExpectationUpdateInput input, InjectExpectation updated, String result, boolean isAsset) {
    List<InjectExpectation> expectations =
        isAsset
            ? this.expectationsForAssets(
                updated.getInject(), updated.getAssetGroup(), updated.getType())
            : this.expectationsForAgents(
                updated.getInject(),
                updated.getAsset(),
                updated.getAssetGroup(),
                updated.getType());

    expectations.forEach(
        expectation -> {
          expectation
              .getResults()
              .add(
                  buildInjectExpectationResult(
                      input.getSourceId(),
                      input.getSourceType(),
                      input.getSourceName(),
                      result,
                      input.getScore()));
          expectation.setScore(updated.getScore());
          expectation.setUpdatedAt(updated.getUpdatedAt());
          if (isAsset) {
            updateInjectExpectationAgent(input, expectation, result);
          }
        });

    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
        expectations.stream().map(exp -> exp.getInject().getExercise()).toList());

    injectExpectationRepository.saveAll(expectations);
  }

  private void updateInjectExpectationAsset(
      ExpectationUpdateInput input, InjectExpectation updated, String result) {
    updateInjectExpectation(input, updated, result, true);
  }

  private void updateInjectExpectationAgent(
      ExpectationUpdateInput input, InjectExpectation updated, String result) {
    updateInjectExpectation(input, updated, result, false);
  }

  // -- DELETE RESULT FROM UI --

  public InjectExpectation deleteInjectExpectationResult(
      @NotBlank final String expectationId, @NotBlank final String sourceId) {
    InjectExpectation injectExpectation =
        this.injectExpectationRepository.findById(expectationId).orElseThrow();
    Optional<InjectExpectationResult> exists =
        injectExpectation.getResults().stream()
            .filter(r -> sourceId.equals(r.getSourceId()))
            .findAny();
    if (exists.isPresent()) {
      injectExpectation.setResults(
          injectExpectation.getResults().stream()
              .filter(r -> !sourceId.equals(r.getSourceId()))
              .toList());
      if (List.of(MANUAL, ARTICLE, CHALLENGE).contains(injectExpectation.getType())) {
        injectExpectation.setScore(null);
      } else {
        List<Double> scores =
            injectExpectation.getResults().stream()
                .map(InjectExpectationResult::getScore)
                .filter(Objects::nonNull)
                .toList();
        injectExpectation.setScore(!scores.isEmpty() ? Collections.max(scores) : 0.0);
      }
    }
    injectExpectation.setUpdatedAt(now());
    InjectExpectation updated = this.injectExpectationRepository.save(injectExpectation);

    boolean isAssetGroupExpectation = updated.getAssetGroup() != null && updated.getAsset() == null;
    boolean isAssetExpectation = updated.getAsset() != null && updated.getAgent() == null;

    if (isAssetGroupExpectation) {
      // Delete result InjectExpectations for Assets linked to this asset group
      deleteInjectExpectationResultAsset(sourceId, updated);
    } else if (isAssetExpectation) {
      // Delete InjectExpectations results for Agents installed on this asset
      deleteInjectExpectationResultAgent(sourceId, updated);
    }

    // If The expectation is type manual, We should update expectations for teams and players
    if (List.of(MANUAL, ARTICLE, CHALLENGE).contains(injectExpectation.getType())
        && updated.getTeam() != null) {
      computeExpectationsForTeamsAndPlayer(updated, null);
    }

    List<Exercise> exercises = new ArrayList<>();
    exercises.add(updated.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);

    return updated;
  }

  private void deleteInjectExpectationResult(
      String sourceId, InjectExpectation updated, boolean isAsset) {
    List<InjectExpectation> expectations =
        isAsset
            ? this.expectationsForAssets(
                updated.getInject(), updated.getAssetGroup(), updated.getType())
            : this.expectationsForAgents(
                updated.getInject(),
                updated.getAsset(),
                updated.getAssetGroup(),
                updated.getType());

    expectations.forEach(
        expectation -> {
          expectation.setResults(
              expectation.getResults().stream()
                  .filter(r -> !sourceId.equals(r.getSourceId()))
                  .toList());
          expectation.setScore(updated.getScore());
          expectation.setUpdatedAt(updated.getUpdatedAt());
          if (isAsset) {
            deleteInjectExpectationResultAgent(sourceId, expectation);
          }
        });

    injectExpectationRepository.saveAll(expectations);
  }

  private void deleteInjectExpectationResultAsset(String sourceId, InjectExpectation updated) {
    deleteInjectExpectationResult(sourceId, updated, true);
  }

  private void deleteInjectExpectationResultAgent(String sourceId, InjectExpectation updated) {
    deleteInjectExpectationResult(sourceId, updated, false);
  }

  // -- COMMUN --

  private void computeExpectationsForTeamsAndPlayer(InjectExpectation updated, String result) {
    // If the updated expectation was a player expectation, We have to update the team expectation
    // using player expectations (based on validation type)
    if (updated.getUser() != null) {
      List<InjectExpectation> toProcess =
          injectExpectationRepository.findAllByInjectAndTeamAndExpectationName(
              updated.getInject().getId(), updated.getTeam().getId(), updated.getName());
      InjectExpectation parentExpectation =
          toProcess.stream()
              .filter(exp -> exp.getUser() == null)
              .findFirst()
              .orElseThrow(ElementNotFoundException::new);
      List<InjectExpectation> playersExpectations =
          toProcess.stream().filter(exp -> exp.getUser() != null).toList();
      List<InjectExpectation> playersAnsweredExpectations =
          playersExpectations.stream().filter(exp -> exp.getScore() != null).toList();

      if (updated.isExpectationGroup()) { // If At least one player
        List<InjectExpectation> successPlayerExpectations =
            playersExpectations.stream()
                .filter(exp -> exp.getScore() != null)
                .filter(exp -> exp.getScore() >= updated.getExpectedScore())
                .toList();

        if (!successPlayerExpectations.isEmpty()) { // At least one player success
          result = SUCCESS;
          OptionalDouble avgSuccessPlayer =
              successPlayerExpectations.stream().mapToDouble(InjectExpectation::getScore).average();
          parentExpectation.setScore(avgSuccessPlayer.getAsDouble());
        } else if (playersAnsweredExpectations.size()
            == playersExpectations.size()) { // All players had answers and no one success
          result = FAILED;
          parentExpectation.setScore(0.0);
        } else {
          result = PENDING;
          parentExpectation.setScore(null);
        }

      } else { // All Player
        boolean hasFailedPlayer =
            playersExpectations.stream()
                .filter(exp -> exp.getScore() != null)
                .anyMatch(exp -> exp.getScore() < updated.getExpectedScore());

        if (hasFailedPlayer) {
          result = FAILED;
        } else if (playersAnsweredExpectations.size()
            == playersExpectations.size()) { // All players answered and no failures
          result = SUCCESS;
        } else { // Some players haven't answered yet
          result = PENDING;
          parentExpectation.setScore(null);
        }

        if (!result.equals(PENDING)) {
          OptionalDouble avgAllPlayer =
              playersAnsweredExpectations.stream()
                  .mapToDouble(InjectExpectation::getScore)
                  .average();
          parentExpectation.setScore(avgAllPlayer.orElse(0.0));
        }
      }

      parentExpectation.setUpdatedAt(Instant.now());
      parentExpectation.getResults().clear();
      parentExpectation
          .getResults()
          .add(
              buildInjectExpectationResult(
                  "player-manual-validation",
                  "player-manual-validation",
                  "Player Manual Validation",
                  result,
                  parentExpectation.getScore()));
      injectExpectationRepository.save(parentExpectation);

    } else {
      // If I update the expectation team: What happens with children? -> update expectation score
      // for all children -> set score from InjectExpectation
      List<InjectExpectation> toProcess =
          injectExpectationRepository.findAllByInjectAndTeamAndExpectationNameAndUserIsNotNull(
              updated.getInject().getId(), updated.getTeam().getId(), updated.getName());
      for (InjectExpectation expectation : toProcess) {
        expectation.setScore(updated.getScore());
        expectation.setUpdatedAt(Instant.now());
        expectation.getResults().clear();
        if (result != null) {
          expectation
              .getResults()
              .add(
                  buildInjectExpectationResult(
                      "team-manual-validation",
                      "team-manual-validation",
                      "Team Manual Validation",
                      result,
                      updated.getScore()));
        }
        injectExpectationRepository.save(expectation);
      }
    }
  }

  private InjectExpectationResult buildInjectExpectationResult(
      String id, String type, String name, String resultStatus, Double score) {
    return InjectExpectationResult.builder()
        .sourceId(id)
        .sourceType(type)
        .sourceName(name)
        .result(resultStatus)
        .date(now().toString())
        .score(score)
        .build();
  }

  // -- UPDATE FROM EXTERNAL SOURCE : COLLECTORS --

  public InjectExpectation updateInjectExpectation(
      @NotBlank String expectationId, @Valid @NotNull InjectExpectationUpdateInput input) {
    InjectExpectation injectExpectation =
        this.findInjectExpectation(expectationId).orElseThrow(ElementNotFoundException::new);
    Collector collector =
        this.collectorRepository
            .findById(input.getCollectorId())
            .orElseThrow(ElementNotFoundException::new);

    // Update inject expectation at agent level
    injectExpectation =
        this.computeExpectation(
            injectExpectation,
            collector.getId(),
            COLLECTOR,
            collector.getName(),
            input.getResult(),
            input.getIsSuccess(),
            input.getMetadata());

    Inject inject = injectExpectation.getInject();
    // Compute potential expectations for asset
    propagateUpdateToAssets(injectExpectation, inject, collector);
    // Compute potential expectations for asset groups
    propagateUpdateToAssetGroups(inject, collector);

    // end of computing

    List<Exercise> exercises = new ArrayList<>();
    exercises.add(injectExpectation.getInject().getExercise());
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);

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
        this.collectorRepository
            .findById(
                inputs.values().stream()
                    .findFirst()
                    .orElseThrow(ElementNotFoundException::new)
                    .getCollectorId())
            .orElseThrow(ElementNotFoundException::new);

    // Update inject expectation at agent level
    for (Map.Entry<String, InjectExpectationUpdateInput> entry : inputs.entrySet()) {
      String injectExpectationId = entry.getKey();
      InjectExpectationUpdateInput input = entry.getValue();

      InjectExpectation injectExpectation = expectationsToUpdate.get(injectExpectationId);
      if (injectExpectation == null) {
        log.error("Inject expectation not found for ID: {}", injectExpectationId);
        continue;
      }

      injectExpectation =
          this.computeExpectation(
              injectExpectation,
              collector.getId(),
              COLLECTOR,
              collector.getName(),
              input.getResult(),
              input.getIsSuccess(),
              input.getMetadata());

      Inject inject = injectExpectation.getInject();
      // Compute potential expectations for asset
      propagateUpdateToAssets(injectExpectation, inject, collector);
      // Compute potential expectations for asset groups
      propagateUpdateToAssetGroups(inject, collector);
      // end of computing
    }
    securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
        injectExpectations.stream().map(exp -> exp.getInject().getExercise()).toList());
  }

  private void propagateUpdateToAssets(
      InjectExpectation injectExpectation, Inject inject, Collector collector) {
    InjectExpectation finalInjectExpectation = injectExpectation;

    List<InjectExpectation> expectationAssets =
        inject.getExpectations().stream()
            .filter(e -> e.getAsset() != null)
            .filter(e -> e.getAgent() == null)
            .filter(e -> e.getAsset().getId().equals(finalInjectExpectation.getAsset().getId()))
            .filter(
                e ->
                    (finalInjectExpectation.getAssetGroup() != null)
                        ? (e.getAssetGroup() != null
                            && e.getAssetGroup()
                                .getId()
                                .equals(finalInjectExpectation.getAssetGroup().getId()))
                        : e.getAssetGroup() == null)
            .filter(e -> e.getType().equals(finalInjectExpectation.getType()))
            .toList();

    expectationAssets.forEach(
        (expectationAsset -> {
          List<InjectExpectation> expectationAgents =
              this.expectationsForAgents(
                  expectationAsset.getInject(),
                  expectationAsset.getAsset(),
                  expectationAsset.getAssetGroup(),
                  expectationAsset.getType());
          // Every agent expectation (result by collector id) is filled
          if (expectationAgents.stream()
              .noneMatch(
                  e ->
                      e.getResults().stream()
                          .filter(result -> result.getSourceId().equals(collector.getId()))
                          .toList()
                          .isEmpty())) {
            // Update Asset inject expectation with new result and score
            this.computeExpectationAsset(
                expectationAsset,
                expectationAgents,
                collector.getId(),
                COLLECTOR,
                collector.getName());
          }
        }));
  }

  private void propagateUpdateToAssetGroups(Inject inject, Collector collector) {
    List<InjectExpectation> expectationAssetGroups =
        inject.getExpectations().stream().filter(e -> isAssetGroupExpectation(e)).toList();

    expectationAssetGroups.forEach(
        (expectationAssetGroup -> {
          List<InjectExpectation> expectationAssetsByAssetGroup =
              this.expectationsForAssets(
                  expectationAssetGroup.getInject(),
                  expectationAssetGroup.getAssetGroup(),
                  expectationAssetGroup.getType());
          // Every asset expectation is filled
          if (expectationAssetsByAssetGroup.stream()
              .noneMatch(
                  e ->
                      e.getResults().stream()
                          .filter(result -> result.getSourceId().equals(collector.getId()))
                          .toList()
                          .isEmpty())) {
            this.computeExpectationGroup(
                expectationAssetGroup,
                expectationAssetsByAssetGroup,
                collector.getId(),
                COLLECTOR,
                collector.getName());
          }
        }));
  }

  // -- COMPUTE RESULTS FROM INJECT EXPECTATIONS --

  public InjectExpectation computeExpectation(
      @NotNull final InjectExpectation expectation,
      @NotBlank final String sourceId,
      @NotBlank final String sourceType,
      @NotBlank final String sourceName,
      @NotBlank final String result,
      @NotBlank final Boolean success,
      final Map<String, String> metadata) {
    double actualScore =
        success
            ? expectation.getExpectedScore()
            : expectation.getScore() == null ? 0.0 : expectation.getScore();
    computeResult(
        expectation,
        sourceId,
        sourceType,
        sourceName,
        result,
        success ? expectation.getExpectedScore() : 0.0,
        metadata);
    expectation.setScore(actualScore);
    return this.update(expectation);
  }

  public void computeExpectationAsset(
      InjectExpectation expectationAsset,
      List<InjectExpectation> expectationAgents,
      String sourceId,
      String sourceType,
      String sourceName) {
    processExpectation(
        expectationAsset, expectationAgents, sourceId, sourceType, sourceName, false);
  }

  public void computeExpectationGroup(
      @NotNull final InjectExpectation expectationAssetGroup,
      @NotNull final List<InjectExpectation> expectationAssets,
      @NotBlank final String sourceId,
      @NotBlank final String sourceType,
      @NotBlank final String sourceName) {
    processExpectation(
        expectationAssetGroup,
        expectationAssets,
        sourceId,
        sourceType,
        sourceName,
        expectationAssetGroup.isExpectationGroup());
  }

  private boolean isSuccess(List<InjectExpectation> expectations, boolean isGroup) {
    if (expectations.isEmpty()) {
      return false;
    }
    return isGroup
        ? expectations.stream().anyMatch(e -> e.getExpectedScore().equals(e.getScore()))
        : expectations.stream().allMatch(e -> e.getExpectedScore().equals(e.getScore()));
  }

  private boolean isSuccessScoreResult(
      List<InjectExpectation> expectations, String sourceId, double expectedScore) {
    return !expectations.isEmpty()
        && expectations.stream()
            .flatMap(exp -> exp.getResults().stream().filter(r -> r.getSourceId().equals(sourceId)))
            .allMatch(r -> r.getScore().equals(expectedScore));
  }

  private void processExpectation(
      InjectExpectation expectation,
      List<InjectExpectation> expectations,
      String sourceId,
      String sourceType,
      String sourceName,
      boolean isGroup) {

    boolean success = isSuccess(expectations, isGroup);
    boolean successScoreResult =
        isSuccessScoreResult(expectations, sourceId, expectation.getExpectedScore());
    double finalScore = success ? expectation.getExpectedScore() : 0.0;

    computeResult(
        expectation,
        sourceId,
        sourceType,
        sourceName,
        successScoreResult
            ? computeSuccessMessage(expectation.getType())
            : computeFailedMessage(expectation.getType()),
        successScoreResult ? finalScore : 0.0,
        null);
    expectation.setScore(finalScore);
    this.update(expectation);
  }

  // -- FINAL UPDATE --

  private InjectExpectation update(@NotNull InjectExpectation injectExpectation) {
    injectExpectation.setUpdatedAt(now());
    Inject inject = injectExpectation.getInject();
    inject.setUpdatedAt(now());
    this.injectRepository.save(inject);
    return this.injectExpectationRepository.save(injectExpectation);
  }

  // -- FETCH INJECT EXPECTATIONS --

  public List<InjectExpectation> expectationsNotFill() {
    return fromIterable(this.injectExpectationRepository.findAll()).stream()
        .filter(e -> e.getResults().stream().toList().isEmpty())
        .toList();
  }

  public List<InjectExpectation> expectationsForAgents(
      @NotNull final Inject inject,
      @NotNull final Asset asset,
      @Nullable final AssetGroup assetGroup,
      @NotNull final InjectExpectation.EXPECTATION_TYPE expectationType) {

    Endpoint resolvedEndpoint = endpointService.endpoint(asset.getId());
    List<String> agentIds =
        resolvedEndpoint.getAgents().stream().map(Agent::getId).distinct().toList();

    Specification<InjectExpectation> spec =
        Specification.where(InjectExpectationSpecification.type(expectationType))
            .and(
                assetGroup != null
                    ? InjectExpectationSpecification.fromAssetGroup(assetGroup.getId())
                    : InjectExpectationSpecification.assetGroupIsNull())
            .and(InjectExpectationSpecification.fromAgents(inject.getId(), agentIds));

    return this.injectExpectationRepository.findAll(spec);
  }

  public List<InjectExpectation> expectationsForAssets(
      @NotNull final Inject inject,
      @NotNull final AssetGroup assetGroup,
      @NotNull final InjectExpectation.EXPECTATION_TYPE expectationType) {
    AssetGroup resolvedAssetGroup = assetGroupService.assetGroup(assetGroup.getId());
    List<String> assetIds =
        Stream.concat(
                resolvedAssetGroup.getAssets().stream(),
                resolvedAssetGroup.getDynamicAssets().stream())
            .map(Asset::getId)
            .distinct()
            .toList();
    return this.injectExpectationRepository.findAll(
        Specification.where(InjectExpectationSpecification.type(expectationType))
            .and(InjectExpectationSpecification.fromAssetGroup(assetGroup.getId()))
            .and(InjectExpectationSpecification.fromAssets(inject.getId(), assetIds)));
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

  public List<InjectExpectation> preventionExpectationsNotFill(@NotBlank final String source) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(e -> e.getAsset() != null && e.getAgent() != null)
        .filter(e -> e.getResults().stream().noneMatch(r -> source.equals(r.getSourceId())))
        .toList();
  }

  public List<InjectExpectation> preventionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(PREVENTION)))
        .stream()
        .filter(e -> e.getAsset() != null && e.getAgent() != null)
        .filter(e -> e.getResults().stream().toList().isEmpty())
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

  public List<InjectExpectation> detectionExpectationsNotFill(@NotBlank final String source) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(e -> e.getAsset() != null && e.getAgent() != null)
        .filter(e -> e.getResults().stream().noneMatch(r -> source.equals(r.getSourceId())))
        .toList();
  }

  public List<InjectExpectation> detectionExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(DETECTION)))
        .stream()
        .filter(e -> e.getAsset() != null && e.getAgent() != null)
        .filter(e -> e.getResults().stream().toList().isEmpty())
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

  public List<InjectExpectation> manualExpectationsNotFill(@NotBlank final String source) {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> e.getResults().stream().noneMatch(r -> source.equals(r.getSourceId())))
        .toList();
  }

  public List<InjectExpectation> manualExpectationsNotFill() {
    return this.injectExpectationRepository
        .findAll(Specification.where(InjectExpectationSpecification.type(MANUAL)))
        .stream()
        .filter(e -> e.getResults().stream().toList().isEmpty())
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
        case AGENT ->
            injectExpectationRepository.findAllByInjectAndAssetGroupAndAgent(
                injectId, parentTargetId, targetId);
        case ASSETS ->
            injectExpectationRepository.findAllByInjectAndAssetGroupAndAsset(
                injectId, parentTargetId, targetId);
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
    List<String> notCopiedSourceTypes = List.of("collector");

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
    boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();
    boolean isScheduledInject = !executableInject.isDirect();
    // Create the expectations
    List<Team> teams = executableInject.getTeams();
    List<Asset> assets = executableInject.getAssets();
    List<AssetGroup> assetGroups = executableInject.getAssetGroups();

    if ((isScheduledInject || isAtomicTesting) && !expectations.isEmpty()) {
      if (!teams.isEmpty()) {
        List<InjectExpectation> injectExpectationsByTeam;

        List<InjectExpectation> injectExpectationsByUserAndTeam;
        // If atomicTesting, We create expectation for every player and every team
        if (isAtomicTesting) {
          injectExpectationsByTeam =
              teams.stream()
                  .flatMap(
                      team ->
                          expectations.stream()
                              .map(
                                  expectation ->
                                      expectationConverter(team, executableInject, expectation)))
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
                                                      team, user, executableInject, expectation))))
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
                                      exerciseTeamUser
                                          .getExercise()
                                          .getId()
                                          .equals(
                                              executableInject
                                                  .getInjection()
                                                  .getExercise()
                                                  .getId()))
                              .flatMap(
                                  exerciseTeamUser ->
                                      expectations.stream()
                                          .map(
                                              expectation ->
                                                  expectationConverter(
                                                      team,
                                                      exerciseTeamUser.getUser(),
                                                      executableInject,
                                                      expectation))))
                  .toList();

          // Create a set of teams that have at least one enabled player
          Set<Team> teamsWithEnabledPlayers =
              injectExpectationsByUserAndTeam.stream()
                  .map(InjectExpectation::getTeam)
                  .collect(Collectors.toSet());

          // Add only the expectations where the team has at least one enabled player
          injectExpectationsByTeam =
              teamsWithEnabledPlayers.stream()
                  .flatMap(
                      team ->
                          expectations.stream()
                              .map(
                                  expectation ->
                                      expectationConverter(team, executableInject, expectation)))
                  .collect(Collectors.toList());
        }
        injectExpectationsByTeam.addAll(injectExpectationsByUserAndTeam);
        injectExpectationRepository.saveAll(injectExpectationsByTeam);
      } else if (!assets.isEmpty() || !assetGroups.isEmpty()) {
        List<InjectExpectation> injectExpectations =
            expectations.stream()
                .map(expectation -> expectationConverter(executableInject, expectation))
                .collect(Collectors.toList());
        injectExpectationRepository.saveAll(injectExpectations);
      }
    }
  }
}
