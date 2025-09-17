package io.openbas.service.stix;

import static io.openbas.utils.AssetUtils.extractPlatformArchPairs;
import static io.openbas.utils.SecurityCoverageUtils.getExternalIds;

import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.injectors.manual.ManualContract;
import io.openbas.rest.attack_pattern.service.AttackPatternService;
import io.openbas.rest.cve.service.CveService;
import io.openbas.rest.inject.service.InjectAssistantService;
import io.openbas.rest.inject.service.InjectService;
import io.openbas.rest.injector_contract.InjectorContractService;
import io.openbas.service.AssetGroupService;
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

  public static final int TARGET_NUMBER_OF_INJECTS = 1;

  private final InjectService injectService;
  private final InjectAssistantService injectAssistantService;
  private final AttackPatternService attackPatternService;
  private final CveService vulnerabilityService;
  private final AssetGroupService assetGroupService;
  private final InjectorContractService injectorContractService;

  private final InjectRepository injectRepository;

  /**
   * Creates and manages injects for the given scenario based on the associated security coverage.
   *
   * @param scenario the scenario for which injects are managed
   * @param securityCoverage the related security coverage providing AttackPattern references
   * @return list injects related to this scenario
   */
  public Set<Inject> createdInjectsForScenarioAndSecurityCoverage(
      Scenario scenario, SecurityCoverage securityCoverage) {
    // 1. Remove all inject placeholders
    cleanInjectPlaceholders(scenario.getId());

    // 2. Fetch asset groups via tag rules
    Set<AssetGroup> assetGroups = assetGroupService.fetchAssetGroupsFromScenarioTagRules(scenario);

    // 3. Get all endpoints per asset group
    Map<AssetGroup, List<Endpoint>> assetsFromGroupMap =
        assetGroupService.assetsFromAssetGroupMap(new ArrayList<>(assetGroups));

    // 4. Fetch InjectorContract to use for inject placeholder
    InjectorContract contractForInjectPlaceholders =
        injectorContractService.injectorContract(ManualContract.MANUAL_DEFAULT);

    // 5. Build injects from Vulnerabilities
    getInjectsByVulnerabilities(
        scenario,
        securityCoverage.getVulnerabilitiesRefs(),
        assetsFromGroupMap,
        contractForInjectPlaceholders);

    // 6. Build injects from Attack Patterns
    getInjectsRelatedToAttackPatterns(
        scenario,
        securityCoverage.getAttackPatternRefs(),
        assetsFromGroupMap,
        contractForInjectPlaceholders);

    return injectRepository.findByScenarioId(scenario.getId());
  }

  private void cleanInjectPlaceholders(String scenarioId) {
    injectRepository.deleteAllByScenarioIdAndInjectorContract(
        ManualContract.MANUAL_DEFAULT, scenarioId);
  }

  /**
   * Create injects for the given scenario based on the associated security coverage and
   * vulnerability refs.
   *
   * <p>Steps:
   *
   * <ul>
   *   <li>Resolves internal vulnerabilities from the coverage
   *   <li>Remove all inject from scenario where vulnerability refs is empty
   *   <li>Generates injects based on injector contract related to these vulnerabilities
   * </ul>
   *
   * @param scenario the scenario for which injects are managed
   * @param vulnerabilityRefs the related security coverage providing AttackPattern references
   * @return list injects related to this scenario
   */
  private void getInjectsByVulnerabilities(
      Scenario scenario,
      Set<StixRefToExternalRef> vulnerabilityRefs,
      Map<AssetGroup, List<Endpoint>> assetGroupListMap,
      InjectorContract contractForPlaceholder) {
    // 1. Fetch internal Ids for Vulnerabilities
    Set<Cve> vulnerabilities =
        vulnerabilityService.getVulnerabilitiesByExternalIds(getExternalIds(vulnerabilityRefs));

    // 2. List of injects is cleaned
    injectRepository.deleteAllInjectsWithVulnerableContractsByScenarioId(scenario.getId());

    // 3. Now, injects are created with injectorContracts related to these vulnerabilities
    injectAssistantService.generateInjectsWithTargetsByVulnerabilities(
        scenario,
        vulnerabilities,
        assetGroupListMap,
        TARGET_NUMBER_OF_INJECTS,
        contractForPlaceholder);
  }

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
   * @param attackPatternRefs the related security coverage providing AttackPattern references
   * @return list injects related to this scenario
   */
  private void getInjectsRelatedToAttackPatterns(
      Scenario scenario,
      Set<StixRefToExternalRef> attackPatternRefs,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      InjectorContract contractForPlaceholder) {

    // 1. Fetch internal Ids for AttackPatterns
    Map<String, AttackPattern> attackPatterns =
        attackPatternService.fetchInternalAttackPatternIds(attackPatternRefs);

    // 2. Remove Inject with contract related to attack patterns if attackPattern is empty
    if (attackPatterns.isEmpty()) {
      injectRepository.deleteAllInjectsWithAttackPatternContractsByScenarioId(scenario.getId());
      return;
    }

    // 3. Fetch Inject coverage
    Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap =
        injectService.extractCombinationAttackPatternPlatformArchitecture(scenario);

    // Check if assetgroups are empties because it could reduce the code
    boolean assetGroupsAreEmpties =
        assetsFromGroupMap.isEmpty()
            || assetsFromGroupMap.values().stream().allMatch(List::isEmpty);
    if (assetGroupsAreEmpties) {
      handleNoAssetGroupsCase(scenario, attackPatterns, injectCoverageMap, contractForPlaceholder);
    } else {
      handleWithAssetGroupsCase(
          scenario, assetsFromGroupMap, attackPatterns, injectCoverageMap, contractForPlaceholder);
    }
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
      Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap,
      InjectorContract contractForPlaceholder) {
    Set<String> coveredAttackPatterns =
        injectCoverageMap.values().stream()
            .flatMap(Set::stream)
            .map(Triple::getLeft)
            .collect(Collectors.toSet());

    // 4. Remove AttackPatterns already covered
    Set<String> requiredAttackPatternIds = requiredAttackPatterns.keySet();

    Set<String> missingAttackPatterns = new HashSet<>(requiredAttackPatternIds);
    missingAttackPatterns.removeAll(coveredAttackPatterns);

    // 5. Remove injects not in requiredAttackPatterns
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

    // 6. Generate missing injects only for missing AttackPatterns and relevant asset groups
    if (!missingAttackPatterns.isEmpty()) {
      Set<AttackPattern> missingAttacks =
          missingAttackPatterns.stream()
              .map(requiredAttackPatterns::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());

      injectAssistantService.generateInjectsByAttackPatternsWithoutAssetGroups(
          scenario, missingAttacks, TARGET_NUMBER_OF_INJECTS, contractForPlaceholder);
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
      Map<Inject, Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>> injectCoverageMap,
      InjectorContract contractForPlaceholder) {

    // 4. Compute all (Platform, Arch) configs across all endpoints
    List<Endpoint> endpoints = assetsFromGroupMap.values().stream().flatMap(List::stream).toList();
    Set<Pair<Endpoint.PLATFORM_TYPE, String>> allPlatformArchs =
        extractPlatformArchPairs(endpoints);

    // 5. Build required (AttackPattern × Platform × Arch) combinations
    Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> requiredCombinations =
        buildCombinationAttackPatternPlatformArchitecture(
            attackPatterns.keySet(), allPlatformArchs);

    // 6. Extract covered combinations from existing injects
    Set<Triple<String, Endpoint.PLATFORM_TYPE, String>> coveredCombinations =
        injectCoverageMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

    // 7. Identify injects to delete: if all their combinations are irrelevant
    // 8. Delete injects
    removeInjectsNoLongerNecessary(injectCoverageMap, requiredCombinations);

    // 9. Compute missing combinations
    // 10. Filter AttackPatterns that are still missing
    // 11. Filter AssetGroups based on missing (Platform × Arch)
    MissingCombinations missingCombinations =
        getMissingCombinations(requiredCombinations, coveredCombinations, assetsFromGroupMap);

    // 12. Generate missing injects only for missing AttackPatterns and relevant asset groups
    if (!missingCombinations.filteredAttackPatterns().isEmpty()) {
      Set<AttackPattern> missingAttacks =
          missingCombinations.filteredAttackPatterns().stream()
              .map(attackPatterns::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());

      injectAssistantService.generateInjectsByAttackPatternsWithAssetGroups(
          scenario,
          missingAttacks,
          TARGET_NUMBER_OF_INJECTS,
          missingCombinations.filteredAssetsFromGroupMap(),
          contractForPlaceholder);
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
                    extractPlatformArchPairs(entry.getValue()).stream()
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
