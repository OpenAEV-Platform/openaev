package io.openaev.service;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.engine.EngineService;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.api.StructuralHistogramRuntime;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.query.EsAttackPath;
import io.openaev.engine.query.EsSeries;
import io.openaev.engine.query.EsSeriesData;
import io.openaev.utils.CustomDashboardTimeRange;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsAttackPathService {

  private final AttackPatternRepository attackPatternRepository;

  private final EngineService esService;

  /**
   * Fetches attack paths for a given simulation.
   *
   * @param user the user requesting the data
   * @param runtime the structural histogram runtime containing the simulation filter and the series
   * @return a list of attack paths associated with the simulation
   * @throws ExecutionException
   * @throws InterruptedException
   */
  public List<EsAttackPath> attackPaths(
      RawUserAuth user,
      StructuralHistogramRuntime runtime,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters)
      throws ExecutionException, InterruptedException {

    String simulationId = extractSimulationIdFromSeriesFilter(runtime.getWidget());

    CompletableFuture<List<EsInject>> simulationEsInjectsFuture =
        CompletableFuture.supplyAsync(
            () ->
                fetchSimulationInjectsFromES(user, simulationId, parameters, definitionParameters));
    CompletableFuture<List<EsSeries>> simulationSeriesFuture =
        CompletableFuture.supplyAsync(() -> esService.multiTermHistogram(user, runtime));

    List<EsInject> simulationEsInjects = simulationEsInjectsFuture.get();
    List<EsSeries> simulationSeries = simulationSeriesFuture.get();

    // Fetch attackPattern of simulation
    Set<String> attackPatternIds = extractAttackPatternFromEsInjects(simulationEsInjects);
    Map<String, AttackPattern> attackPatternMap = fetchAttackPatterns(attackPatternIds);

    // Process series results
    Map<String, Long> successRateByAttackPatternIdMap =
        computeSuccessRateSeriesByAttackPatternId(simulationSeries);

    // Build Attack Paths
    return buildAttackPathsFromEsInjectList(
        simulationEsInjects, attackPatternMap, successRateByAttackPatternIdMap);
  }

  /**
   * Extracts the simulation ID from the series filter of the given structural histogram runtime.
   *
   * @param widget the structural histogram widget containing where the simulation ID is
   * @return the simulation ID extracted
   */
  public String extractSimulationIdFromSeriesFilter(StructuralHistogramWidget widget) {
    return widget.getSeries().getFirst().getFilter().getFilters().stream()
        .filter(f -> "base_simulation_side".equals(f.getKey()))
        .findFirst()
        .map(f -> f.getValues().getFirst())
        .orElseThrow();
  }

  /**
   * Fetches the injects associated with a given simulation ID from Elasticsearch.
   *
   * @param user the user requesting the data
   * @param simulationId the ID of the simulation for which injects are to be fetched
   * @return a list of EsInject objects associated with the simulation
   */
  private List<EsInject> fetchSimulationInjectsFromES(
      RawUserAuth user,
      String simulationId,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {
    Map<String, List<String>> filterMap = Map.of("base_simulation_side", List.of(simulationId));
    ListConfiguration config = esService.createListConfiguration("inject", filterMap);
    config.setDateAttribute("inject_created_at");
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);

    return esService
        .entities(user, new ListRuntime(config, parameters, definitionParameters))
        .stream()
        .filter(EsInject.class::isInstance)
        .map(EsInject.class::cast)
        .toList();
  }

  /**
   * Extracts attack pattern IDs from a list of EsInject objects.
   *
   * @param esInject the list of EsInject objects to extract attack patterns from
   * @return a set of unique attack pattern IDs
   */
  private Set<String> extractAttackPatternFromEsInjects(List<EsInject> esInject) {
    return esInject.stream()
        .filter(i -> i.getBase_attack_patterns_side() != null)
        .flatMap(i -> i.getBase_attack_patterns_side().stream())
        .collect(Collectors.toSet());
  }

  /**
   * Fetches attack patterns from the database based on a set of attack pattern IDs.
   *
   * @param attackPatternIds the set of attack pattern IDs to fetch
   * @return a map of attack pattern IDs to AttackPattern objects
   */
  private Map<String, AttackPattern> fetchAttackPatterns(Set<String> attackPatternIds) {
    if (attackPatternIds.isEmpty()) {
      return new HashMap<>();
    }

    return StreamSupport.stream(
            attackPatternRepository.findAllById(attackPatternIds).spliterator(), false)
        .collect(Collectors.toMap(AttackPattern::getId, attackPattern -> attackPattern));
  }

  /**
   * Computes the success rate series by attack pattern ID from a list of inject expectation
   *
   * @param injectExpectationSeries the list of EsSeries containing inject expectations
   * @return a map where keys are attack pattern IDs and values are their success rates
   */
  private Map<String, Long> computeSuccessRateSeriesByAttackPatternId(
      List<EsSeries> injectExpectationSeries) {
    Map<String, Long> successCounts = aggregateSeriesData(injectExpectationSeries, "SUCCESS");
    Map<String, Long> failedCounts = aggregateSeriesData(injectExpectationSeries, "FAILED");

    Map<String, Long> successRateMap = new HashMap<>();
    Set<String> allKeys = new HashSet<>(successCounts.keySet());
    allKeys.addAll(failedCounts.keySet());

    for (String key : allKeys) {
      long success = successCounts.getOrDefault(key, 0L);
      long failure = failedCounts.getOrDefault(key, 0L);
      long total = success + failure;

      Long successRate = total > 0 ? (success * 100) / total : null;
      successRateMap.put(key, successRate);
    }

    return successRateMap;
  }

  /**
   * Aggregates data from a list of EsSeries based on a specific filter label. ( Success or Failed )
   *
   * @param series the list of EsSeries to aggregate
   * @param label the label to filter the series data (e.g., "SUCCESS" or "FAILED")
   * @return a map where keys are series data keys(attackPatternId) and values are their aggregated
   *     counts
   */
  private Map<String, Long> aggregateSeriesData(List<EsSeries> series, String label) {
    return series.stream()
        .filter(s -> label.equals(s.getLabel()))
        .flatMap(s -> s.getData().stream())
        .collect(Collectors.toMap(EsSeriesData::getKey, EsSeriesData::getValue, Long::sum));
  }

  /**
   * Builds a list of attack paths from a list of EsInject objects, mapping them to their
   * corresponding attack patterns and success rates.
   *
   * @param esInjects the list of EsInject objects to process
   * @param attackPatterns a map of attack pattern IDs to AttackPattern objects
   * @param successRateMap a map of attack pattern IDs to their success rates
   * @return a list of EsAttackPath objects representing the attack paths
   */
  private List<EsAttackPath> buildAttackPathsFromEsInjectList(
      List<EsInject> esInjects,
      Map<String, AttackPattern> attackPatterns,
      Map<String, Long> successRateMap) {
    Map<String, EsAttackPath> esAttackPathsMap = new HashMap<>();

    for (EsInject inject : esInjects) {
      if (inject.getBase_attack_patterns_side() == null) {
        continue;
      }

      for (String attackId : inject.getBase_attack_patterns_side()) {
        esAttackPathsMap.compute(
            attackId,
            (key, value) ->
                value == null
                    ? createNewAttackPath(
                        attackPatterns.get(attackId), inject, successRateMap.get(attackId))
                    : updateAttackPath(value, inject));
      }
    }

    return esAttackPathsMap.values().stream().toList();
  }

  /**
   * Creates a new EsAttackPath object based on the provided attack pattern and inject.
   *
   * @param attackPattern the attack pattern to base the attack path on
   * @param inject the inject containing the base ID and children attack patterns
   * @param successRate the success rate of the attack pattern
   * @return a new EsAttackPath object, or null if the attack pattern is null
   */
  private EsAttackPath createNewAttackPath(
      AttackPattern attackPattern, EsInject inject, Long successRate) {
    if (attackPattern == null) {
      return null; // Or handle missing attack pattern appropriately
    }

    List<EsAttackPath.KillChainPhaseObject> killChainPhases =
        attackPattern.getKillChainPhases().stream()
            .map(
                killChainPhase ->
                    new EsAttackPath.KillChainPhaseObject(
                        killChainPhase.getId(),
                        killChainPhase.getName(),
                        killChainPhase.getOrder()))
            .toList();

    Set<String> childrenIds =
        inject.getBase_attack_patterns_children_side() != null
            ? new HashSet<>(inject.getBase_attack_patterns_children_side())
            : new HashSet<>();

    return new EsAttackPath(
        attackPattern.getId(),
        attackPattern.getName(),
        attackPattern.getExternalId(),
        killChainPhases,
        childrenIds,
        new HashSet<>(List.of(inject.getBase_id())),
        successRate);
  }

  /**
   * Updates an existing EsAttackPath object by adding the inject's base ID and children
   *
   * @param attackPath the existing EsAttackPath to update
   * @param inject the EsInject containing the base ID and children attack patterns
   * @return the updated EsAttackPath object
   */
  private EsAttackPath updateAttackPath(EsAttackPath attackPath, EsInject inject) {
    attackPath.getInjectIds().add(inject.getBase_id());
    if (attackPath.getAttackPatternChildrenIds() != null
        && inject.getBase_attack_patterns_children_side() != null) {
      attackPath
          .getAttackPatternChildrenIds()
          .addAll(inject.getBase_attack_patterns_children_side());
    }
    return attackPath;
  }
}
