package io.openbas.service;

import static io.openbas.utils.AssetUtils.computePairsPlatformArchitecture;
import static java.util.Collections.emptySet;

import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.rest.attack_pattern.service.AttackPatternService;
import io.openbas.rest.inject.service.InjectAssistantService;
import io.openbas.rest.inject.service.InjectService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Service
@Slf4j
@Validated
public class SecurityCoverageInjectService {

  public static final int INJECTS_PER_ATTACK_PATTERN = 1;

  private final InjectService injectService;
  private final InjectAssistantService injectAssistantService;
  private final AttackPatternService attackPatternService;
  private final AssetGroupService assetGroupService;

  private final InjectRepository injectRepository;

  /**
   * Creates and manages injects for the given scenario based on the associated security coverage.
   *
   * <p>Steps:
   *
   * <ul>
   *   <li>Resolves internal AttackPatterns from the coverage
   *   <li>Fetches asset groups based on scenario tag rules
   *   <li>Analyzes existing inject coverage
   *   <li>Removes outdated injects
   *   <li>Generates missing injects depending on whether asset groups are available
   * </ul>
   *
   * @param scenario the scenario for which injects are managed
   * @param securityCoverage the related security coverage providing AttackPattern references
   * @return list injects related to this scenario
   */
  public Set<Inject> createdInjectsForScenario(
      Scenario scenario, SecurityCoverage securityCoverage) {
    // 1. Fetch internal Ids for AttackPatterns
    Map<String, AttackPattern> attackPatterns =
        attackPatternService.fetchInternalAttackPatternIds(securityCoverage.getAttackPatternRefs());

    if (attackPatterns.isEmpty()) {
      injectService.deleteAll(scenario.getInjects());
      return emptySet();
    }

    // 2. Fetch asset groups via tag rules
    List<AssetGroup> assetGroups = assetGroupService.fetchAssetGroupsFromScenarioTagRules(scenario);

    // 3. Fetch Inject coverage
    Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap =
        injectService.extractCombinationAttackPatternPlatformArchitecture(scenario);

    // 4. Get all endpoints per asset group
    Map<AssetGroup, List<Endpoint>> assetsFromGroupMap =
        assetGroupService.assetsFromAssetGroupMap(assetGroups);

    // Check if assetgroups are empties because it could reduce the code
    boolean assetGroupsAreEmpties =
        assetGroups.isEmpty() || assetsFromGroupMap.values().stream().allMatch(List::isEmpty);
    if (assetGroupsAreEmpties) {
      handleNoAssetGroupsCase(scenario, attackPatterns, injectCoverageMap);
    } else {
      handleWithAssetGroupsCase(scenario, assetsFromGroupMap, attackPatterns, injectCoverageMap);
    }

    return injectRepository.findByScenarioId(scenario.getId());
  }

  /**
   * Handles inject deletion and generation when no asset groups are defined or available.
   *
   * <p>Only required AttackPatterns are used to determine what to remove or generate.
   *
   * @param scenario the scenario being processed
   * @param requiredAttackPatterns list of required AttackPatterns
   * @param injectCoverageMap current inject coverage
   */
  private void handleNoAssetGroupsCase(
      Scenario scenario,
      Map<String, AttackPattern> requiredAttackPatterns,
      Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap) {
    Set<String> coveredAttackPatterns =
        injectCoverageMap.values().stream()
            .flatMap(Set::stream)
            .map(Triple::getLeft)
            .collect(Collectors.toSet());

    // 5. Remove AttackPatterns already covered
    Set<String> requiredAttackPatternIds = requiredAttackPatterns.keySet();

    Set<String> missingAttackPatterns = new HashSet<>(requiredAttackPatternIds);
    missingAttackPatterns.removeAll(coveredAttackPatterns);

    // 6. Remove injects not in requiredAttackPatterns
    List<Inject> injectsToRemove =
        injectCoverageMap.entrySet().stream()
            .filter(
                entry -> {
                  Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> triples = entry.getValue();
                  return triples.isEmpty() // In order to filter Placeholders
                      || triples.stream()
                          .map(Triple::getLeft)
                          .noneMatch(requiredAttackPatternIds::contains);
                })
            .map(Map.Entry::getKey)
            .toList();

    injectRepository.deleteAll(injectsToRemove);

    // 7. Generate missing injects only for missing AttackPatterns and relevant asset groups
    if (!missingAttackPatterns.isEmpty()) {
      Set<AttackPattern> missingAttacks =
          missingAttackPatterns.stream()
              .map(requiredAttackPatterns::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());

      injectAssistantService.generateInjectsByAttackPatternsWithoutAssetGroups(
          scenario, missingAttacks, INJECTS_PER_ATTACK_PATTERN);
    }
  }

