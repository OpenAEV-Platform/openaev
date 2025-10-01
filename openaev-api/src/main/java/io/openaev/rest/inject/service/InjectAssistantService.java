package io.openaev.rest.inject.service;

import static io.openaev.utils.AssetUtils.mapEndpointsByPlatformArch;
import static java.util.Collections.emptyList;

import io.openaev.database.helper.InjectorContractRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exception.UnprocessableContentException;
import io.openaev.rest.inject.form.InjectAssistantInput;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class InjectAssistantService {

  public static final int MAX_NUMBER_INJECTS = 5;

  private final InjectorContractRepositoryHelper injectorContractRepositoryHelper;
  private final AssetGroupService assetGroupService;
  private final EndpointService endpointService;
  private final InjectService injectService;
  private final AttackPatternService attackPatternService;
  private final InjectorContractService injectorContractService;

  private final InjectRepository injectRepository;
  private final InjectorContractRepository injectorContractRepository;

  // -- Used in Assistance AI

  /**
   * Generate injects for a given scenario based on the provided input.
   *
   * @param scenario the scenario for which injects are generated
   * @param input the input containing details for inject generation, such as attack pattern IDs,
   *     asset IDs, asset group IDs, and the number of injects per AttackPattern
   * @return a list of generated injects
   */
  public List<Inject> generateInjectsForScenario(Scenario scenario, InjectAssistantInput input) {
    if (input.getInjectByTTPNumber() > MAX_NUMBER_INJECTS) {
      throw new UnsupportedOperationException(
          "Number of inject by Attack Pattern must be less than or equal to 5");
    }
    List<Endpoint> endpoints = this.endpointService.endpoints(input.getAssetIds());
    List<AssetGroup> assetGroups = this.assetGroupService.assetGroups(input.getAssetGroupIds());

    Map<AssetGroup, List<Endpoint>> assetsFromGroupMap =
        assetGroupService.assetsFromAssetGroupMap(assetGroups);

    InjectorContract contractForPlaceholder =
        injectorContractService.injectorContract(ManualContract.MANUAL_DEFAULT);

    // Process injects generation for each attack pattern
    List<Inject> injects = new ArrayList<>();
    input
        .getAttackPatternIds()
        .forEach(
            attackPatternId -> {
              try {
                Set<Inject> injectsToAdd =
                    this.generateInjectsByAttackPatternId(
                        attackPatternId,
                        endpoints,
                        assetsFromGroupMap,
                        input.getInjectByTTPNumber(),
                        contractForPlaceholder);
                injects.addAll(injectsToAdd);
              } catch (UnprocessableContentException e) {
                throw new UnsupportedOperationException(e);
              }
            });

    for (Inject inject : injects) {
      inject.setScenario(scenario);
    }

    return injects;
  }

  /**
   * Generates injects based on the provided attack pattern ID, endpoints, asset groups, and the
   * number of injects to create for each AttackPattern.
   *
   * @param attackPatternId the internal ID of the attack pattern to generate injects for
   * @param endpoints the list of endpoints to consider for the injects
   * @param assetsFromGroupMap the list of asset groups to consider for the injects
   * @param injectsPerAttackPattern the maximum number of injects to create for each AttackPattern
   * @return a set of generated injects
   */
  private Set<Inject> generateInjectsByAttackPatternId(
      String attackPatternId,
      List<Endpoint> endpoints,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Integer injectsPerAttackPattern,
      InjectorContract contractForPlaceholder)
      throws UnprocessableContentException {

    // Check if attack pattern exist
    AttackPattern attackPattern = attackPatternService.findById(attackPatternId);

    Set<Inject> injects =
        buildInjectsBasedOnAttackPatternsAndAssetsAndAssetGroups(
            attackPattern,
            endpoints,
            assetsFromGroupMap,
            injectsPerAttackPattern,
            contractForPlaceholder);

    if (injects.isEmpty()) {
      throw new UnprocessableContentException("No target found");
    }
    return injects;
  }

  // -- Used in Stix Import

  /**
   * Generates injects for the given scenario and set of attack patterns, without considering asset
   * groups or endpoint platform/architecture.
   *
   * <p>This method assumes no platform or architecture constraints and tries to generate injects
   * using any compatible injector contract, or falls back to a generic manual inject.
   *
   * @param scenario the scenario to which the injects belong
   * @param attackPatterns the set of attack patterns (AttackPatterns) to generate injects for
   * @param injectsPerAttackPattern the number of injects to generate per AttackPattern
   * @return the list of created and saved injects
   */
  public List<Inject> generateInjectsByAttackPatternsWithoutAssetGroups(
      Scenario scenario,
      Set<AttackPattern> attackPatterns,
      Integer injectsPerAttackPattern,
      InjectorContract contractForPlaceholder) {
    return attackPatterns.stream()
        .flatMap(
            attackPattern ->
                buildInjectsForAnyPlatformAndArchitecture(
                    injectsPerAttackPattern, attackPattern, contractForPlaceholder)
                    .stream())
        .peek(inject -> inject.setScenario(scenario))
        .toList();
  }

  /**
   * Generates injects for the given scenario and attack patterns, using the specified asset groups
   * and their endpoints to guide platform and architecture selection.
   *
   * @param scenario the scenario to which the injects belong
   * @param attackPatterns the set of attack patterns (AttackPatterns) to generate injects for
   * @param injectsPerAttackPattern the number of injects to generate per AttackPattern
   * @param assetsFromGroupMap a mapping of asset groups to their associated endpoints
   * @return the set of created and saved injects
   * @throws UnsupportedOperationException if inject creation fails due to unprocessable content
   */
  public Set<Inject> generateInjectsByAttackPatternsWithAssetGroups(
      Scenario scenario,
      Set<AttackPattern> attackPatterns,
      Integer injectsPerAttackPattern,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      InjectorContract contractForPlaceholder) {
    Set<Inject> injects = new HashSet<>();

    for (AttackPattern attackPattern : attackPatterns) {
      try {
        Set<Inject> injectsToAdd =
            this.generateInjectsForSingleAttackPatternWithAssetGroups(
                attackPattern, assetsFromGroupMap, injectsPerAttackPattern, contractForPlaceholder);
        injectsToAdd.forEach(inject -> inject.setScenario(scenario));
        injects.addAll(injectsToAdd);
      } catch (UnprocessableContentException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    return injects;
  }

  /**
   * Generates injects for a single attack pattern (AttackPattern), based on the provided asset
   * groups and their associated endpoints.
   *
   * <p>First attempts to use injector contracts that support all required platform-architecture
   * pairs. If not found, performs a deeper search across endpoints and asset groups for matching
   * contracts.
   *
   * @param attackPattern the attack pattern to generate injects for
   * @param assetsFromGroupMap a mapping of asset groups to their associated endpoints
   * @param injectsPerAttackPattern the number of injects to generate
   * @return the set of injects generated for the given attack pattern
   * @throws UnprocessableContentException if no valid inject configuration can be found
   */
  private Set<Inject> generateInjectsForSingleAttackPatternWithAssetGroups(
      AttackPattern attackPattern,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Integer injectsPerAttackPattern,
      InjectorContract contractForPlaceholder)
      throws UnprocessableContentException {

    // Computing best case (with all possible platforms and architecture)
    List<Endpoint> NO_ENDPOINTS = new ArrayList<>();
    return buildInjectsBasedOnAttackPatternsAndAssetsAndAssetGroups(
        attackPattern,
        NO_ENDPOINTS,
        assetsFromGroupMap,
        injectsPerAttackPattern,
        contractForPlaceholder);
  }

  /**
   * Attempts to generate injects for a given attack pattern (AttackPattern) without restricting to
   * specific platforms or architectures.
   *
   * <p>If any compatible injector contracts exist, they are used. Otherwise, a generic manual
   * inject is created using default values "ANY" for platform and architecture.
   *
   * @param injectsPerAttackPattern the number of injects to generate
   * @param attackPattern the attack pattern to generate injects for
   * @return the list of generated injects
   */
  private List<Inject> buildInjectsForAnyPlatformAndArchitecture(
      Integer injectsPerAttackPattern,
      AttackPattern attackPattern,
      InjectorContract contractForPlaceholder) {
    List<InjectorContract> injectorContracts =
        this.injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(), emptyList(), injectsPerAttackPattern);

    if (!injectorContracts.isEmpty()) {
      return injectorContracts.stream()
          .map(
              ic ->
                  injectService.buildTechnicalInject(
                      ic, attackPattern.getExternalId(), attackPattern.getName()))
          .toList();
    }
    return List.of(
        buildManualInject(
            contractForPlaceholder,
            attackPattern.getExternalId(),
            "[any platform]",
            "[any architecture]"));
  }

  // -- Vulnerabilities --

  /**
   * Generates injects for the given scenario and vulnerabilities
   *
   * @param scenario the scenario to which the injects belong
   * @param vulnerabilities the set of Vulnerabilities (CVEs) to generate injects for
   * @param assetGroupListMap assets and associated endpoints involved in the automatic assignment
   * @param injectsPerVulnerability the number of injects to generate per Vulnerability
   * @param contractForPlaceholder contract to use for placeholder injects
   * @return the list of injects to create
   */
  public List<Inject> generateInjectsWithTargetsByVulnerabilities(
      Scenario scenario,
      Set<Cve> vulnerabilities,
      Map<AssetGroup, List<Endpoint>> assetGroupListMap,
      int injectsPerVulnerability,
      InjectorContract contractForPlaceholder) {

    Map<String, Set<InjectorContract>> mapVulnerabilityInjectorContract =
        computeMapVulnerabilityInjectorContracts(vulnerabilities, injectsPerVulnerability);

    return vulnerabilities.stream()
        .flatMap(
            vulnerability ->
                buildInjectsWithTargetsByVulnerability(
                    vulnerability,
                    mapVulnerabilityInjectorContract.getOrDefault(
                        vulnerability.getExternalId().toLowerCase(), Set.of()),
                    assetGroupListMap,
                    contractForPlaceholder)
                    .stream())
        .peek(inject -> inject.setScenario(scenario))
        .toList();
  }

  private Map<String, Set<InjectorContract>> computeMapVulnerabilityInjectorContracts(
      Set<Cve> vulnerabilities, int injectsPerVulnerability) {
    Set<String> vulnerabilityExternalIds =
        vulnerabilities.stream()
            .map(Cve::getExternalId)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

    Set<InjectorContract> contracts =
        injectorContractRepository.findInjectorContractsByVulnerabilityIdIn(
            vulnerabilityExternalIds, injectsPerVulnerability);

    Map<String, Set<InjectorContract>> mapVulnerabilityInjectorContract = new HashMap<>();

    contracts.forEach(
        contract -> {
          contract.getVulnerabilities().stream()
              .map(v -> v.getExternalId().toLowerCase())
              .filter(vulnerabilityExternalIds::contains)
              .forEach(
                  vulnId ->
                      mapVulnerabilityInjectorContract
                          .computeIfAbsent(vulnId, k -> new HashSet<>())
                          .add(contract));
        });
    return mapVulnerabilityInjectorContract;
  }

  /**
   * Builds a set of {@link Inject} objects for a given vulnerability
   *
   * @param vulnerability the {@link Cve} vulnerability to generate injects for
   * @param injectorContracts related to this vulnerability
   * @param assetGroupListMap assets and associated endpoints involved in the automatic assignment
   * @param contractForPlaceholder contract to use for placeholder injects
   * @return a set of generated {@link Inject} objects, never {@code null}
   */
  private Set<Inject> buildInjectsWithTargetsByVulnerability(
      Cve vulnerability,
      Set<InjectorContract> injectorContracts,
      Map<AssetGroup, List<Endpoint>> assetGroupListMap,
      InjectorContract contractForPlaceholder) {

    if (injectorContracts.isEmpty()) {
      return Set.of(
          buildManualInject(contractForPlaceholder, vulnerability.getExternalId(), null, null));
    }

    Set<Inject> injects = new HashSet<>();
    for (InjectorContract ic : injectorContracts) {
      Inject inject =
          injectService.buildTechnicalInject(
              ic, vulnerability.getExternalId(), vulnerability.getCisaVulnerabilityName());
      // Set the targets in the inject based on the field types in the contract's content.fields.
      // Fields of type "asset-group" take priority, because tag rules are directly associated with
      // asset groups.
      // If no "asset-group" fields are present, we flatten the endpoints from the asset groups and
      // set them as assets in the inject.
      injectService.assignAssetGroup(inject, assetGroupListMap.keySet().stream().toList());
      injects.add(inject);
    }
    return injects;
  }

  private Set<Inject> buildInjectsBasedOnAttackPatternsAndAssetsAndAssetGroups(
      AttackPattern attackPattern,
      List<Endpoint> endpoints,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Integer injectsPerAttackPattern,
      InjectorContract contractForPlaceholder) {

    // Try best case (all possible platform/arch combinations)
    Set<Inject> bestCase =
        buildInjectsForAllPlatformAndArchCombinations(
            endpoints,
            new ArrayList<>(assetsFromGroupMap.keySet()),
            injectsPerAttackPattern,
            attackPattern);

    if (!bestCase.isEmpty()) {
      return bestCase;
    }

    // Otherwise use contract + manual injects
    Map<InjectorContract, Inject> contractInjectMap = new HashMap<>();
    Map<String, Inject> manualInjectMap = new HashMap<>();
    List<InjectorContract> knownInjectorContracts = new ArrayList<>();

    if (!endpoints.isEmpty()) {
      handleEndpoints(
          endpoints,
          attackPattern,
          injectsPerAttackPattern,
          contractInjectMap,
          manualInjectMap,
          knownInjectorContracts,
          contractForPlaceholder);
    }

    if (!assetsFromGroupMap.isEmpty()) {
      handleAssetGroups(
          assetsFromGroupMap,
          attackPattern,
          injectsPerAttackPattern,
          contractInjectMap,
          manualInjectMap,
          knownInjectorContracts,
          contractForPlaceholder);
    }

    return Stream.concat(contractInjectMap.values().stream(), manualInjectMap.values().stream())
        .collect(Collectors.toSet());
  }

  /**
   * Attempts to generate injects using injector contracts that support all required
   * platform-architecture combinations for the given attack pattern.
   *
   * <p>If such injector contracts exist, injects are created and associated with the given asset
   * groups and endpoints. Otherwise, an empty list is returned.
   *
   * @param endpoints the list of endpoints involved (optional context for assets)
   * @param assetGroups the list of asset groups to assign to each inject
   * @param injectsPerAttackPattern the number of injects to generate
   * @param attackPattern the attack pattern to generate injects for
   * @return the set of injects, or an empty list if no contracts matched
   */
  private Set<Inject> buildInjectsForAllPlatformAndArchCombinations(
      List<Endpoint> endpoints,
      List<AssetGroup> assetGroups,
      Integer injectsPerAttackPattern,
      AttackPattern attackPattern) {
    List<String> allPlatformArchitecturePairs = Endpoint.PLATFORM_TYPE.getAllNamesAsStrings();
    List<InjectorContract> injectorContracts =
        this.injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(), allPlatformArchitecturePairs, injectsPerAttackPattern);

    return injectorContracts.stream()
        .map(
            ic -> {
              Inject inject =
                  injectService.buildTechnicalInject(
                      ic, attackPattern.getExternalId(), attackPattern.getName());
              inject.setAssetGroups(assetGroups);
              inject.setAssets(endpoints.stream().map(Asset.class::cast).toList());
              return inject;
            })
        .collect(Collectors.toSet());
  }

  /**
   * Handles the endpoints, search injector contract then create or update injects
   *
   * @param endpoints the list of endpoints to process
   * @param attackPattern the attack pattern for which the injects are created
   * @param injectsPerAttackPattern the maximum number of injects to create for each AttackPattern
   * @param contractInjectMap a map to store the injector contracts and their corresponding injects
   * @param manualInjectMap a map to store manual injects based on platform-architecture pairs
   * @param knownInjectorContracts the list of already known injector contracts
   */
  private void handleEndpoints(
      List<Endpoint> endpoints,
      AttackPattern attackPattern,
      Integer injectsPerAttackPattern,
      Map<InjectorContract, Inject> contractInjectMap,
      Map<String, Inject> manualInjectMap,
      List<InjectorContract> knownInjectorContracts,
      InjectorContract contractForPlaceholder) {
    if (endpoints.isEmpty()) {
      return;
    }
    ContractResultForEndpoints endpointResults =
        getInjectorContractForAssetsAndAttackPattern(
            attackPattern, injectsPerAttackPattern, endpoints);
    // Add matched contracts
    endpointResults.contractEndpointsMap.forEach(
        (contract, value) -> {
          Inject inject =
              contractInjectMap.computeIfAbsent(
                  contract,
                  k ->
                      injectService.buildTechnicalInject(
                          k, attackPattern.getExternalId(), attackPattern.getName()));
          inject.setAssets(value.stream().map(Asset.class::cast).toList());
        });
    // Add manual injects
    endpointResults.manualEndpoints.forEach(
        (platformArchitecture, value) -> {
          Inject inject =
              manualInjectMap.computeIfAbsent(
                  platformArchitecture,
                  key -> {
                    String[] parts = key.split(":");
                    return buildManualInject(
                        contractForPlaceholder, attackPattern.getExternalId(), parts[0], parts[1]);
                  });
          inject.setAssets(value.stream().map(Asset.class::cast).toList());
        });

    knownInjectorContracts.addAll(endpointResults.contractEndpointsMap.keySet());
  }

  /**
   * Get the injector contract for assets and AttackPattern.
   *
   * @param attackPattern the attack pattern for which the injector contract need to match
   * @param injectsPerAttackPattern the maximum number of injector contracts to return
   * @param endpoints the list of endpoints to consider for the injector contract
   * @return a ContractResultForEndpoints containing the matched injector contracts with their
   *     endpoints, and the map of platform architecture pairs with the endpoints for those that
   *     didn't find injectorContract
   */
  private ContractResultForEndpoints getInjectorContractForAssetsAndAttackPattern(
      AttackPattern attackPattern, Integer injectsPerAttackPattern, List<Endpoint> endpoints) {
    Map<InjectorContract, List<Endpoint>> contractEndpointsMap = new HashMap<>();
    Map<String, List<Endpoint>> manualEndpoints = new HashMap<>();

    // Group endpoints by platform:architecture
    Map<String, List<Endpoint>> groupedAssets = mapEndpointsByPlatformArch(endpoints);

    // Try to find injectors contract covering all platform-architecture pairs at once
    List<InjectorContract> injectorContracts =
        this.injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(),
            groupedAssets.keySet().stream().toList(),
            injectsPerAttackPattern);

    if (!injectorContracts.isEmpty()) {
      injectorContracts.forEach(ic -> contractEndpointsMap.put(ic, endpoints));
    } else {
      // Or else
      groupedAssets.forEach(
          (platformArchitecture, endpointValue) -> {
            // For each platform architecture pairs try to find injectorContracts
            List<InjectorContract> injectorContractsForGroup =
                this.injectorContractRepositoryHelper
                    .searchInjectorContractsByAttackPatternAndEnvironment(
                        attackPattern.getExternalId(),
                        List.of(platformArchitecture),
                        injectsPerAttackPattern);

            // Else take the manual injectorContract
            if (injectorContractsForGroup.isEmpty()) {
              manualEndpoints.put(platformArchitecture, endpointValue);
            } else {
              injectorContractsForGroup.forEach(ic -> contractEndpointsMap.put(ic, endpointValue));
            }
          });
    }
    return new ContractResultForEndpoints(contractEndpointsMap, manualEndpoints);
  }

  /**
   * Handles the asset groups, search injector contract then create or update injects
   *
   * @param assetsFromGroupMap Map of assetGroups with their list of endpoints
   * @param attackPattern the attack pattern for which the injects are created
   * @param injectsPerAttackPattern the maximum number of injects to create for each AttackPattern
   * @param contractInjectMap a map to store the injector contracts and their corresponding inject
   * @param manualInjectMap a map to store manual injects based on platform-architecture pairs
   * @param knownInjectorContracts the list of already known injector contracts
   */
  private void handleAssetGroups(
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      AttackPattern attackPattern,
      Integer injectsPerAttackPattern,
      Map<InjectorContract, Inject> contractInjectMap,
      Map<String, Inject> manualInjectMap,
      List<InjectorContract> knownInjectorContracts,
      InjectorContract contractForPlaceholder) {
    for (AssetGroup group : assetsFromGroupMap.keySet()) {
      List<Endpoint> assetsFromGroup = assetsFromGroupMap.get(group);

      ContractResultForAssetGroup result =
          getInjectorContractsForAssetGroupAndAttackPattern(
              assetsFromGroup, attackPattern, injectsPerAttackPattern, knownInjectorContracts);

      result.injectorContracts.forEach(
          contract -> {
            Inject inject =
                contractInjectMap.computeIfAbsent(
                    contract,
                    k ->
                        injectService.buildTechnicalInject(
                            k, attackPattern.getExternalId(), attackPattern.getName()));
            inject.getAssetGroups().add(group);
          });

      if (!result.unmatchedPlatformArchitecture.isEmpty()) {
        Inject inject =
            manualInjectMap.computeIfAbsent(
                result.unmatchedPlatformArchitecture,
                k -> {
                  String[] parts = k.split(":");
                  return buildManualInject(
                      contractForPlaceholder, attackPattern.getExternalId(), parts[0], parts[1]);
                });
        inject.getAssetGroups().add(group);
      }

      knownInjectorContracts.addAll(result.injectorContracts);
    }
  }

  /**
   * Get the injector contracts for a specific asset group and AttackPattern
   *
   * @param assetsFromGroup the assets related to group for which the injector contracts need to be
   *     found
   * @param attackPattern the attack pattern for which the injector contracts need to match
   * @param injectsPerAttackPattern the maximum number of injector contracts to return
   * @param knownInjectorContracts the list of already found injector contracts to search from
   * @return a ContractResultForAssetGroup containing the injector contracts that successfully
   *     matched for the asset group. and the most common platform-architecture pairs within the
   *     asset group for which no matching injector contract was found.
   */
  private ContractResultForAssetGroup getInjectorContractsForAssetGroupAndAttackPattern(
      List<Endpoint> assetsFromGroup,
      AttackPattern attackPattern,
      Integer injectsPerAttackPattern,
      List<InjectorContract> knownInjectorContracts) {
    String unmatchedPlatformArchitecture = "";

    // Retrieve and group all endpoints in the asset group by platform:architecture
    if (assetsFromGroup.isEmpty()) {
      // No endpoints in the asset group, return empty result
      return new ContractResultForAssetGroup(emptyList(), "");
    }
    Map<String, List<Endpoint>> groupedAssets = mapEndpointsByPlatformArch(assetsFromGroup);

    // Try to find an existing injectorsContract that cover all platform:architecture pairs from
    // this group at once
    List<InjectorContract> injectorContracts =
        findOrSearchInjectorContract(
            knownInjectorContracts,
            attackPattern,
            groupedAssets.keySet().stream().toList(),
            injectsPerAttackPattern);
    if (!injectorContracts.isEmpty()) {
      return new ContractResultForAssetGroup(injectorContracts, unmatchedPlatformArchitecture);
    }
    // Otherwise, select the most common platform-architecture group
    String mostCommonPlatformArch =
        groupedAssets.entrySet().stream()
            .max(Comparator.comparingInt(entry -> entry.getValue().size()))
            .map(Map.Entry::getKey)
            .orElse("");

    // Try to find injectors contract for the most common group
    List<InjectorContract> injectorContractsForGroup =
        findOrSearchInjectorContract(
            knownInjectorContracts,
            attackPattern,
            List.of(mostCommonPlatformArch),
            injectsPerAttackPattern);
    if (injectorContractsForGroup.isEmpty()) {
      unmatchedPlatformArchitecture = mostCommonPlatformArch;
    }
    return new ContractResultForAssetGroup(injectorContracts, unmatchedPlatformArchitecture);
  }

  /**
   * Builds a manual Inject object - also called Placeholder.
   *
   * @param identifier the AttackPattern or vulnerability to specify in the title and description of
   *     the Inject
   * @param platform the platform to specify in the title and description of the Inject
   * @param architecture the architecture to specify in the title and description of the Inject
   * @return the built manual Inject object
   */
  public Inject buildManualInject(
      InjectorContract contractForPlaceholder,
      String identifier,
      String platform,
      String architecture) {
    return injectService.buildInject(
        contractForPlaceholder,
        formatTitle(identifier, platform, architecture),
        formatDescription(identifier, platform, architecture),
        false);
  }

  private String formatTitle(String identifier, String platform, String architecture) {
    if (platform != null && architecture != null) {
      return String.format("[%s] Placeholder - %s %s", identifier, platform, architecture);
    }
    return String.format("[%s] Placeholder", identifier);
  }

  private String formatDescription(String identifier, String platform, String architecture) {
    String base = "This placeholder is disabled because the %s is currently not covered. %s";

    if (platform != null && architecture != null) {
      return String.format(
          base,
          "Attack Pattern " + identifier,
          String.format(
              "Please create the payloads for platform %s and architecture %s.",
              platform, architecture));
    } else {
      return String.format(
          base,
          "Vulnerability " + identifier,
          "Please add the contracts related to this vulnerability.");
    }
  }

  /**
   * Finds or searches in Database for injector contracts based on the provided parameters.
   *
   * @param knownInjectorContracts the list of known injector contracts to search from
   * @param attackPattern the attack pattern to match against the injector contracts
   * @param platformArchitecturePairs the list of platform-architecture pairs to filter the
   *     contracts
   * @param injectsPerAttackPattern the maximum number of injector contracts to return
   * @return a list of InjectorContract objects that match the search criteria
   */
  private List<InjectorContract> findOrSearchInjectorContract(
      List<InjectorContract> knownInjectorContracts,
      AttackPattern attackPattern,
      List<String> platformArchitecturePairs,
      Integer injectsPerAttackPattern) {
    // Find in existing list of InjectorContracts
    List<InjectorContract> existingInjectorContract =
        findInjectorContracts(
            knownInjectorContracts, platformArchitecturePairs, injectsPerAttackPattern);
    if (!existingInjectorContract.isEmpty()) {
      return existingInjectorContract;
    }
    // Else find from DB
    return this.injectorContractRepositoryHelper
        .searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(), platformArchitecturePairs, injectsPerAttackPattern);
  }

  /**
   * Finds injector contracts based on the provided list of already found injector contracts,
   *
   * @param knownInjectorContracts the list of known injector contracts to search from
   * @param platformArchitecturePairs the list of platform-architecture pairs to filter the
   *     contracts
   * @param injectsPerAttackPattern the maximum number of injector contracts to return
   * @return a list of InjectorContract objects that match the search criteria
   */
  private List<InjectorContract> findInjectorContracts(
      List<InjectorContract> knownInjectorContracts,
      List<String> platformArchitecturePairs,
      Integer injectsPerAttackPattern) {
    if (knownInjectorContracts == null
        || platformArchitecturePairs == null
        || platformArchitecturePairs.isEmpty()) {
      return emptyList();
    }

    Set<Endpoint.PLATFORM_TYPE> platforms = new HashSet<>();
    Set<String> architectures = new HashSet<>();
    for (String pair : platformArchitecturePairs) {
      String[] parts = pair.split(":");
      if (parts.length == 2) {
        platforms.add(Endpoint.PLATFORM_TYPE.valueOf(parts[0]));
        architectures.add(parts[1]);
      }
    }
    String architecture =
        architectures.size() == 1
            ? architectures.iterator().next()
            : Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES.name();

    return knownInjectorContracts.stream()
        .filter(
            ic -> {
              Set<Endpoint.PLATFORM_TYPE> icPlatformsSet =
                  Arrays.stream(ic.getPlatforms()).collect(Collectors.toSet());
              boolean hasPlatforms = icPlatformsSet.containsAll(platforms);
              boolean hasArchitecture =
                  architecture.equals(ic.getPayload().getExecutionArch().name());
              return hasPlatforms && hasArchitecture;
            })
        .limit(injectsPerAttackPattern)
        .toList();
  }

  private record ContractResultForAssetGroup(
      List<InjectorContract> injectorContracts, String unmatchedPlatformArchitecture) {}

  private record ContractResultForEndpoints(
      Map<InjectorContract, List<Endpoint>> contractEndpointsMap,
      Map<String, List<Endpoint>> manualEndpoints) {}
}
