package io.openbas.service.stix;

import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGET_PROPERTY_SELECTOR;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGET_SELECTOR;
import static java.util.Collections.emptyList;

import io.openbas.database.helper.InjectorContractRepositoryHelper;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.injector_contract.ContractTargetedProperty;
import io.openbas.rest.exception.UnprocessableContentException;
import io.openbas.rest.inject.service.InjectAssistantService;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class InjectStixAssistantService {

  private final InjectAssistantService injectAssistantService;

  private final InjectRepository injectRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final InjectorContractRepositoryHelper injectorContractRepositoryHelper;

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
  public Set<Inject> generateInjectsByAttackPatternsWithoutAssetGroups(
      Scenario scenario, Set<AttackPattern> attackPatterns, Integer injectsPerAttackPattern) {
    Set<Inject> injects =
        attackPatterns.stream()
            .flatMap(
                attackPattern ->
                    buildInjectsForAnyPlatformAndArchitecture(
                        injectsPerAttackPattern, attackPattern)
                        .stream())
            .peek(inject -> inject.setScenario(scenario))
            .collect(Collectors.toSet());

    Set<Inject> savedInjects = new HashSet<>();
    this.injectRepository.saveAll(injects).forEach(savedInjects::add);

    return savedInjects;
  }

  /**
   * Generates injects for the given scenario and attack patterns, using the specified asset groups
   * and their endpoints to guide platform and architecture selection.
   *
   * @param scenario the scenario to which the injects belong
   * @param attackPatterns the set of attack patterns (AttackPatterns) to generate injects for
   * @param injectsPerAttackPattern the number of injects to generate per AttackPattern
   * @param assetsFromGroupMap a mapping of asset groups to their associated endpoints
   * @return the list of created and saved injects
   * @throws UnsupportedOperationException if inject creation fails due to unprocessable content
   */
  public Set<Inject> generateInjectsByAttackPatternsWithAssetGroups(
      Scenario scenario,
      Set<AttackPattern> attackPatterns,
      Integer injectsPerAttackPattern,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap) {
    Set<Inject> injects = new HashSet<>();

    for (AttackPattern attackPattern : attackPatterns) {
      try {
        Set<Inject> injectsToAdd =
            this.generateInjectsForSingleAttackPatternWithAssetGroups(
                attackPattern, assetsFromGroupMap, injectsPerAttackPattern);
        injectsToAdd.forEach(inject -> inject.setScenario(scenario));
        injects.addAll(injectsToAdd);
      } catch (UnprocessableContentException e) {
        throw new UnsupportedOperationException(e);
      }
    }

    Set<Inject> savedInjects = new HashSet<>();
    this.injectRepository.saveAll(injects).forEach(savedInjects::add);

    return savedInjects;
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
   * @return the list of injects generated for the given attack pattern
   * @throws UnprocessableContentException if no valid inject configuration can be found
   */
  private Set<Inject> generateInjectsForSingleAttackPatternWithAssetGroups(
      AttackPattern attackPattern,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Integer injectsPerAttackPattern)
      throws UnprocessableContentException {

    // Computing best case (with all possible platforms and architecture)
    List<Endpoint> NO_ENDPOINTS = new ArrayList<>();
    Set<Inject> bestCaseInjects =
        injectAssistantService.buildInjectsForAllPlatformAndArchCombinations(
            NO_ENDPOINTS,
            new ArrayList<>(assetsFromGroupMap.keySet()),
            injectsPerAttackPattern,
            attackPattern);

    if (!bestCaseInjects.isEmpty()) {
      return bestCaseInjects;
    }

    // Otherwise, process for all endpoints and all assetgroups to find injector contract that
    // match with AttackPattern and platforms/architectures
    Map<InjectorContract, Inject> contractInjectMap = new HashMap<>();
    Map<String, Inject> manualInjectMap = new HashMap<>();
    List<InjectorContract> knownInjectorContracts = new ArrayList<>();

    injectAssistantService.handleAssetGroups(
        assetsFromGroupMap,
        attackPattern,
        injectsPerAttackPattern,
        contractInjectMap,
        manualInjectMap,
        knownInjectorContracts);

    return Stream.concat(contractInjectMap.values().stream(), manualInjectMap.values().stream())
        .collect(Collectors.toSet());
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
      Integer injectsPerAttackPattern, AttackPattern attackPattern) {
    List<InjectorContract> injectorContracts =
        this.injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
            attackPattern.getExternalId(), emptyList(), injectsPerAttackPattern);

    if (!injectorContracts.isEmpty()) {
      return injectorContracts.stream()
          .map(
              ic ->
                  injectAssistantService.buildTechnicalInjectFromInjectorContract(
                      ic, attackPattern.getExternalId(), attackPattern.getName()))
          .toList();
    }
    return List.of(
        injectAssistantService.buildManualInjectDefaultPlatformAndArchitecture(
            attackPattern.getExternalId()));
  }

  // -- Vulnerabilities --

  /**
   * Generates injects for the given scenario and vulnerabilities
   *
   * @param scenario the scenario to which the injects belong
   * @param vulnerabilities the set of Vulnerabilities (Cves) to generate injects for
   * @param assetsByTargetProperty map assets by target property
   * @param injectsPerVulnerability the number of injects to generate per Vulnerability
   * @return the list of created and saved injects
   */
  public Set<Inject> generateInjectsWithTargetsByVulnerabilities(
      Scenario scenario,
      Set<Cve> vulnerabilities,
      Map<ContractTargetedProperty, Set<Endpoint>> assetsByTargetProperty,
      int injectsPerVulnerability) {
    Set<Inject> injects =
        vulnerabilities.stream()
            .flatMap(
                vulnerability ->
                    buildInjectsWithTargetsByVulnerability(
                        vulnerability, injectsPerVulnerability, assetsByTargetProperty)
                        .stream())
            .peek(inject -> inject.setScenario(scenario))
            .collect(Collectors.toSet());

    Set<Inject> savedInjects = new HashSet<>();
    this.injectRepository.saveAll(injects).forEach(savedInjects::add);

    return savedInjects;
  }

  /**
   * Builds a set of {@link Inject} objects for a given vulnerability, using the provided target
   * assets (grouped by their {@link ContractTargetedProperty}).
   *
   * @param vulnerability the {@link Cve} vulnerability to generate injects for
   * @param injectsPerVulnerability maximum number of injects allowed per vulnerability
   * @param assetsByTargetProperty mapping of target properties to sets of {@link Endpoint} assets;
   *     may be empty if no assets are available
   * @return a set of generated {@link Inject} objects, never {@code null}
   */
  private Set<Inject> buildInjectsWithTargetsByVulnerability(
      Cve vulnerability,
      Integer injectsPerVulnerability,
      Map<ContractTargetedProperty, Set<Endpoint>> assetsByTargetProperty) {

    Set<InjectorContract> injectorContracts =
        injectorContractRepository.findInjectorContractsByVulnerabilityId(
            vulnerability.getExternalId(), injectsPerVulnerability);

    if (injectorContracts.isEmpty()) {
      // No injector contracts found -> return manual inject
      return Set.of(
          injectAssistantService.buildManualInjectDefaultPlatformAndArchitecture(
              vulnerability.getExternalId()));
    }

    Set<Inject> injects = new HashSet<>();
    for (InjectorContract ic : injectorContracts) {
      if (!assetsByTargetProperty.isEmpty()) { // If there are no assets
        injects.addAll(buildInjectsFromAssets(ic, vulnerability, assetsByTargetProperty));
      } else {
        injects.add(buildInjectWithoutAssets(ic, vulnerability));
      }
    }
    return injects;
  }

  private Set<Inject> buildInjectsFromAssets(
      InjectorContract ic,
      Cve vulnerability,
      Map<ContractTargetedProperty, Set<Endpoint>> assetsByTargetProperty) {

    Set<Inject> injects = new HashSet<>();
    for (Map.Entry<ContractTargetedProperty, Set<Endpoint>> entry :
        assetsByTargetProperty.entrySet()) {
      Inject inject =
          injectAssistantService.buildTechnicalInjectFromInjectorContract(
              ic, vulnerability.getExternalId(), vulnerability.getCisaVulnerabilityName());

      configureInject(inject, entry.getKey(), new ArrayList<>(entry.getValue()));
      injects.add(inject);
    }
    return injects;
  }

  private Inject buildInjectWithoutAssets(InjectorContract ic, Cve vulnerability) {
    Inject inject =
        injectAssistantService.buildTechnicalInjectFromInjectorContract(
            ic, vulnerability.getExternalId(), vulnerability.getCisaVulnerabilityName());

    configureInject(inject, ContractTargetedProperty.hostname, new ArrayList<>()); // no assets
    return inject;
  }

  /** Configures an {@link Inject} with assets and target property metadata. */
  private void configureInject(
      Inject inject, ContractTargetedProperty targetProperty, List<Endpoint> assets) {
    inject.setAssets(new ArrayList<>(assets));
    inject
        .getContent()
        .put(CONTRACT_ELEMENT_CONTENT_KEY_TARGET_PROPERTY_SELECTOR, targetProperty.name());
    inject
        .getContent()
        .put(CONTRACT_ELEMENT_CONTENT_KEY_TARGET_SELECTOR, CONTRACT_ELEMENT_CONTENT_KEY_ASSETS);
  }
}
