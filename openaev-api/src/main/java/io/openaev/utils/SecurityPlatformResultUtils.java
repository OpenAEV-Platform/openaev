package io.openaev.utils;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY;
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
import java.util.Set;
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
    return isFromSecurityPlatforms(result, Set.of(securityPlatformId));
  }

  /**
   * Whether the result was produced by one of the given security platforms.
   *
   * @param result the expectation result, may be {@code null}
   * @param securityPlatformIds the ids of the security platform assets
   * @return {@code true} when one of the platforms is the source asset of the result
   */
  public static boolean isFromSecurityPlatforms(
      final InjectExpectationResult result, @NotNull final Collection<String> securityPlatformIds) {
    return result != null
        && result.getSourceAssetId() != null
        && securityPlatformIds.contains(result.getSourceAssetId());
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
    return hasResultFromSecurityPlatforms(expectation, Set.of(securityPlatformId));
  }

  /**
   * Whether one of the given security platforms reported on the expectation, see {@link
   * #hasResultFromSecurityPlatform}.
   *
   * @param expectation the expectation to inspect
   * @param securityPlatformIds the ids of the security platform assets
   * @return {@code true} when one of the platforms has a result on the expectation or below it
   */
  public static boolean hasResultFromSecurityPlatforms(
      @NotNull final BaseInjectExpectation expectation,
      @NotNull final Collection<String> securityPlatformIds) {
    return hasDirectResultFromSecurityPlatforms(expectation, securityPlatformIds)
        || childrenOf(expectation).stream()
            .anyMatch(child -> hasResultFromSecurityPlatforms(child, securityPlatformIds));
  }

  /**
   * Returns the expectation as one security platform sees it: a detached clone that keeps only the
   * results the platform wrote on it and carries the platform's verdict as score, so the existing
   * aggregation utilities score the platform alone. The managed expectation is never modified.
   *
   * <p>The verdict is rolled up from the children the platform reported on when the expectation has
   * some (agents of an asset, assets of an asset group), and is otherwise the best score of the
   * platform's own results, a VULNERABLE result winning for VULNERABILITY. It is {@code null}
   * (pending) when the platform has not answered yet.
   *
   * @param expectation the expectation, typically a managed entity
   * @param securityPlatformId the id of the security platform asset
   * @return a detached copy restricted to the platform's results and verdict
   */
  public static BaseInjectExpectation toSecurityPlatformView(
      @NotNull final BaseInjectExpectation expectation, @NotNull final String securityPlatformId) {
    return toSecurityPlatformView(expectation, Set.of(securityPlatformId));
  }

  /**
   * Returns the expectation as a group of security platforms sees it, see {@link
   * #toSecurityPlatformView(BaseInjectExpectation, String)}: the results of every platform of the
   * group are kept, so the group's verdict on an expectation is the best one of its platforms (the
   * worst one for VULNERABILITY).
   *
   * @param expectation the expectation, typically a managed entity
   * @param securityPlatformIds the ids of the security platform assets of the group
   * @return a detached copy restricted to the group's results and verdict
   */
  public static BaseInjectExpectation toSecurityPlatformView(
      @NotNull final BaseInjectExpectation expectation,
      @NotNull final Collection<String> securityPlatformIds) {
    BaseInjectExpectation view = expectation.clone();
    view.setResults(
        view.getResults().stream()
            .filter(result -> isFromSecurityPlatforms(result, securityPlatformIds))
            .toList());
    view.setScore(securityPlatformScore(expectation, view, securityPlatformIds));
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
    Map<String, Set<String>> groups = new LinkedHashMap<>();
    securityPlatformIds.forEach(id -> groups.put(id, Set.of(id)));
    return computeResultsBySecurityPlatformGroup(expectations, groups);
  }

  /**
   * Computes, for each group of security platforms, the expectation results its platforms produced
   * on the given expectations, with the rules of {@link #computeResultsBySecurityPlatform}: a group
   * is scored on the expectations one of its platforms reported on, with the best verdict of its
   * platforms (the worst one for VULNERABILITY). Used for the platforms that share one STIX
   * identity.
   *
   * @param expectations the expectations to attribute
   * @param securityPlatformIdsByGroup the platform ids of every group, by group key
   * @return the results by expectation type for every group with at least one result, keyed by
   *     group key in ascending order; groups without any result are absent
   */
  public static Map<String, List<ExpectationResultsByType>> computeResultsBySecurityPlatformGroup(
      @NotNull final Collection<? extends BaseInjectExpectation> expectations,
      @NotNull final Map<String, ? extends Collection<String>> securityPlatformIdsByGroup) {
    Map<String, List<ExpectationResultsByType>> resultsByGroup = new LinkedHashMap<>();
    for (String groupKey : new TreeSet<>(securityPlatformIdsByGroup.keySet())) {
      Collection<String> platformIds = securityPlatformIdsByGroup.get(groupKey);
      List<BaseInjectExpectation> groupViews =
          expectations.stream()
              .filter(expectation -> hasResultFromSecurityPlatforms(expectation, platformIds))
              .map(expectation -> toSecurityPlatformView(expectation, platformIds))
              .toList();
      if (groupViews.isEmpty()) {
        continue;
      }
      List<ExpectationResultsByType> groupResults =
          InjectExpectationResultUtils.getExpectationResultByTypes(
              groupViews, InjectExpectationResultUtils::getScores);
      if (!groupResults.isEmpty()) {
        resultsByGroup.put(groupKey, groupResults);
      }
    }
    return resultsByGroup;
  }

  private static boolean hasDirectResultFromSecurityPlatforms(
      final BaseInjectExpectation expectation, final Collection<String> securityPlatformIds) {
    return expectation.getResults() != null
        && expectation.getResults().stream()
            .anyMatch(result -> isFromSecurityPlatforms(result, securityPlatformIds));
  }

  private static Double securityPlatformScore(
      final BaseInjectExpectation expectation,
      final BaseInjectExpectation directView,
      final Collection<String> securityPlatformIds) {
    Double directScore =
        directView.getResults().stream()
            .map(InjectExpectationResult::getScore)
            .filter(Objects::nonNull)
            .max(Double::compare)
            .orElse(null);
    List<BaseInjectExpectation> reportedChildren =
        childrenOf(expectation).stream()
            .filter(child -> hasResultFromSecurityPlatforms(child, securityPlatformIds))
            .map(child -> toSecurityPlatformView(child, securityPlatformIds))
            .toList();
    if (reportedChildren.isEmpty() || expectation.getExpectedScore() == null) {
      return InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(directView, directScore);
    }
    Double childrenScore =
        InjectExpectationUtils.computeChildrenScore(
            expectation.isExpectationGroup(), expectation.getExpectedScore(), reportedChildren);
    if (expectation instanceof TechnicalInjectExpectation technical
        && isAssetExpectation(technical)) {
      return combineAssetVerdict(directView, directScore, childrenScore);
    }
    return InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(directView, childrenScore);
  }

  /**
   * Combines the platform's direct verdict on an asset expectation with the verdict rolled up from
   * its agents, with the rule the persisted score follows ({@code
   * InjectExpectationService#combineWithChildrenVerdict}): a direct DETECTION / PREVENTION success
   * wins, and for VULNERABILITY the worst verdict wins: a direct VULNERABLE verdict first, then a
   * VULNERABLE verdict of the agents (a finding reported by an agent overrides a direct "Not
   * vulnerable" through the rollup), then the direct verdict, the agents deciding only when the
   * platform has no direct verdict.
   */
  private static Double combineAssetVerdict(
      final BaseInjectExpectation directView,
      final Double directScore,
      final Double childrenScore) {
    if (VULNERABILITY.equals(directView.getType())) {
      Double directVulnerable =
          InjectExpectationUtils.reconcileWithDirectVulnerableVerdict(directView, null);
      if (directVulnerable != null) {
        return directVulnerable;
      }
      Double expectedScore = directView.getExpectedScore();
      if (childrenScore != null && expectedScore != null && childrenScore < expectedScore) {
        return childrenScore;
      }
      return directScore != null ? directScore : childrenScore;
    }
    if (directScore != null && directScore >= directView.getExpectedScore()) {
      return directScore;
    }
    return childrenScore;
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
