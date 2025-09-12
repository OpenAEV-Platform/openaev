package io.openbas.rest.inject.service;

import static java.util.Collections.emptyList;

import io.openbas.database.helper.InjectorContractRepositoryHelper;
import io.openbas.database.model.*;
import io.openbas.injectors.manual.ManualContract;
import io.openbas.rest.injector_contract.InjectorContractContentUtils;
import io.openbas.rest.injector_contract.InjectorContractService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class InjectAssistantService {

  private final InjectorContractService injectorContractService;
  private final InjectorContractRepositoryHelper injectorContractRepositoryHelper;

  private InjectorContract manualInjectorContract = null;

  /**
   * Builds an Inject object based on the provided InjectorContract, title, description and enabled
   *
   * @param injectorContract the InjectorContract associated with the Inject
   * @param title the title of the Inject
   * @param description the description of the Inject
   * @param enabled indicates whether the Inject is enabled or not
   * @return the inject object built
   */
  public Inject buildInject(
      InjectorContract injectorContract, String title, String description, Boolean enabled) {
    Inject inject = new Inject();
    inject.setTitle(title);
    inject.setDescription(description);
    inject.setInjectorContract(injectorContract);
    inject.setDependsDuration(0L);
    inject.setEnabled(enabled);
    inject.setContent(
        InjectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    return inject;
  }

  /**
   * Builds a technical Inject object from the provided InjectorContract and AttackPattern.
   *
   * @param injectorContract the InjectorContract to build the Inject from
   * @param identifier the AttackPattern or Vulnerability associated with the Inject
   * @param name the AttackPattern or Vulnerability associated with the Inject
   * @return the built Inject object
   */
  public Inject buildTechnicalInjectFromInjectorContract(
      InjectorContract injectorContract, String identifier, String name) {
    return buildInject(
        injectorContract,
        String.format("[%s] %s - %s", identifier, name, injectorContract.getLabels().get("en")),
        null,
        true);
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
  public Inject buildManualInject(String identifier, String platform, String architecture) {
    if (manualInjectorContract == null) {
      manualInjectorContract =
          this.injectorContractService.injectorContract(ManualContract.MANUAL_DEFAULT);
    }
    return buildInject(
        manualInjectorContract,
        String.format("[%s] Placeholder - %s %s", identifier, platform, architecture),
        String.format(
            "This placeholder is disabled because the AttackPattern %s with platform %s and architecture %s is currently not covered. Please create the payloads for the missing AttackPattern.",
            identifier, platform, architecture),
        false);
  }

  /**
   * Builds a manual Inject with default platform and architecture in the title
   *
   * @param identifier the AttackPattern or vulnerability to specify in the title and description of
   *     the Inject
   * @return the built manual Inject object
   */
  public Inject buildManualInjectDefaultPlatformAndArchitecture(String identifier) {
    return buildManualInject(identifier, "[any platform]", "[any architecture]");
  }

  /**
   * Get all platform-architecture pairs that are supported by the system.
   *
   * @return a list of all platform-architecture pairs
   */
  @NotNull
  private List<String> getAllPlatform() {
    List<String> allPlatformArchitecturePairs = new ArrayList<>();
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.Linux.name());
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.MacOS.name());
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.Windows.name());
    return allPlatformArchitecturePairs;
  }

  /**
   * Groups endpoints by their platform and architecture.
   *
   * @param endpoints the list of endpoints to group
   * @return a map where the key is a string combining platform and architecture, and the value is a
   *     list of endpoints that match that platform-architecture pair
   */
  public Map<String, List<Endpoint>> groupEndpointsByPlatformAndArchitecture(
      List<Endpoint> endpoints) {
    return endpoints.stream()
        .collect(
            Collectors.groupingBy(endpoint -> endpoint.getPlatform() + ":" + endpoint.getArch()));
  }

  private record ContractResultForEndpoints(
      Map<InjectorContract, List<Endpoint>> contractEndpointsMap,
      Map<String, List<Endpoint>> manualEndpoints) {}

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
    Map<String, List<Endpoint>> groupedAssets =
        this.groupEndpointsByPlatformAndArchitecture(endpoints);

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

  private record ContractResultForAssetGroup(
      List<InjectorContract> injectorContracts, String unmatchedPlatformArchitecture) {}

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
    Map<String, List<Endpoint>> groupedAssets =
        this.groupEndpointsByPlatformAndArchitecture(assetsFromGroup);

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
   * Handles the endpoints, search injector contract then create or update injects
   *
   * @param endpoints the list of endpoints to process
   * @param attackPattern the attack pattern for which the injects are created
   * @param injectsPerAttackPattern the maximum number of injects to create for each AttackPattern
   * @param contractInjectMap a map to store the injector contracts and their corresponding injects
   * @param manualInjectMap a map to store manual injects based on platform-architecture pairs
   * @param knownInjectorContracts the list of already known injector contracts
   */
  public void handleEndpoints(
      List<Endpoint> endpoints,
      AttackPattern attackPattern,
      Integer injectsPerAttackPattern,
      Map<InjectorContract, Inject> contractInjectMap,
      Map<String, Inject> manualInjectMap,
      List<InjectorContract> knownInjectorContracts) {
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
                      buildTechnicalInjectFromInjectorContract(
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
                    return buildManualInject(attackPattern.getExternalId(), parts[0], parts[1]);
                  });
          inject.setAssets(value.stream().map(Asset.class::cast).toList());
        });

    knownInjectorContracts.addAll(endpointResults.contractEndpointsMap.keySet());
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
  public void handleAssetGroups(
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      AttackPattern attackPattern,
      Integer injectsPerAttackPattern,
      Map<InjectorContract, Inject> contractInjectMap,
      Map<String, Inject> manualInjectMap,
      List<InjectorContract> knownInjectorContracts) {
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
                        buildTechnicalInjectFromInjectorContract(
                            k, attackPattern.getExternalId(), attackPattern.getName()));
            inject.getAssetGroups().add(group);
          });

      if (!result.unmatchedPlatformArchitecture.isEmpty()) {
        Inject inject =
            manualInjectMap.computeIfAbsent(
                result.unmatchedPlatformArchitecture,
                k -> {
                  String[] parts = k.split(":");
                  return buildManualInject(attackPattern.getExternalId(), parts[0], parts[1]);
                });
        inject.getAssetGroups().add(group);
      }

      knownInjectorContracts.addAll(result.injectorContracts);
    }
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
   * @return the list of injects, or an empty list if no contracts matched
   */
  public Set<Inject> buildInjectsForAllPlatformAndArchCombinations(
      List<Endpoint> endpoints,
      List<AssetGroup> assetGroups,
      Integer injectsPerAttackPattern,
      AttackPattern attackPattern) {
    List<String> allPlatformArchitecturePairs = getAllPlatform();
    List<InjectorContract> injectorContracts =
        this.injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(), allPlatformArchitecturePairs, injectsPerAttackPattern);

    return injectorContracts.stream()
        .map(
            ic -> {
              Inject inject =
                  buildTechnicalInjectFromInjectorContract(
                      ic, attackPattern.getExternalId(), attackPattern.getName());
              inject.setAssetGroups(assetGroups);
              inject.setAssets(endpoints.stream().map(Asset.class::cast).toList());
              return inject;
            })
        .collect(Collectors.toSet());
  }
}
