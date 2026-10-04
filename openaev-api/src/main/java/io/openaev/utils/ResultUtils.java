package io.openaev.utils;

import static java.util.Collections.emptyList;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectExpectationIndexing;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.mapper.InjectExpectationMapper;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Component for computing and aggregating expectation results across exercises and scenarios.
 *
 * <p>Provides methods to calculate global expectation results, filter by security platform, and
 * group results by attack patterns. This component is essential for generating reports and
 * dashboards that show the overall effectiveness of security controls.
 *
 * @see ExpectationResultsByType
 * @see InjectExpectationMapper
 */
@RequiredArgsConstructor
@Component
public class ResultUtils {

  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectExpectationMapper injectExpectationMapper;

  /**
   * Computes global expectation results across all specified injects.
   *
   * <p>Aggregates expectation results by type (Prevention, Detection, Vulnerability, Human
   * Response) for the given set of inject IDs using optimized raw database queries.
   *
   * @param injectIds the set of inject IDs to compute results for
   * @return a list of aggregated results by expectation type, or empty list if no injects provided
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResults(Set<String> injectIds) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<RawInjectExpectationIndexing> expectations =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds);

    return injectExpectationMapper.extractExpectationResultByTypesFromRaw(injectIds, expectations);
  }

  /**
   * Loads the expectations the per-platform results of the given injects are computed from: the
   * primary expectations, the ones the global score is computed from (agent-level and player-level
   * expectations are rolled up into them and excluded), and every expectation of an inject that has
   * no primary expectation (for example an inject with agent-level expectations only), the fallback
   * the result mappers apply ({@code InjectMapper#toInjectResultOverviewOutput}).
   *
   * <p>Callers computing several results over the same injects load them once with this method and
   * pass them to {@link #computeGlobalExpectationResultsForPlatform} instead of querying once per
   * platform. At most two queries are issued, whatever the number of injects.
   *
   * @param injectIds the set of inject IDs to load the expectations of
   * @return the expectations of the injects, or an empty list if no injects are provided
   */
  public List<BaseInjectExpectation> findExpectationsForPlatformResults(Set<String> injectIds) {
    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }
    List<BaseInjectExpectation> primaryExpectations =
        injectExpectationRepository.findAllForGlobalScoreByInjects(injectIds);
    Set<String> injectsWithPrimary =
        primaryExpectations.stream()
            .map(expectation -> expectation.getInject().getId())
            .collect(Collectors.toSet());
    Set<String> injectsWithoutPrimary =
        injectIds.stream()
            .filter(injectId -> !injectsWithPrimary.contains(injectId))
            .collect(Collectors.toSet());
    if (injectsWithoutPrimary.isEmpty()) {
      return primaryExpectations;
    }
    List<BaseInjectExpectation> expectations = new ArrayList<>(primaryExpectations);
    expectations.addAll(injectExpectationRepository.findAllByInjectIds(injectsWithoutPrimary));
    return expectations;
  }

  /**
   * Computes global expectation results filtered by a specific security platform.
   *
   * <p>Similar to {@link #computeGlobalExpectationResults(Set)} but every expectation is scored
   * only with the results of the platform ({@link
   * SecurityPlatformResultUtils#toSecurityPlatformView}), on detached copies so the original
   * expectations are never modified: an expectation the platform did not report on counts as
   * pending. Results are attributed to the platform through their source asset, see {@link
   * SecurityPlatformResultUtils}.
   *
   * @param injectIds the set of inject IDs the expectations belong to
   * @param expectations the expectations of those injects, loaded with {@link
   *     #findExpectationsForPlatformResults(Set)}
   * @param securityPlatform the security platform to filter results by
   * @return a list of aggregated results filtered to the specified platform
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResultsForPlatform(
      Set<String> injectIds,
      List<BaseInjectExpectation> expectations,
      SecurityPlatform securityPlatform) {
    return computeGlobalExpectationResultsForPlatforms(
        injectIds, expectations, Set.of(securityPlatform.getId()));
  }

  /**
   * Computes global expectation results filtered by a group of security platforms, see {@link
   * #computeGlobalExpectationResultsForPlatform}: every expectation carries the best verdict of the
   * platforms of the group. Used for the platforms that share one STIX identity.
   *
   * @param injectIds the set of inject IDs the expectations belong to
   * @param expectations the expectations of those injects, loaded with {@link
   *     #findExpectationsForPlatformResults(Set)}
   * @param securityPlatformIds the ids of the security platforms of the group
   * @return a list of aggregated results filtered to the platforms of the group
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResultsForPlatforms(
      Set<String> injectIds,
      List<BaseInjectExpectation> expectations,
      Collection<String> securityPlatformIds) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<BaseInjectExpectation> platformViews =
        expectations.stream()
            .map(
                expectation ->
                    SecurityPlatformResultUtils.toSecurityPlatformView(
                        expectation, securityPlatformIds))
            .toList();

    return injectExpectationMapper.extractExpectationResultByTypes(injectIds, platformViews);
  }

  /**
   * Computes inject expectation results grouped by attack pattern.
   *
   * <p>Organizes the expectations from the provided injects by their associated attack patterns.
   * Each inject may be linked to multiple attack patterns through its injector contract, resulting
   * in grouped results suitable for MITRE ATT&CK matrix visualization.
   *
   * @param injects the list of injects to process (must not be null)
   * @return a list of expectation results grouped by attack pattern
   */
  public List<InjectExpectationResultsByAttackPattern> computeInjectExpectationResults(
      @NotNull final List<Inject> injects) {

    Map<AttackPattern, List<Inject>> groupedByAttackPattern =
        injects.stream()
            .flatMap(
                inject ->
                    inject
                        .getInjectorContract()
                        .map(
                            contract ->
                                contract.getAttackPatterns().stream()
                                    .map(attackPattern -> Map.entry(attackPattern, inject)))
                        .orElseGet(Stream::empty))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    return groupedByAttackPattern.entrySet().stream()
        .map(
            entry ->
                injectExpectationMapper.toInjectExpectationResultsByAttackPattern(
                    entry.getKey(), entry.getValue()))
        .toList();
  }
}
