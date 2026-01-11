package io.openaev.utils;

import static java.util.Collections.emptyList;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.mapper.InjectExpectationMapper;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
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

    List<RawInjectExpectation> expectations =
        injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds);

    return injectExpectationMapper.extractExpectationResultByTypesFromRaw(injectIds, expectations);
  }

  /**
   * Computes global expectation results filtered by a specific security platform.
   *
   * <p>Similar to {@link #computeGlobalExpectationResults(Set)} but filters expectation results to
   * only include those from the specified security platform. Results are cloned to avoid modifying
   * the original expectations, and scores are recalculated based on platform-specific results.
   *
   * @param injectIds the set of inject IDs to compute results for
   * @param securityPlatform the security platform to filter results by
   * @return a list of aggregated results filtered to the specified platform
   */
  public List<ExpectationResultsByType> computeGlobalExpectationResultsForPlatform(
      Set<String> injectIds, SecurityPlatform securityPlatform) {

    if (injectIds == null || injectIds.isEmpty()) {
      return emptyList();
    }

    List<InjectExpectation> expectations =
        injectExpectationRepository.findAllForGlobalScoreByInjects(injectIds).stream()
            .map(InjectExpectation::clone)
            .toList();
    expectations.forEach(
        exp -> {
          exp.setResults(
              exp.getResults().stream()
                  .filter(r -> r.getSourceId().equals(securityPlatform.getId()))
                  .toList());

          exp.setScore(
              exp.getResults().stream()
                  .max(Comparator.comparing(InjectExpectationResult::getScore))
                  .map(InjectExpectationResult::getScore)
                  .orElse(null));
        });

    return injectExpectationMapper.extractExpectationResultByTypes(injectIds, expectations);
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
