package io.openaev.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.expectation.ExpectationType;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Utility class for computing and aggregating inject expectation results.
 *
 * <p>Provides methods for calculating scores, aggregating results by expectation type, and
 * determining success/failure status of expectations. This class is central to the scoring and
 * evaluation system for simulations and atomic testing.
 *
 * <p>Score normalization follows these rules:
 *
 * <ul>
 *   <li>1.0 = Success (score meets or exceeds expected score)
 *   <li>0.5 = Partial success (non-zero score below expected, for non-team expectations)
 *   <li>0.0 = Failure (zero score or below expected for team expectations)
 *   <li>null = Pending (expectation not yet evaluated)
 * </ul>
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.InjectExpectation
 * @see io.openaev.expectation.ExpectationType
 */
public class InjectExpectationResultUtils {

  private InjectExpectationResultUtils() {}

  /**
   * Computes expectation results grouped by type from a list of expectations.
   *
   * <p>Uses the provided score extraction function to calculate normalized scores for each
   * expectation type category (Prevention, Detection, Vulnerability, Human Response).
   *
   * @param <T> the type of expectation objects (InjectExpectation or RawInjectExpectation)
   * @param expectations the list of expectations to process
   * @param getScores function to extract normalized scores for given expectation types
   * @return a list of expectation results grouped by type with aggregated scores
   */
  public static <T> List<ExpectationResultsByType> getExpectationResultByTypes(
      List<T> expectations, BiFunction<List<EXPECTATION_TYPE>, List<T>, List<Double>> getScores) {
    return computeExpectationResults(expectations, getScores);
  }

  private static <T> List<ExpectationResultsByType> computeExpectationResults(
      List<T> expectations,
      BiFunction<List<EXPECTATION_TYPE>, List<T>, List<Double>> scoreExtractor) {

    List<ExpectationResultsByType> result = new ArrayList<>();

    addIfScoresPresent(
        result,
        List.of(EXPECTATION_TYPE.PREVENTION),
        ExpectationType.PREVENTION,
        expectations,
        scoreExtractor);
    addIfScoresPresent(
        result,
        List.of(EXPECTATION_TYPE.DETECTION),
        ExpectationType.DETECTION,
        expectations,
        scoreExtractor);
    addIfScoresPresent(
        result,
        List.of(EXPECTATION_TYPE.VULNERABILITY),
        ExpectationType.VULNERABILITY,
        expectations,
        scoreExtractor);
    addIfScoresPresent(
        result,
        List.of(EXPECTATION_TYPE.ARTICLE, EXPECTATION_TYPE.CHALLENGE, EXPECTATION_TYPE.MANUAL),
        ExpectationType.HUMAN_RESPONSE,
        expectations,
        scoreExtractor);

    return result;
  }

  private static <T> void addIfScoresPresent(
      List<ExpectationResultsByType> resultList,
      List<EXPECTATION_TYPE> types,
      ExpectationType resultType,
      List<T> expectations,
      BiFunction<List<EXPECTATION_TYPE>, List<T>, List<Double>> scoreExtractor) {

    List<Double> scores = scoreExtractor.apply(types, expectations);
    if (!scores.isEmpty()) {
      getExpectationByType(resultType, scores).ifPresent(resultList::add);
    }
  }

  // -- NORMALIZED SCORES --

  /**
   * Extracts and normalizes scores from raw expectation data.
   *
   * <p>Filters expectations by the specified types and converts their scores to normalized values
   * (0.0, 0.5, 1.0, or null). Team expectations use binary scoring (0.0 or 1.0), while non-team
   * expectations can have partial scores (0.5).
   *
   * @param types the expectation types to filter by
   * @param expectations the raw expectation data from database queries
   * @return a list of normalized score values
   */
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

  /**
   * Extracts and normalizes scores from inject expectations.
   *
   * <p>Filters expectations by the specified types and converts their scores to normalized values.
   * The normalization rules differ based on whether the expectation is team-based:
   *
   * <ul>
   *   <li>Team expectations: 1.0 if score >= expected, otherwise 0.0
   *   <li>Non-team expectations: 1.0 if score >= expected, 0.5 if partial, 0.0 if zero
   * </ul>
   *
   * @param types the expectation types to filter by
   * @param expectations the inject expectations to process
   * @return a list of normalized score values (null for pending expectations)
   */
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

  /**
   * Creates an expectation result for a specific type from normalized scores.
   *
   * <p>Calculates the aggregate status and distribution of results for the given expectation type.
   * Returns UNKNOWN status if the scores list is empty, PENDING if all scores are null, otherwise
   * calculates SUCCESS, PARTIAL, or FAILED based on the average.
   *
   * @param type the expectation type category
   * @param scores the list of normalized scores (may contain nulls for pending expectations)
   * @return an Optional containing the computed result, always present
   */
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

  /**
   * Calculates the average of non-null normalized scores.
   *
   * <p>Filters out null values (representing pending expectations) before computing the average.
   * Returns an empty OptionalDouble if all scores are null.
   *
   * @param scores the list of normalized scores (may contain nulls)
   * @return the average of non-null scores, or empty if no valid scores exist
   */
  public static OptionalDouble calculateAverageFromExpectations(final List<Double> scores) {
    return scores.stream()
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .average(); // Null values are expectations for injects in Pending
  }

  /**
   * Generates detailed distribution of results by outcome category.
   *
   * <p>Counts expectations in each outcome category (success, partial, pending, failure) and
   * returns labeled distribution data suitable for visualization.
   *
   * @param type the expectation type (used for category-specific labels)
   * @param normalizedScores the list of normalized scores to analyze
   * @return a list of result distributions with counts for each outcome category
   */
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

  /**
   * Record representing aggregated expectation results for a specific type.
   *
   * @param type the expectation type category
   * @param avgResult the overall status based on average scores
   * @param distribution the detailed breakdown of results by outcome
   */
  public record ExpectationResultsByType(
      @NotNull ExpectationType type,
      @NotNull InjectExpectation.EXPECTATION_STATUS avgResult,
      @NotNull List<ResultDistribution> distribution) {
    @JsonIgnore
    public double getSuccessRate() {
      if (distribution.isEmpty()) {
        return 0;
      }

      double numberExpectations = 0;
      for (ResultDistribution dist : distribution) {
        numberExpectations += dist.value();
      }

      if (numberExpectations == 0) {
        return 0;
      }

      double numberSuccess =
          distribution.stream()
              .filter(d -> Objects.equals(d.id, ExpectationType.SUCCESS_ID))
              .findFirst()
              .map(ResultDistribution::value)
              .orElse(0);

      return numberSuccess / numberExpectations;
    }
  }

  /**
   * Record representing the distribution of a single result outcome.
   *
   * @param id the unique identifier for this outcome category
   * @param label the display label for this outcome
   * @param value the count of expectations in this category
   */
  public record ResultDistribution(
      @NotNull String id, @NotNull String label, @NotNull Integer value) {}
}
