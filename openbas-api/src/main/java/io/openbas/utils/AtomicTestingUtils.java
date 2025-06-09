package io.openbas.utils;

import io.openbas.database.model.*;
import io.openbas.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openbas.database.raw.*;
import io.openbas.expectation.ExpectationType;
import jakarta.validation.constraints.NotNull;
import java.util.*;

public class AtomicTestingUtils {

  public static final String ENDPOINT = "Endpoint";

  // -- RESULTS BY EXPECTATION TYPE --
  @NotNull
  public static List<ExpectationResultsByType> getExpectationResultByTypesFromRaw(
      List<RawInjectExpectation> expectations) {
    List<Double> preventionScores =
        getScoresFromRaw(List.of(EXPECTATION_TYPE.PREVENTION), expectations);
    List<Double> detectionScores =
        getScoresFromRaw(List.of(EXPECTATION_TYPE.DETECTION), expectations);
    List<Double> humanScores =
        getScoresFromRaw(
            List.of(EXPECTATION_TYPE.ARTICLE, EXPECTATION_TYPE.CHALLENGE, EXPECTATION_TYPE.MANUAL),
            expectations);

    List<ExpectationResultsByType> resultAvgOfExpectations = new ArrayList<>();

    getExpectationByType(ExpectationType.PREVENTION, preventionScores)
        .ifPresent(resultAvgOfExpectations::add);
    getExpectationByType(ExpectationType.DETECTION, detectionScores)
        .ifPresent(resultAvgOfExpectations::add);
    getExpectationByType(ExpectationType.HUMAN_RESPONSE, humanScores)
        .ifPresent(resultAvgOfExpectations::add);

    return resultAvgOfExpectations;
  }

  @NotNull
  public static List<ExpectationResultsByType> getExpectationResultByTypes(
      final List<InjectExpectation> expectations) {
    List<Double> preventionScores = getScores(List.of(EXPECTATION_TYPE.PREVENTION), expectations);
    List<Double> detectionScores = getScores(List.of(EXPECTATION_TYPE.DETECTION), expectations);
    List<Double> humanScores =
        getScores(
            List.of(EXPECTATION_TYPE.ARTICLE, EXPECTATION_TYPE.CHALLENGE, EXPECTATION_TYPE.MANUAL),
            expectations);

    List<ExpectationResultsByType> resultAvgOfExpectations = new ArrayList<>();

    getExpectationByType(ExpectationType.PREVENTION, preventionScores)
        .map(resultAvgOfExpectations::add);
    getExpectationByType(ExpectationType.DETECTION, detectionScores)
        .map(resultAvgOfExpectations::add);
    getExpectationByType(ExpectationType.HUMAN_RESPONSE, humanScores)
        .map(resultAvgOfExpectations::add);

    return resultAvgOfExpectations;
  }

  // -- NORMALIZED SCORES --
  public static List<Double> getScoresFromRaw(
      List<EXPECTATION_TYPE> types, List<RawInjectExpectation> expectations) {
    return expectations.stream()
        .filter(e -> types.contains(EXPECTATION_TYPE.valueOf(e.getInject_expectation_type())))
        .map(
            rawInjectExpectation -> {
              if (rawInjectExpectation.getInject_expectation_score() == null) {
                return null;
              }
              if (rawInjectExpectation.getTeam_id() != null) {
                if (rawInjectExpectation.getInject_expectation_score()
                    >= rawInjectExpectation.getInject_expectation_expected_score()) {
                  return 1.0;
                } else {
                  return 0.0;
                }
              } else {
                if (rawInjectExpectation.getInject_expectation_score()
                    >= rawInjectExpectation.getInject_expectation_expected_score()) {
                  return 1.0;
                }
                if (rawInjectExpectation.getInject_expectation_score() == 0) {
                  return 0.0;
                }
                return 0.5;
              }
            })
        .toList();
  }

  public static List<Double> getScores(
      final List<EXPECTATION_TYPE> types, final List<InjectExpectation> expectations) {
    return expectations.stream()
        .filter(e -> types.contains(e.getType()))
        .map(
            injectExpectation -> {
              if (injectExpectation.getScore() == null) {
                return null;
              }
              if (injectExpectation.getTeam() != null) {
                if (injectExpectation.getScore() >= injectExpectation.getExpectedScore()) {
                  return 1.0;
                } else {
                  return 0.0;
                }
              } else {
                if (injectExpectation.getScore() >= injectExpectation.getExpectedScore()) {
                  return 1.0;
                }
                if (injectExpectation.getScore() == 0) {
                  return 0.0;
                }
                return 0.5;
              }
            })
        .toList();
  }

  public static Optional<ExpectationResultsByType> getExpectationByType(
      final ExpectationType type, final List<Double> scores) {
    if (scores.isEmpty()) {
      return Optional.of(
          new ExpectationResultsByType(
              type, InjectExpectation.EXPECTATION_STATUS.UNKNOWN, Collections.emptyList()));
    }
    OptionalDouble avgResponse = calculateAverageFromExpectations(scores);
    if (avgResponse.isPresent()) {
      return Optional.of(
          new ExpectationResultsByType(
              type, getResult(avgResponse), getResultDetail(type, scores)));
    }
    return Optional.of(
        new ExpectationResultsByType(
            type, InjectExpectation.EXPECTATION_STATUS.PENDING, getResultDetail(type, scores)));
  }

  public static InjectExpectation.EXPECTATION_STATUS getResult(final OptionalDouble avg) {
    Double avgAsDouble = avg.getAsDouble();
    return avgAsDouble == 0.0
        ? InjectExpectation.EXPECTATION_STATUS.FAILED
        : (avgAsDouble == 1.0
            ? InjectExpectation.EXPECTATION_STATUS.SUCCESS
            : InjectExpectation.EXPECTATION_STATUS.PARTIAL);
  }

  public static OptionalDouble calculateAverageFromExpectations(final List<Double> scores) {
    return scores.stream()
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .average(); // Null values are expectations for injects in Pending
  }

  public static List<ResultDistribution> getResultDetail(
      final ExpectationType type, final List<Double> normalizedScores) {
    long successCount = normalizedScores.stream().filter(s -> s != null && s.equals(1.0)).count();
    long partialCount = normalizedScores.stream().filter(s -> s != null && s.equals(0.5)).count();
    long pendingCount = normalizedScores.stream().filter(Objects::isNull).count();
    long failureCount = normalizedScores.stream().filter(s -> s != null && s.equals(0.0)).count();

    return List.of(
        new ResultDistribution(ExpectationType.SUCCESS_ID, type.successLabel, (int) successCount),
        new ResultDistribution(ExpectationType.PENDING_ID, type.pendingLabel, (int) pendingCount),
        new ResultDistribution(ExpectationType.PARTIAL_ID, type.partialLabel, (int) partialCount),
        new ResultDistribution(ExpectationType.FAILED_ID, type.failureLabel, (int) failureCount));
  }

  // -- RECORDS --
  public record ExpectationResultsByType(
      @NotNull ExpectationType type,
      @NotNull InjectExpectation.EXPECTATION_STATUS avgResult,
      @NotNull List<ResultDistribution> distribution) {}

  public record ResultDistribution(
      @NotNull String id, @NotNull String label, @NotNull Integer value) {}
}
