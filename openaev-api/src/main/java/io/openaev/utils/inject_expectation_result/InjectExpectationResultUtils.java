package io.openaev.utils.inject_expectation_result;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.*;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.Collector;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class InjectExpectationResultUtils {

  private static final String NOT_APPLICABLE = null;

  private InjectExpectationResultUtils() {}

  // -- SCORE --

  /**
   * Evaluate overall status from per-source results.
   *
   * <p>* SUCCESS if any expected source reports expectedScore
   *
   * <p>* NO_DATA if no success and at least one expected source is missing
   *
   * <p>* ERROR if no success and all expected sources reported but none matched
   */
  public static Double computeScore(
      @NotNull final List<InjectExpectationResult> results,
      @NotNull final InjectExpectation expectation) {
    final Double expectedScore = expectation.getExpectedScore();
    if (expectedScore == null) {
      return null;
    }
    if (hasNoResults(results) || hasAnyEmptyResult(results)) {
      return null;
    }

    return Collections.max(results.stream().map(InjectExpectationResult::getScore).toList());
  }

  // -- SETUP --

  private static InjectExpectationResult setUp(
      @NotNull final String sourceId,
      @NotNull final String sourceName,
      @NotNull final String sourcePlatform) {
    return InjectExpectationResult.builder()
        .sourceId(sourceId)
        .sourceType(COLLECTOR)
        .sourceName(sourceName)
        .sourcePlatform(sourcePlatform)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static List<InjectExpectationResult> setUpFromCollectors(
      @NotNull final List<Collector> collectors) {
    return collectors.stream()
        .map(
            c ->
                setUp(
                    c.getId(),
                    c.getName(),
                    Optional.ofNullable(c.getSecurityPlatform())
                        .map(sp -> sp.getSecurityPlatformType().name())
                        .orElse(null)))
        .toList();
  }

  // -- BUILD --

  public static void addResult(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotNull final String resultMsg) {
    InjectExpectationResult existing =
        findResultBySourceId(injectExpectation.getResults(), input.getSourceId());
    if (existing != null) {
      existing.setResult(resultMsg);
      existing.setScore(input.getScore());
    } else {
      existing =
          InjectExpectationResult.builder()
              .sourceId(input.getSourceId())
              .sourceType(input.getSourceType())
              .sourceName(input.getSourceName())
              .sourcePlatform(input.getSourcePlatform())
              .result(resultMsg)
              .date(now().toString())
              .score(input.getScore())
              .build();
      injectExpectation.getResults().add(existing);
    }
  }

  public static void addResult(
      @NotNull final InjectExpectation injectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    final double score =
        InjectExpectationUtils.computeScore(injectExpectation, input.getIsSuccess());

    InjectExpectationResult existing =
        findResultBySourceId(injectExpectation.getResults(), collector.getId());

    if (existing != null) {
      existing.setResult(input.getResult());
      existing.setScore(score);
      existing.setMetadata(input.getMetadata());
    } else {
      existing =
          InjectExpectationResult.builder()
              .sourceId(collector.getId())
              .sourceType(COLLECTOR)
              .sourceName(collector.getName())
              .sourcePlatform(
                  Optional.ofNullable(collector.getSecurityPlatform())
                      .map(sp -> sp.getSecurityPlatformType().name())
                      .orElse(null))
              .result(input.getResult())
              .date(Instant.now().toString())
              .score(score)
              .metadata(input.getMetadata())
              .build();
      injectExpectation.getResults().add(existing);
    }
  }

  public static void deleteResult(
      @NotNull final InjectExpectation expectation, @NotBlank final String sourceId) {
    expectation.setResults(
        expectation.getResults().stream().filter(r -> !sourceId.equals(r.getSourceId())).toList());

    final Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
  }

  public static InjectExpectationResult buildForMediaPressure(
      @NotNull final InjectExpectation injectExpectation) {
    return InjectExpectationResult.builder()
        .sourceId("media-pressure")
        .sourceType("media-pressure")
        .sourceName("Media pressure read")
        .sourcePlatform(NOT_APPLICABLE)
        .result(Instant.now().toString())
        .date(Instant.now().toString())
        .score(injectExpectation.getExpectedScore())
        .build();
  }

  public static InjectExpectationResult buildForVulnerabilityManager() {
    return InjectExpectationResult.builder()
        .sourceId(EXPECTATIONS_VULNERABILITY_COLLECTOR_ID)
        .sourceType(EXPECTATIONS_VULNERABILITY_COLLECTOR_TYPE)
        .sourceName(EXPECTATIONS_VULNERABILITY_COLLECTOR_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .score(0.0)
        .result(VULNERABILITY.failureLabel)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static InjectExpectationResult buildForPlayerManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return InjectExpectationResult.builder()
        .sourceId("player-manual-validation")
        .sourceType("player-manual-validation")
        .sourceName("Player Manual Validation")
        .sourcePlatform(NOT_APPLICABLE)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static InjectExpectationResult buildForTeamManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return InjectExpectationResult.builder()
        .sourceId("team-manual-validation")
        .sourceType("team-manual-validation")
        .sourceName("Team Manual Validation")
        .sourcePlatform(NOT_APPLICABLE)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  // -- CLOSE --

  public static void expireEmptyResults(
      @NotNull final List<InjectExpectationResult> results,
      final Double score,
      final String result) {
    results.stream()
        .filter(r -> !hasText(r.getResult()))
        .forEach(
            r -> {
              r.setScore(score);
              r.setResult(result);
            });
  }

  // -- GETTER --

  public static InjectExpectationResult findResultBySourceId(
      @NotNull final List<InjectExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream().filter(r -> sourceId.equals(r.getSourceId())).findFirst().orElse(null);
  }

  // -- RESULT --

  public static boolean hasNoResult(
      @NotNull final List<InjectExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream()
        .noneMatch(
            r -> {
              if (sourceId.equals(r.getSourceId())) {
                return hasText(r.getResult());
              }
              return false;
            });
  }

  public static boolean hasNoResults(@NotNull final List<InjectExpectationResult> results) {
    return results.isEmpty() || results.stream().noneMatch(r -> hasText(r.getResult()));
  }

  public static boolean hasAnyEmptyResult(@NotNull List<InjectExpectationResult> results) {
    return results.isEmpty() || results.stream().anyMatch(r -> !hasText(r.getResult()));
  }

  public static boolean hasValidResults(@NotNull final List<InjectExpectationResult> results) {
    return !results.isEmpty() && results.stream().allMatch(r -> hasText(r.getResult()));
  }

  public static boolean hasValidResultFromSource(
      @NotNull final List<InjectExpectationResult> results, @NotBlank final String sourceId) {
    return results.stream()
        .anyMatch(r -> sourceId.equals(r.getSourceId()) && hasText(r.getResult()));
  }
}
