package io.openaev.utils;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Attributes inject expectation results to the security platform that produced them.
 *
 * <p>A result belongs to a security platform when that platform is its source asset ({@link
 * InjectExpectationResult#getSourceAssetId()}). It is the one field every writer stamps with the
 * platform id: a collector linked to a platform (whose {@code sourceId} is the collector id, not
 * the platform id), a platform answering directly (assessment injectors) and a manual detection or
 * prevention validation against a platform. It is also the field the platforms of a simulation are
 * discovered from ({@code InjectService#extractSecurityPlatforms}), so a platform is attributed
 * exactly the results it was discovered by.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public final class SecurityPlatformResultUtils {

  private SecurityPlatformResultUtils() {}

  /**
   * Whether the result was produced by the given security platform.
   *
   * @param result the expectation result, may be {@code null}
   * @param securityPlatformId the id of the security platform asset
   * @return {@code true} when the platform is the source asset of the result
   */
  public static boolean isFromSecurityPlatform(
      final InjectExpectationResult result, @NotNull final String securityPlatformId) {
    return result != null && securityPlatformId.equals(result.getSourceAssetId());
  }

  /**
   * Whether at least one result of the expectation was produced by the given security platform.
   *
   * @param expectation the expectation to inspect
   * @param securityPlatformId the id of the security platform asset
   * @return {@code true} when the platform reported on this expectation
   */
  public static boolean hasResultFromSecurityPlatform(
      @NotNull final BaseInjectExpectation expectation, @NotNull final String securityPlatformId) {
    return expectation.getResults() != null
        && expectation.getResults().stream()
            .anyMatch(result -> isFromSecurityPlatform(result, securityPlatformId));
  }

  /**
   * Returns the expectation as one security platform sees it: a detached clone that keeps only the
   * results of that platform and is scored with the best of them, so the existing aggregation
   * utilities score the platform alone. The managed expectation is never modified.
   *
   * <p>The score is {@code null} (pending) when the platform has no result on the expectation or
   * none of its results carries a score yet.
   *
   * @param expectation the expectation, typically a managed entity
   * @param securityPlatformId the id of the security platform asset
   * @return a detached copy restricted to the platform's results
   */
  public static BaseInjectExpectation toSecurityPlatformView(
      @NotNull final BaseInjectExpectation expectation, @NotNull final String securityPlatformId) {
    BaseInjectExpectation view = expectation.clone();
    List<InjectExpectationResult> platformResults =
        view.getResults().stream()
            .filter(result -> isFromSecurityPlatform(result, securityPlatformId))
            .toList();
    view.setResults(platformResults);
    view.setScore(
        platformResults.stream()
            .map(InjectExpectationResult::getScore)
            .filter(Objects::nonNull)
            .max(Double::compare)
            .orElse(null));
    return view;
  }

  /**
   * Computes, for each security platform, the expectation results it produced on the given
   * expectations.
   *
   * <p>A platform is scored only on the expectations it reported on: an expectation without any
   * result of the platform is outside its scope (another platform monitors that asset, or the
   * platform does not handle that expectation type) and does not dilute its success rate. Each
   * expectation type is listed only when the platform reported on at least one expectation of that
   * type. Normalization and aggregation are the ones of the global score ({@link
   * InjectExpectationResultUtils#getExpectationResultByTypes}), so a platform reporting on every
   * expectation gets the global score.
   *
   * @param expectations the expectations to attribute, typically the primary expectations of the
   *     injects matching one covered object
   * @param securityPlatformIds the platforms that may be attributed; results of any other source
   *     are ignored
   * @return the results by expectation type for every platform with at least one result, keyed by
   *     platform id in ascending order; platforms without any result are absent
   */
  public static Map<String, List<ExpectationResultsByType>> computeResultsBySecurityPlatform(
      @NotNull final Collection<? extends BaseInjectExpectation> expectations,
      @NotNull final Collection<String> securityPlatformIds) {
    Map<String, List<ExpectationResultsByType>> resultsByPlatform = new LinkedHashMap<>();
    for (String securityPlatformId : new TreeSet<>(securityPlatformIds)) {
      List<BaseInjectExpectation> platformViews =
          expectations.stream()
              .filter(expectation -> hasResultFromSecurityPlatform(expectation, securityPlatformId))
              .map(expectation -> toSecurityPlatformView(expectation, securityPlatformId))
              .toList();
      if (platformViews.isEmpty()) {
        continue;
      }
      List<ExpectationResultsByType> platformResults =
          InjectExpectationResultUtils.getExpectationResultByTypes(
              platformViews, InjectExpectationResultUtils::getScores);
      if (!platformResults.isEmpty()) {
        resultsByPlatform.put(securityPlatformId, platformResults);
      }
    }
    return resultsByPlatform;
  }
}
