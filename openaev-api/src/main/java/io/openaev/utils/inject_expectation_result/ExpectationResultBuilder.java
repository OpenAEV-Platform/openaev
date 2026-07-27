package io.openaev.utils.inject_expectation_result;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.*;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static io.openaev.service.InjectExpectationService.SECURITY_PLATFORM;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.rest.inject.form.InjectExpectationUpdateInput;
import io.openaev.service.InjectExpectationUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for building and managing InjectExpectationResult objects.
 *
 * <p>This class provides methods for:
 *
 * <ul>
 *   <li>Building result objects for different sources (media pressure, manual validation, etc.)
 *   <li>Computing scores from result collections
 *   <li>Managing result lifecycle (add, delete, expire)
 *   <li>Checking result status conditions
 * </ul>
 *
 * <p>Note: For aggregating expectation results by types and computing averages, see {@link
 * io.openaev.utils.InjectExpectationResultUtils}.
 */
public final class ExpectationResultBuilder {

  private ExpectationResultBuilder() {}

  // Source type and ID constants
  public static final String MEDIA_PRESSURE_SOURCE_ID = "media-pressure";
  public static final String MEDIA_PRESSURE_SOURCE_TYPE = "media-pressure";
  public static final String MEDIA_PRESSURE_SOURCE_NAME = "Media pressure read";

  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_ID = "player-manual-validation";
  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_TYPE = "player-manual-validation";
  public static final String PLAYER_MANUAL_VALIDATION_SOURCE_NAME = "Player Manual Validation";

  public static final String TEAM_MANUAL_VALIDATION_SOURCE_ID = "team-manual-validation";
  public static final String TEAM_MANUAL_VALIDATION_SOURCE_TYPE = "team-manual-validation";
  public static final String TEAM_MANUAL_VALIDATION_SOURCE_NAME = "Team Manual Validation";

