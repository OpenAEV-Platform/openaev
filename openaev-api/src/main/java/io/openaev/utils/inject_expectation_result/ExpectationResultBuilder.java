package io.openaev.utils.inject_expectation_result;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.*;
import static io.openaev.service.InjectExpectationService.COLLECTOR;
import static io.openaev.service.InjectExpectationService.SECURITY_PLATFORM;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.rest.inject.form.InjectExpectationUpdateFromCollectorInput;
import io.openaev.service.InjectExpectationUtils;
import io.openaev.service.expectation.ExpectationVerdict;
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
   * <p>* SUCCESS if any expected source reports expectedScore. For DETECTION and PREVENTION this
   * settles the expectation at once: one security platform proving it is enough, so the other
   * expected sources are not waited for. VULNERABILITY keeps waiting, since another source may
   * still report the asset vulnerable.
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
    if (hasNoResults(results)) {
      return null;
    }
    if (isSettledByAnySource(expectation) && hasAnyScoreReaching(results, expectedScore)) {
      return maxScore(results);
    }
    if (hasAnyEmptyResult(results)) {
      return null;
    }

    return maxScore(results);
  }

  private static boolean isSettledByAnySource(@NotNull final BaseInjectExpectation expectation) {
    return BaseInjectExpectation.EXPECTATION_TYPE.DETECTION.equals(expectation.getType())
        || BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION.equals(expectation.getType());
  }

  private static boolean hasAnyScoreReaching(
      @NotNull final List<InjectExpectationResult> results, @NotNull final Double expectedScore) {
    return results.stream()
        .map(InjectExpectationResult::getScore)
        .anyMatch(score -> score != null && score >= expectedScore);
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
  // TODO: duplicate of computeScore
  public static Double computeResultsScore(
      @NotNull final List<InjectExpectationResult> results,
      @NotNull final BaseInjectExpectation expectation) {
    if (hasNoResults(results)) {
      return null;
    }
    final Double expectedScore = expectation.getExpectedScore();
    // TODO: improve the expectation type check in the inject refactor
    if (expectedScore != null
        && isSettledByAnySource(expectation)
        && hasAnyScoreReaching(results, expectedScore)) {
      return maxScore(results);
    }
    if (hasAnyEmptyResult(results)) {
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
        .date(Instant.now())
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
      @NotNull final ExpectationVerdict verdict,
      @NotNull final String resultMsg) {
    InjectExpectationResult existing =
        findResultBySourceId(baseInjectExpectation.getResults(), verdict.sourceId());
    if (existing != null) {
      existing.setResult(resultMsg);
      existing.setScore(verdict.score());
      // A source answering again without metadata keeps the metadata of its previous answer
      if (verdict.metadata() != null) {
        existing.setMetadata(verdict.metadata());
      }
    } else {
      existing =
          InjectExpectationResult.builder()
              .sourceId(verdict.sourceId())
              .sourceType(verdict.sourceType())
              .sourceName(verdict.sourceName())
              .sourcePlatform(verdict.sourcePlatform())
              .sourceAssetId(verdict.sourceAssetId())
              .result(resultMsg)
              .date(now())
              .score(verdict.score())
              .metadata(verdict.metadata())
              .build();
      baseInjectExpectation.addResult(existing);
    }
  }

  /**
   * Adds (or updates) the result written by a security platform source (e.g. assessment injectors),
   * upserted on the platform ID. Collector verdicts go through the behaviors instead: see {@link
   * #addResult(BaseInjectExpectation, ExpectationVerdict, String)}.
   */
  public static void addResult(
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @NotNull final InjectExpectationUpdateFromCollectorInput input,
      @NotNull final SecurityPlatform securityPlatform) {
    final double score =
        InjectExpectationUtils.computeScore(baseInjectExpectation, input.getIsSuccess());

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
              .date(Instant.now())
              .score(score)
              .metadata(input.getMetadata())
              .build();
      baseInjectExpectation.addResult(existing);
    }
  }

  /**
   * Removes the result of a source from the expectation. The score is left untouched: it is the
   * caller's job to recompute it.
   */
  public static List<InjectExpectationResult> removeResultsFromSource(
      @NotNull final BaseInjectExpectation expectation, @NotBlank final String sourceId) {
    return expectation.clearResultsFromSource(sourceId);
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
        .date(Instant.now())
        .score(score)
        .build();
  }

  public static String getSourcePlatform(Collector collector) {
    return Optional.ofNullable(collector.getSecurityPlatform())
        .map(sp -> sp.getSecurityPlatformType().name())
        .orElse(null);
  }

  public static String getSourceAssetId(Collector collector) {
    return collector.isExternal() && collector.getSecurityPlatform() != null
        ? collector.getSecurityPlatform().getId()
        : null;
  }

  public static ExpectationVerdict buildMediaPressureVerdict(@NotNull final Double score) {
    return ExpectationVerdict.builder()
        .sourceId(MEDIA_PRESSURE_SOURCE_ID)
        .sourceType(MEDIA_PRESSURE_SOURCE_TYPE)
        .sourceName(MEDIA_PRESSURE_SOURCE_NAME)
        .sourceAssetId(null)
        .score(score)
        .build();
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
        .date(Instant.now())
        .build();
  }

  public static InjectExpectationResult buildDefaultForVulnerabilityManagerInFailed() {
    return buildForVulnerabilityManager(NO_RESULT, NO_SCORE);
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
        .date(Instant.now())
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
        .date(Instant.now())
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
}
