package io.openbas.rest.inject.service;

import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.rest.attack_pattern.service.AttackPatternService;
import io.openbas.rest.exception.UnprocessableContentException;
import io.openbas.rest.inject.form.InjectAssistantInput;
import io.openbas.service.AssetGroupService;
import io.openbas.service.EndpointService;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class InjectAIAssistantService {

  private final AssetGroupService assetGroupService;
  private final EndpointService endpointService;
  private final AttackPatternService attackPatternService;
  private final InjectAssistantService injectAssistantService;

  private final InjectRepository injectRepository;

  /**
   * Generate injects for a given scenario based on the provided input.
   *
   * @param scenario the scenario for which injects are generated
   * @param input the input containing details for inject generation, such as attack pattern IDs,
   *     asset IDs, asset group IDs, and the number of injects per TTP
   * @return a list of generated injects
   */
  public Set<Inject> generateInjectsForScenario(Scenario scenario, InjectAssistantInput input) {
    if (input.getInjectByTTPNumber() > 5) {
      throw new UnsupportedOperationException(
          "Number of inject by Attack Pattern must be less than or equal to 5");
    }
    List<Endpoint> endpoints = this.endpointService.endpoints(input.getAssetIds());
    List<AssetGroup> assetGroups = this.assetGroupService.assetGroups(input.getAssetGroupIds());

    Map<AssetGroup, List<Endpoint>> assetsFromGroupMap =
        assetGroupService.assetsFromAssetGroupMap(assetGroups);

    // Process injects generation for each attack pattern
    Set<Inject> injects = new HashSet<>();
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
                        input.getInjectByTTPNumber());
                injects.addAll(injectsToAdd);
              } catch (UnprocessableContentException e) {
                throw new UnsupportedOperationException(e);
              }
            });

    for (Inject inject : injects) {
      inject.setScenario(scenario);
    }

    return this.injectRepository.saveAll(injects).stream().collect(Collectors.toSet());
  }

  /**
   * Generates injects based on the provided attack pattern ID, endpoints, asset groups, and the
   * number of injects to create for each TTP.
   *
   * @param attackPatternId the internal ID of the attack pattern to generate injects for
   * @param endpoints the list of endpoints to consider for the injects
   * @param assetsFromGroupMap the list of asset groups to consider for the injects
   * @param injectsPerTTP the maximum number of injects to create for each TTP
   * @return a list of generated injects
   */
  private Set<Inject> generateInjectsByAttackPatternId(
      String attackPatternId,
      List<Endpoint> endpoints,
      Map<AssetGroup, List<Endpoint>> assetsFromGroupMap,
      Integer injectsPerTTP)
      throws UnprocessableContentException {

    Set<Inject> injects;

    // Check if attack pattern exist
    AttackPattern attackPattern = attackPatternService.findById(attackPatternId);

    // Otherwise, We try computing the best case (with all possible platforms and architecture)
    injects =
        injectAssistantService.buildInjectsForAllPlatformAndArchCombinations(
            endpoints, new ArrayList<>(assetsFromGroupMap.keySet()), injectsPerTTP, attackPattern);

    if (!injects.isEmpty()) {
      return injects;
    }

    // Otherwise, process for all endpoints and all assetgroups to find injector contract that match
    // with TTP and platforms/architectures
    Map<InjectorContract, Inject> contractInjectMap = new HashMap<>();
    Map<String, Inject> manualInjectMap = new HashMap<>();
    List<InjectorContract> knownInjectorContracts = new ArrayList<>();

    injectAssistantService.handleEndpoints(
        endpoints,
        attackPattern,
        injectsPerTTP,
        contractInjectMap,
        manualInjectMap,
        knownInjectorContracts);
    injectAssistantService.handleAssetGroups(
        assetsFromGroupMap,
        attackPattern,
        injectsPerTTP,
        contractInjectMap,
        manualInjectMap,
        knownInjectorContracts);

    injects =
        Stream.concat(contractInjectMap.values().stream(), manualInjectMap.values().stream())
            .collect(Collectors.toSet());
    if (injects.isEmpty()) {
      throw new UnprocessableContentException("No target found");
    }
    return injects;
  }
}
