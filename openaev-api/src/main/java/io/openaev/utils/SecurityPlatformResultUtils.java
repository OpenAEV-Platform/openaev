package io.openaev.utils;

import static io.openaev.utils.ExpectationUtils.getAgentsExpectationsForAsset;
import static io.openaev.utils.ExpectationUtils.getExpectationsAssetsForAssetGroup;
import static io.openaev.utils.ExpectationUtils.isAgentExpectation;
import static io.openaev.utils.ExpectationUtils.isAssetExpectation;
import static io.openaev.utils.ExpectationUtils.isAssetGroupExpectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.service.InjectExpectationUtils;
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
 * <p>Collectors answer the agent expectations. Their parent asset and asset group expectations only
 * receive the rolled-up score, not the collector results, so the verdict of a platform on a parent
 * is rolled up from the platform's results on the children, with the rules of the score propagation
 * ({@link InjectExpectationUtils#computeChildrenScore}).
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
   * Whether the given security platform reported on the expectation: on the expectation itself or,
   * for an asset or asset group expectation, on one of the children its score rolls up from.
   *
   * @param expectation the expectation to inspect
   * @param securityPlatformId the id of the security platform asset
   * @return {@code true} when the platform has at least one result on the expectation or below it
   */
  public static boolean hasResultFromSecurityPlatform(
      @NotNull final BaseInjectExpectation expectation, @NotNull final String securityPlatformId) {
    return hasDirectResultFromSecurityPlatform(expectation, securityPlatformId)
        || childrenOf(expectation).stream()
            .anyMatch(child -> hasResultFromSecurityPlatform(child, securityPlatformId));
  }

  /**
   * Returns the expectation as one security platform sees it: a detached clone that keeps only the
   * results the platform wrote on it and carries the platform's verdict as score, so the existing
   * aggregation utilities score the platform alone. The managed expectation is never modified.
   *
   * <p>The verdict is rolled up from the children the platform reported on when the expectation has
   * some (agents of an asset, assets of an asset group), and is otherwise the best score of the
   * platform's own results. It is {@code null} (pending) when the platform has not answered yet.
   *
   * @param expectation the expectation, typically a managed entity
   * @param securityPlatformId the id of the security platform asset
   * @return a detached copy restricted to the platform's results and verdict
   */
  public static BaseInjectExpectation toSecurityPlatformView(
      @NotNull final BaseInjectExpectation expectation, @NotNull final String securityPlatformId) {
    BaseInjectExpectation view = expectation.clone();
    view.setResults(
        view.getResults().stream()
            .filter(result -> isFromSecurityPlatform(result, securityPlatformId))
            .toList());
    view.setScore(securityPlatformScore(expectation, view, securityPlatformId));
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
   * expectation with the global verdicts gets the global score.
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

  private static boolean hasDirectResultFromSecurityPlatform(
      final BaseInjectExpectation expectation, final String securityPlatformId) {
    return expectation.getResults() != null
        && expectation.getResults().stream()
            .anyMatch(result -> isFromSecurityPlatform(result, securityPlatformId));
  }

  private static Double securityPlatformScore(
      final BaseInjectExpectation expectation,
      final BaseInjectExpectation directView,
      final String securityPlatformId) {
    Double directScore =
        directView.getResults().stream()
            .map(InjectExpectationResult::getScore)
            .filter(Objects::nonNull)
            .max(Double::compare)
            .orElse(null);
    List<BaseInjectExpectation> reportedChildren =
        childrenOf(expectation).stream()
            .filter(child -> hasResultFromSecurityPlatform(child, securityPlatformId))
            .map(child -> toSecurityPlatformView(child, securityPlatformId))
            .toList();
    if (reportedChildren.isEmpty() || expectation.getExpectedScore() == null) {
      return directScore;
    }
    Double childrenScore =
        InjectExpectationUtils.computeChildrenScore(
            expectation.isExpectationGroup(), expectation.getExpectedScore(), reportedChildren);
    return InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(directView, childrenScore);
  }

  /**
   * The children a parent expectation's score rolls up from, resolved like the score propagation
   * does: the agent expectations of an asset expectation, the asset expectations of an asset group
   * expectation. Agent expectations and agentless rows have none.
   */
  private static List<TechnicalInjectExpectation> childrenOf(
      final BaseInjectExpectation expectation) {
    if (!(expectation instanceof TechnicalInjectExpectation technical)
        || technical.getInject() == null
        || isAgentExpectation(technical)) {
      return List.of();
    }
    if (isAssetGroupExpectation(technical)) {
      return getExpectationsAssetsForAssetGroup(technical);
    }
    if (isAssetExpectation(technical)) {
      return getAgentsExpectationsForAsset(technical);
    }
    return List.of();
  }
}
