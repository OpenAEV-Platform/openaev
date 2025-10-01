package io.openaev.rest.scenario.service;

import io.openaev.database.raw.RawFinishedExerciseWithInjects;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.scenario.response.GlobalScoreBySimulationEndDate;
import io.openaev.rest.scenario.response.ScenarioStatistic;
import io.openaev.rest.scenario.response.SimulationsResultsLatest;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.InjectExpectationResultUtils.ResultDistribution;
import io.openaev.utils.ResultUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenarioStatisticService {

  private final ExerciseRepository exerciseRepository;

  private final ResultUtils resultUtils;

  private static final int GLOBAL_SCORE_PERCENTAGE_NUMBER_OF_DECIMALS = 1;
  private static final int PERCENTAGE_MULTIPLIER = 100;

  public ScenarioStatistic getStatistics(String scenarioId) {
    return new ScenarioStatistic(getSimulationsResultsLatest(scenarioId));
  }

  private SimulationsResultsLatest getSimulationsResultsLatest(String scenarioId) {
    List<FinishedExerciseWithInjects> orderedFinishedExercises =
        getOrderedFinishedExercises(scenarioId);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> globalScoresByExpectationTypes =
        getGlobalScoresByExpectationTypes(orderedFinishedExercises);

    return new SimulationsResultsLatest(globalScoresByExpectationTypes);
  }

  private Map<ExpectationType, List<GlobalScoreBySimulationEndDate>>
      getGlobalScoresByExpectationTypes(List<FinishedExerciseWithInjects> finishedExercises) {

    List<ExpectationTypeAndGlobalScore> allGlobalScores = getAllGlobalScores(finishedExercises);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> result = new HashMap<>();

    for (ExpectationType type : ExpectationType.values()) {
      List<GlobalScoreBySimulationEndDate> scores =
          getGlobalScoresForExpectationType(allGlobalScores, type);
      if (!scores.isEmpty()) {
        result.put(type, scores);
      }
    }

    return result;
  }

  private List<ExpectationTypeAndGlobalScore> getAllGlobalScores(
      List<FinishedExerciseWithInjects> finishedExercises) {
    return finishedExercises.stream().flatMap(this::getExpectationTypeAndGlobalScores).toList();
  }

  private Stream<ExpectationTypeAndGlobalScore> getExpectationTypeAndGlobalScores(
      FinishedExerciseWithInjects finishedExercise) {
    return resultUtils.computeGlobalExpectationResults(finishedExercise.injectIds()).stream()
        .map(
            expectationResultByType ->
                getExpectationTypeAndGlobalScore(finishedExercise, expectationResultByType));
  }

  private static ExpectationTypeAndGlobalScore getExpectationTypeAndGlobalScore(
      FinishedExerciseWithInjects finishedExercise,
      ExpectationResultsByType expectationResultByType) {
    return new ExpectationTypeAndGlobalScore(
        expectationResultByType.type(),
        new GlobalScoreBySimulationEndDate(
            finishedExercise.endDate(), getPercentageOfInjectsOnSuccess(expectationResultByType)));
  }

  private static List<GlobalScoreBySimulationEndDate> getGlobalScoresForExpectationType(
      List<ExpectationTypeAndGlobalScore> allGlobalScores, ExpectationType expectationType) {
    return allGlobalScores.stream()
        .filter(typeAndScore -> typeAndScore.expectationType == expectationType)
        .map(typeAndScore -> typeAndScore.globalScoreBySimulationEndDate)
        .toList();
  }

  private List<FinishedExerciseWithInjects> getOrderedFinishedExercises(String scenarioId) {
    List<RawFinishedExerciseWithInjects> rawFinishedExercises =
        exerciseRepository.rawLatestFinishedExercisesWithInjectsByScenarioId(scenarioId);
    return rawFinishedExercises.stream()
        .map(
            exercise ->
                new FinishedExerciseWithInjects(
                    exercise.getExercise_end_date(), exercise.getInject_ids()))
        .sorted(Collections.reverseOrder())
        .toList();
  }

  private record ExpectationTypeAndGlobalScore(
      ExpectationType expectationType,
      GlobalScoreBySimulationEndDate globalScoreBySimulationEndDate) {}

  private static float getPercentageOfInjectsOnSuccess(
      ExpectationResultsByType expectationResultByType) {
    if (expectationResultByType.distribution().isEmpty()) {
      return 0;
    }
    return getRoundedPercentage(expectationResultByType);
  }

  public static float getRoundedPercentage(ExpectationResultsByType expectationResultByType) {
    float percentage =
        ((float) getNumberOfInjectsOnSuccess(expectationResultByType)
                / getTotalNumberOfInjects(expectationResultByType))
            * PERCENTAGE_MULTIPLIER;
    return truncatePercentageDecimals(percentage);
  }

  private static float truncatePercentageDecimals(float percentage) {
    return new BigDecimal(percentage)
        .setScale(GLOBAL_SCORE_PERCENTAGE_NUMBER_OF_DECIMALS, RoundingMode.UP)
        .floatValue();
  }

  private static int getNumberOfInjectsOnSuccess(ExpectationResultsByType expectationResultByType) {
    return expectationResultByType.distribution().getFirst().value();
  }

  private static int getTotalNumberOfInjects(ExpectationResultsByType expectationResultByType) {
    return expectationResultByType.distribution().stream()
        .map(ResultDistribution::value)
        .reduce(0, Integer::sum);
  }

  private record FinishedExerciseWithInjects(Instant endDate, Set<String> injectIds)
      implements Comparable<FinishedExerciseWithInjects> {
    @Override
    public int compareTo(FinishedExerciseWithInjects exercise) {
      if (this.endDate.isBefore(exercise.endDate)) return 1;
      if (this.endDate.isAfter(exercise.endDate)) return -1;
      return 0;
    }
  }
}