  /**
   * Handles inject deletion and generation when asset groups and endpoints are available.
   *
   * <p>Performs:
   *
   * <ul>
   *   <li>Required combination computation
   *   <li>Comparison with existing injects
   *   <li>Obsolete inject deletion
   *   <li>Missing inject generation
   * </ul>
   *
   * @param scenario the scenario being processed
   * @param assetsFromGroupMap the available asset groups and their endpoints
   * @param attackPatterns list of required AttackPatterns
   * @param injectCoverageMap existing inject coverage
   */
  private void handleWithAssetGroupsCase(
      Scenario scenario,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Map<String, AttackPattern> attackPatterns,
      Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap) {

    // 5. Compute all (Platform, Arch) configs across all endpoints
    List<Endpoint> endpoints =
        assetsFromGroupMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
    Set<Pair<Endpoint.PLATFORM_TYPE, String>> allPlatformArchs =
        computePairsPlatformArchitecture(endpoints);

    // 6. Build required (AttackPattern × Platform × Arch) combinations
    Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> requiredCombinations =
        buildCombinationAttackPatternPlatformArchitecture(
            attackPatterns.keySet(), allPlatformArchs);

    // 7. Extract covered combinations from existing injects
    Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> coveredCombinations =
        injectCoverageMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

    // 8. Identify injects to delete: if all their combinations are irrelevant
    // 9. Delete injects
    removeInjectsNoLongerNecessary(injectCoverageMap, requiredCombinations);

    // 10. Compute missing combinations
    // 11. Filter AttackPatterns that are still missing
    // 12. Filter AssetGroups based on missing (Platform × Arch)
    MissingCombinations missingCombinations =
        getMissingCombinations(requiredCombinations, coveredCombinations, assetsFromGroupMap);

    // 13. Generate missing injects only for missing AttackPatterns and relevant asset groups
    if (!missingCombinations.filteredAttackPatterns().isEmpty()) {
      Set<AttackPattern> missingAttacks =
          missingCombinations.filteredAttackPatterns().stream()
              .map(attackPatterns::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());

      injectAssistantService.generateInjectsByAttackPatternsWithAssetGroups(
          scenario,
          missingAttacks,
          INJECTS_PER_ATTACK_PATTERN,
          missingCombinations.filteredAssetsFromGroupMap());
    }
  }

  /**
   * Builds the complete set of required combinations of TTPs and platform-architecture pairs.
   *
   * @param attackPatterns list of attack patterns (TTPs)
   * @param allPlatformArchs set of platform-architecture pairs
   * @return set of (TTP × Platform × Architecture) combinations
   */
  private Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>
      buildCombinationAttackPatternPlatformArchitecture(
          Set<String> attackPatterns, Set<Pair<Endpoint.PLATFORM_TYPE, String>> allPlatformArchs) {
    return attackPatterns.stream()
        .flatMap(
            attackPattern ->
                allPlatformArchs.stream()
                    .map(
                        platformArch ->
                            Triple.of(
                                attackPattern, platformArch.getLeft(), platformArch.getRight())))
        .collect(Collectors.toSet());
  }

  /**
   * Removes injects that do not match any of the required (AttackPattern × Platform × Architecture)
   * combinations.
   *
   * @param injectCoverageMap current inject coverage
   * @param requiredCombinations all required combinations
   */
  private void removeInjectsNoLongerNecessary(
      Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap,
      Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> requiredCombinations) {
    // 7. Identify injects to delete: if all their combinations are irrelevant
    // Inject with configuration outdated
    List<Inject> injectsToRemove =
        injectCoverageMap.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().isEmpty() // In order to filter Placeholders
                        || entry.getValue().stream().noneMatch(requiredCombinations::contains))
            .map(Map.Entry::getKey)
            .toList();

    // 8. Remove outdated injects
    injectRepository.deleteAll(injectsToRemove);
  }

  /**
   * Computes the missing combinations by comparing required vs. covered combinations. Filters the
   * missing AttackPatterns and identifies the relevant asset groups.
   *
   * @param requiredCombinations expected combinations to be covered
   * @param coveredCombinations currently covered combinations
   * @param assetsFromGroupMap map of asset groups to endpoints
   * @return a {@link MissingCombinations} object containing uncovered AttackPatterns and relevant
   *     assets
   */
  private MissingCombinations getMissingCombinations(
      Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> requiredCombinations,
      Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> coveredCombinations,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap) {
    Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> missingCombinations =
        new HashSet<>(requiredCombinations);
    missingCombinations.removeAll(coveredCombinations);

    // 10. Filter AttackPatterns that are still missing
    Set<String> filteredAttackPatterns =
        missingCombinations.stream().map(Triple::getLeft).collect(Collectors.toSet());

    // 11. Filter AssetGroups based on missing (Platform × Arch)
    Map<AssetGroup, List<Endpoint>> filteredAssetsFromGroupMap =
        computeMissingAssetGroups(missingCombinations, assetsFromGroupMap);

    return new MissingCombinations(filteredAttackPatterns, filteredAssetsFromGroupMap);
  }

  /**
   * Filters and returns asset groups whose endpoints match any of the missing platform-architecture
   * combinations.
   *
   * @param missingCombinations set of missing AttackPattern-platform-architecture triples
   * @param assetsFromGroupMap all asset groups and their endpoints
   * @return filtered map of asset groups relevant to the missing combinations
   */
  private Map<AssetGroup, List<Endpoint>> computeMissingAssetGroups(
      Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> missingCombinations,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap) {
    Set<Pair<Endpoint.PLATFORM_TYPE, String>> missingPlatformArchs =
        missingCombinations.stream()
            .map(triple -> Pair.of(triple.getMiddle(), triple.getRight()))
            .collect(Collectors.toSet());

    List<AssetGroup> filteredAssetGroups =
        assetsFromGroupMap.entrySet().stream()
            .filter(
                entry ->
                    computePairsPlatformArchitecture(entry.getValue()).stream()
                        .anyMatch(missingPlatformArchs::contains))
            .map(Map.Entry::getKey)
            .toList();

    return assetsFromGroupMap.entrySet().stream()
        .filter(entry -> filteredAssetGroups.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Record representing the result of a missing combination analysis, containing uncovered
   * AttackPatterns and the filtered asset groups relevant to them.
   *
   * @param filteredAttackPatterns set of uncovered AttackPatterns
   * @param filteredAssetsFromGroupMap map of relevant asset groups with their endpoints
   */
  private record MissingCombinations(
      Set<String> filteredAttackPatterns,
      Map<AssetGroup, List<Endpoint>> filteredAssetsFromGroupMap) {}
}
