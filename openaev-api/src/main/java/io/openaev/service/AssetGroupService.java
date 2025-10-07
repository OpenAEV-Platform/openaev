package io.openaev.service;

import static io.openaev.database.model.Filters.isEmptyFilterGroup;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.FilterUtilsRuntime.computeFilterGroupRuntime;
import static java.time.Instant.now;

import io.openaev.database.model.*;
import io.openaev.database.raw.RawAssetGroup;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.specification.EndpointSpecification;
import io.openaev.utils.FilterUtilsJpa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetGroupService {

  private final AssetGroupRepository assetGroupRepository;
  private final AssetService assetService;
  private final EndpointService endpointService;
  private final TagRuleService tagRuleService;

  // -- ASSET GROUP --

  public AssetGroup createAssetGroup(@NotNull final AssetGroup assetGroup) {
    AssetGroup assetGroupCreated = this.assetGroupRepository.save(assetGroup);
    return computeDynamicAssets(assetGroupCreated);
  }

  public List<AssetGroup> assetGroups() {
    List<AssetGroup> assetGroups = fromIterable(this.assetGroupRepository.findAll());
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroups(@NotBlank final List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findAllById(assetGroupIds));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroupsForSimulation(@NotBlank final String simulationId) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findDistinctByInjectsSimulationId(simulationId));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroupsByIdsForSimulation(
      @NotBlank final String simulationId, List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(
            this.assetGroupRepository.findDistinctByInjectsSimulationIdAndIdIn(
                simulationId, assetGroupIds));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroupsForScenario(@NotBlank final String scenarioId) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findDistinctByInjectsScenarioId(scenarioId));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroupsByIdsForScenario(
      @NotBlank final String scenarioId, List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(
            this.assetGroupRepository.findDistinctByInjectsScenarioIdAndIdIn(
                scenarioId, assetGroupIds));
    return computeDynamicAssets(assetGroups);
  }

  public AssetGroup assetGroup(@NotBlank final String assetGroupId) {
    AssetGroup assetGroup =
        this.assetGroupRepository
            .findById(assetGroupId)
            .orElseThrow(() -> new IllegalArgumentException("Asset group not found"));
    return computeDynamicAssets(assetGroup);
  }

  public Optional<AssetGroup> findByExternalReference(String externalReference) {
    return this.assetGroupRepository.findByExternalReference(externalReference);
  }

  public AssetGroup updateAssetGroup(@NotNull final AssetGroup assetGroup) {
    assetGroup.setUpdatedAt(now());
    AssetGroup assetGroupUpdated = this.assetGroupRepository.save(assetGroup);
    return computeDynamicAssets(assetGroupUpdated);
  }

  public AssetGroup updateAssetsOnAssetGroup(
      @NotNull final AssetGroup assetGroup, @NotNull final List<String> assetIds) {
    Iterable<Asset> assets = this.assetService.assetFromIds(assetIds);
    assetGroup.setAssets(fromIterable(assets));
    assetGroup.setUpdatedAt(now());
    AssetGroup assetGroupUpdated = this.assetGroupRepository.save(assetGroup);
    return computeDynamicAssets(assetGroupUpdated);
  }

  public void deleteAssetGroup(@NotBlank final String assetGroupId) {
    this.assetGroupRepository.deleteById(assetGroupId);
  }

  public AssetGroup createOrUpdateAssetGroupWithoutDynamicAssets(AssetGroup assetGroup) {
    return this.assetGroupRepository.save(assetGroup);
  }

  // -- ASSET --

  @Transactional(readOnly = true)
  public List<Asset> assetsFromAssetGroup(@NotBlank final String assetGroupId) {
    AssetGroup assetGroup = this.assetGroup(assetGroupId);
    List<Asset> assets = new ArrayList<>();
    List<String> assetIds = new ArrayList<>();
    Stream.concat(assetGroup.getAssets().stream(), assetGroup.getDynamicAssets().stream())
        .forEach(
            asset -> {
              // We have to call getId() because some assets are returned null because of Hibernate
              // unproxy
              if (!assetIds.contains(asset.getId())) {
                assets.add(asset);
                assetIds.add(asset.getId());
              }
            });
    return assets;
  }

  private List<AssetGroup> computeDynamicAssets(@NotNull final List<AssetGroup> assetGroups) {
    if (assetGroups.stream()
        .noneMatch(assetGroup -> isEmptyFilterGroup(assetGroup.getDynamicFilter()))) {
      return assetGroups;
    }

    List<Asset> assets = this.assetService.assets();
    assetGroups.forEach(
        assetGroup -> {
          if (!isEmptyFilterGroup(assetGroup.getDynamicFilter())) {
            Predicate<Object> filters = computeFilterGroupRuntime(assetGroup.getDynamicFilter());

            List<Asset> filteredAssets =
                assets.stream()
                    .filter(
                        asset ->
                            "Endpoint"
                                .equals(
                                    asset.getType())) // Filters for dynamic assets are applicable
                    // only to endpoints
                    .filter(filters)
                    .toList();

            assetGroup.setDynamicAssets(filteredAssets);
          }
        });
    return assetGroups;
  }

  public AssetGroup computeDynamicAssets(@NotNull final AssetGroup assetGroup) {
    if (isEmptyFilterGroup(assetGroup.getDynamicFilter())) {
      return assetGroup;
    }
    Specification<Endpoint> specification = computeFilterGroupJpa(assetGroup.getDynamicFilter());
    Specification<Endpoint> specification2 =
        EndpointSpecification.findEndpointsForInjectionOrAgentlessEndpoints();
    List<Asset> assets =
        this.endpointService.endpoints(specification.and(specification2)).stream()
            .map(Asset.class::cast)
            .distinct()
            .toList();
    assetGroup.setDynamicAssets(assets);
    return assetGroup;
  }

  public Map<String, List<Endpoint>> computeDynamicAssetFromRaw(
      @NotNull Set<RawAssetGroup> assetGroups) {
    if (assetGroups.isEmpty()) {
      return Map.of();
    }

    return assetGroups.stream()
        .collect(
            Collectors.toMap(
                RawAssetGroup::getAsset_group_id,
                assetGroup ->
                    Optional.of(assetGroup.getAssetGroupDynamicFilter())
                        .filter(filterGroup -> !isEmptyFilterGroup(filterGroup))
                        .map(
                            filter -> {
                              Specification<Endpoint> specification = computeFilterGroupJpa(filter);
                              Specification<Endpoint> specification2 =
                                  EndpointSpecification
                                      .findEndpointsForInjectionOrAgentlessEndpoints();
                              return this.endpointService
                                  .endpoints(specification.and(specification2))
                                  .stream()
                                  .distinct()
                                  .toList();
                            })
                        .orElse(Collections.emptyList())));
  }

  public List<FilterUtilsJpa.Option> getOptionsByNameLinkedToFindings(
      String searchText, String sourceId, Pageable pageable) {
    String trimmedSearchText = StringUtils.trimToNull(searchText);
    String trimmedSourceId = StringUtils.trimToNull(sourceId);

    List<Object[]> results;

    if (trimmedSourceId == null) {
      results = assetGroupRepository.findAllByNameLinkedToFindings(trimmedSearchText, pageable);
    } else {
      results =
          assetGroupRepository.findAllByNameLinkedToFindingsWithContext(
              trimmedSourceId, trimmedSearchText, pageable);
    }

    return results.stream()
        .map(i -> new FilterUtilsJpa.Option((String) i[0], (String) i[1]))
        .toList();
  }

  /**
   * Build a map with asset groups and their list of endpoints (directly or dynamically related)
   *
   * @param assetGroups list
   * @return map of asset groups with the list of endpoints
   */
  public Map<AssetGroup, List<Endpoint>> assetsFromAssetGroupMap(List<AssetGroup> assetGroups) {
    return assetGroups.stream()
        .collect(
            Collectors.toMap(
                group -> group,
                group ->
                    this.assetsFromAssetGroup(group.getId()).stream()
                        .map(Endpoint.class::cast)
                        .toList()));
  }

  /**
   * Retrieves asset groups for a scenario based on tag rules using the {@code tagRuleService}.
   *
   * @param scenario the scenario containing tag references
   * @return set of asset groups associated with the scenario tags
   */
  public Set<AssetGroup> fetchAssetGroupsFromScenarioTagRules(Scenario scenario) {
    return new HashSet<>(
        tagRuleService.getAssetGroupsFromTagIds(
            scenario.getTags().stream().map(Tag::getId).toList()));
  }
}