  private static final String NOT_APPLICABLE = null;
  private static final String NO_RESULT = null;
  private static final Double NO_SCORE = null;
  private static final String NO_ASSET = null;

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
      @NotNull final BaseInjectExpectation expectation) {
    final Double expectedScore = expectation.getExpectedScore();
    if (expectedScore == null) {
      return null;
    }
    if (hasNoResults(results) || hasAnyEmptyResult(results)) {
      return null;
    }

    return maxScore(results);
  }

  /**
   * Evaluate overall status from per-source results.
   *
   * <p>* SUCCESS if any expected source reports expectedScore
   *
   * <p>* NO_DATA if no success and at least one expected source is missing
   *
   * <p>* ERROR if no success and all expected sources reported but none matched
   */
  public static Double computeResultsScore(@NotNull final List<InjectExpectationResult> results) {
    if (hasNoResults(results) || hasAnyEmptyResult(results)) {
      return null;
    }

    return maxScore(results);
  }

  /**
   * Highest non-null score across results, or {@code null} when none carries a score. A result may
   * legitimately have text but no score (e.g. an expired or NO_DATA result), so the null scores are
   * skipped instead of feeding {@link Collections#max} a null that would NPE.
   */
  private static Double maxScore(@NotNull final List<InjectExpectationResult> results) {
    List<Double> scores =
        results.stream()
            .map(InjectExpectationResult::getScore)
            .filter(java.util.Objects::nonNull)
            .toList();
    return scores.isEmpty() ? null : Collections.max(scores);
  }

  /**
   * Ensures the expectation's results list is a mutable {@link java.util.ArrayList}. JSON
   * deserialization can produce an immutable list and a persisted row can have a null column,
   * either of which breaks a subsequent {@code add} (the null case NPEs inside the copy
   * constructor).
   */
  private static void ensureMutableResults(@NotNull final BaseInjectExpectation expectation) {
    List<InjectExpectationResult> current = expectation.getResults();
    if (current == null) {
      expectation.setResults(new java.util.ArrayList<>());
    } else if (!(current instanceof java.util.ArrayList)) {
      expectation.setResults(new java.util.ArrayList<>(current));
    }
  }

  // -- SETUP --

  private static InjectExpectationResult setUp(
      @NotNull final String sourceId,
      @NotNull final String sourceName,
      @NotNull final String sourcePlatform,
      @NotNull final String sourceAssetId) {
    return InjectExpectationResult.builder()
        .sourceId(sourceId)
        .sourceType(COLLECTOR)
        .sourceName(sourceName)
        .sourcePlatform(sourcePlatform)
        .sourceAssetId(sourceAssetId)
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
                        .orElse(null),
                    getSourceAssetId(c)))
        .toList();
  }

  // -- BUILD --

  public static void addResult(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExpectationUpdateInput input,
      @NotNull final String resultMsg) {
    ensureMutableResults(baseInjectExpectation);

    InjectExpectationResult existing =
        findResultBySourceId(baseInjectExpectation.getResults(), input.getSourceId());
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
              .sourceAssetId(input.getSourceId())
              .result(resultMsg)
              .date(now().toString())
              .score(input.getScore())
              .build();
      baseInjectExpectation.getResults().add(existing);
    }
  }

  public static void addResult(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final Collector collector) {
    final double score =
        InjectExpectationUtils.computeScore(baseInjectExpectation, input.getIsSuccess());

    ensureMutableResults(baseInjectExpectation);

    InjectExpectationResult existing =
        findResultBySourceId(baseInjectExpectation.getResults(), collector.getId());

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
              .sourceAssetId(getSourceAssetId(collector))
              .result(input.getResult())
              .date(Instant.now().toString())
              .score(score)
              .metadata(input.getMetadata())
              .build();
      baseInjectExpectation.getResults().add(existing);
    }
  }

  /**
   * Adds (or updates) the result written by a security platform source, mirroring {@link
   * #addResult(BaseInjectExpectation, InjectExpectationUpdateInput, Collector)} for verdicts that
   * are attributed to a platform entry instead of a collector (e.g. assessment injectors).
   */
  public static void addResult(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final InjectExpectationUpdateInput input,
      @NotNull final SecurityPlatform securityPlatform) {
    final double score =
        InjectExpectationUtils.computeScore(baseInjectExpectation, input.getIsSuccess());

    ensureMutableResults(baseInjectExpectation);

    InjectExpectationResult existing =
        findResultBySourceId(baseInjectExpectation.getResults(), securityPlatform.getId());

    if (existing != null) {
      existing.setResult(input.getResult());
      existing.setScore(score);
      existing.setMetadata(input.getMetadata());
    } else {
      existing =
          InjectExpectationResult.builder()
              .sourceId(securityPlatform.getId())
              .sourceType(SECURITY_PLATFORM)
              .sourceName(securityPlatform.getName())
              .sourcePlatform(
                  securityPlatform.getSecurityPlatformType() != null
                      ? securityPlatform.getSecurityPlatformType().name()
                      : null)
              .sourceAssetId(securityPlatform.getId())
              .result(input.getResult())
              .date(Instant.now().toString())
              .score(score)
              .metadata(input.getMetadata())
              .build();
      baseInjectExpectation.getResults().add(existing);
    }
  }

  public static void deleteResult(
      @NotNull final BaseInjectExpectation expectation, @NotBlank final String sourceId) {
    expectation.setResults(
        expectation.getResults().stream().filter(r -> !sourceId.equals(r.getSourceId())).toList());

    final Double score = computeScore(expectation.getResults(), expectation);
    expectation.setScore(score);
  }

  private static InjectExpectationResult buildForMediaPressure(
      @Nullable final String result, @Nullable final Double score) {
    return InjectExpectationResult.builder()
        .sourceId(MEDIA_PRESSURE_SOURCE_ID)
        .sourceType(MEDIA_PRESSURE_SOURCE_TYPE)
        .sourceName(MEDIA_PRESSURE_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .date(Instant.now().toString())
        .score(score)
        .build();
  }

  private static String getSourceAssetId(Collector collector) {
    return collector.isExternal() && collector.getSecurityPlatform() != null
        ? collector.getSecurityPlatform().getId()
        : null;
  }

  public static InjectExpectationResult buildForMediaPressure(
      @NotNull final BaseInjectExpectation baseInjectExpectation) {
    return buildForMediaPressure(
        Instant.now().toString(), baseInjectExpectation.getExpectedScore());
  }

  public static InjectExpectationResult buildDefaultForMediaPressure() {
    return buildForMediaPressure(NO_RESULT, NO_SCORE);
  }

  public static InjectExpectationResult buildForVulnerabilityManager(
      @Nullable final String result, @Nullable final Double score) {
    return InjectExpectationResult.builder()
        .sourceId(EXPECTATIONS_VULNERABILITY_COLLECTOR_ID)
        .sourceType(EXPECTATIONS_VULNERABILITY_COLLECTOR_TYPE)
        .sourceName(EXPECTATIONS_VULNERABILITY_COLLECTOR_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .score(score)
        .result(result)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static InjectExpectationResult buildForVulnerabilityManagerInFailed() {
    return buildForVulnerabilityManager(VULNERABILITY.failureLabel, 0.0);
  }

  public static InjectExpectationResult buildDefaultForVulnerabilityManagerInFailed() {
    return buildForVulnerabilityManager(NO_RESULT, NO_SCORE);
  }

  /**
   * Builds a result attributed to a real security platform (e.g. the platform entry registered by
   * an assessment injector such as Nuclei), so the verdict renders as a per-platform row - logo,
   * type chip and pivot - exactly like detection/prevention platform rows.
   */
  public static InjectExpectationResult buildForSecurityPlatform(
      @NotNull final SecurityPlatform securityPlatform,
      @Nullable final String result,
      @Nullable final Double score) {
    return InjectExpectationResult.builder()
        .sourceId(securityPlatform.getId())
        .sourceType(SECURITY_PLATFORM)
        .sourceName(securityPlatform.getName())
        .sourcePlatform(
            securityPlatform.getSecurityPlatformType() != null
                ? securityPlatform.getSecurityPlatformType().name()
                : null)
        .sourceAssetId(securityPlatform.getId())
        .score(score)
        .result(result)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static InjectExpectationResult buildForSecurityPlatformInFailed(
      @NotNull final SecurityPlatform securityPlatform) {
    return buildForSecurityPlatform(securityPlatform, VULNERABILITY.failureLabel, 0.0);
  }

  public static InjectExpectationResult buildDefaultForSecurityPlatform(
      @NotNull final SecurityPlatform securityPlatform) {
    return buildForSecurityPlatform(securityPlatform, NO_RESULT, NO_SCORE);
  }

  public static InjectExpectationResult buildDefaultForPlayerManualValidation() {
    return buildForPlayerManualValidation(NO_RESULT, NO_SCORE);
  }

  public static InjectExpectationResult buildForPlayerManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return InjectExpectationResult.builder()
        .sourceId(PLAYER_MANUAL_VALIDATION_SOURCE_ID)
        .sourceType(PLAYER_MANUAL_VALIDATION_SOURCE_TYPE)
        .sourceName(PLAYER_MANUAL_VALIDATION_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  public static InjectExpectationResult buildForTeamManualValidation(
      @NotNull final String result, @NotNull final Double score) {
    return InjectExpectationResult.builder()
        .sourceId(TEAM_MANUAL_VALIDATION_SOURCE_ID)
        .sourceType(TEAM_MANUAL_VALIDATION_SOURCE_TYPE)
        .sourceName(TEAM_MANUAL_VALIDATION_SOURCE_NAME)
        .sourcePlatform(NOT_APPLICABLE)
        .sourceAssetId(NO_ASSET)
        .result(result)
        .score(score)
        .date(String.valueOf(Instant.now()))
        .build();
  }

  // -- CLOSE --

  public static void expireEmptyResults(
      final List<InjectExpectationResult> results, final Double score, final String result) {
    // An expectation that never received any result has a null results list; there is nothing to
    // expire in that case, so no-op instead of throwing (ExpectationsExpirationManagerJob NPE).
    if (results == null) {
      return;
    }
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

  public static boolean hasNoResults(final List<InjectExpectationResult> results) {
    return results == null
        || results.isEmpty()
        || results.stream().noneMatch(r -> hasText(r.getResult()));
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
