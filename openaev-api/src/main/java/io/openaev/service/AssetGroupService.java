package io.openaev.service;

import static io.openaev.database.model.Filters.isEmptyFilterGroup;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static java.time.Instant.now;

import io.openaev.database.model.*;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.specification.EndpointSpecification;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.mapper.AssetGroupMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
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
  private final AssetGroupMapper assetGroupMapper;

  // -- ASSET GROUP --

  public AssetGroup createAssetGroup(@NotNull final AssetGroup assetGroup) {
    AssetGroup assetGroupCreated = this.assetGroupRepository.save(assetGroup);
    return computeDynamicAssets(assetGroupCreated);
  }

  public List<AssetGroup> assetGroups() {
    List<AssetGroup> assetGroups = fromIterable(this.assetGroupRepository.findAll());
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroups(@NotNull final List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findAllById(assetGroupIds));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroup> assetGroupsForSimulation(@NotBlank final String simulationId) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findDistinctByInjectsSimulationId(simulationId));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroupOutput> assetGroupsByIdsForSimulation(
      @NotBlank final String simulationId, List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(
            this.assetGroupRepository.findDistinctByInjectsSimulationIdAndIdIn(
                simulationId, assetGroupIds));
    return computeDynamicAssets(assetGroups).stream()
        .map(assetGroupMapper::toAssetGroupOutput)
        .toList();
  }

  public List<AssetGroup> assetGroupsForScenario(@NotBlank final String scenarioId) {
    List<AssetGroup> assetGroups =
        fromIterable(this.assetGroupRepository.findDistinctByInjectsScenarioId(scenarioId));
    return computeDynamicAssets(assetGroups);
  }

  public List<AssetGroupOutput> assetGroupsByIdsForScenario(
      @NotBlank final String scenarioId, List<String> assetGroupIds) {
    List<AssetGroup> assetGroups =
        fromIterable(
            this.assetGroupRepository.findDistinctByInjectsScenarioIdAndIdIn(
                scenarioId, assetGroupIds));
    return computeDynamicAssets(assetGroups).stream()
        .map(assetGroupMapper::toAssetGroupOutput)
        .toList();
  }

  public AssetGroup assetGroup(@NotBlank final String assetGroupId) {
    return this.assetGroupRepository
        .findById(assetGroupId)
        .map(this::computeDynamicAssets)
        .orElseThrow(() -> new ElementNotFoundException("Asset group not found: " + assetGroupId));
  }

  public Optional<AssetGroup> findByExternalReference(String externalReference, String tenantId) {
    return this.assetGroupRepository.findByExternalReferenceAndTenantId(
        externalReference, tenantId);
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
    return assetsFromAssetGroup(this.assetGroup(assetGroupId));
  }

  /**
   * Same as {@link #assetsFromAssetGroup(String)} but for a group whose dynamic assets are already
   * resolved - avoids re-running the (potentially expensive) dynamic resolution.
   */
  public List<Asset> assetsFromAssetGroup(@NotNull final AssetGroup assetGroup) {
    List<Asset> assets = new ArrayList<>();
    // Dedup on getId() (not on the instance) because Hibernate proxies and unproxied
    // entities of the same row would otherwise both pass an identity-based check.
    Set<String> assetIds = new HashSet<>();
    Stream.concat(assetGroup.getAssets().stream(), assetGroup.getDynamicAssets().stream())
        .forEach(
            asset -> {
              if (assetIds.add(asset.getId())) {
                assets.add(asset);
              }
            });
    return assets;
  }

  private List<AssetGroup> computeDynamicAssets(@NotNull final List<AssetGroup> assetGroups) {
    if (assetGroups.stream()
        .allMatch(assetGroup -> isEmptyFilterGroup(assetGroup.getDynamicFilter()))) {
      return assetGroups;
    }

    assetGroups.forEach(
        assetGroup -> {
          if (!isEmptyFilterGroup(assetGroup.getDynamicFilter())) {
            Specification<Endpoint> specification =
                computeFilterGroupJpa(assetGroup.getDynamicFilter());
            List<Asset> assets = new ArrayList<>();
            this.endpointService.endpoints(specification).stream()
                .map(Asset.class::cast)
                .forEach(assets::add);
            assets.addAll(resolveDynamicNonEndpointAssets(assetGroup.getDynamicFilter()));
            assetGroup.setDynamicAssets(assets.stream().distinct().toList());
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
    List<Asset> assets = new ArrayList<>();
    this.endpointService.endpoints(specification.and(specification2)).stream()
        .map(Asset.class::cast)
        .forEach(assets::add);
    assets.addAll(resolveDynamicNonEndpointAssets(assetGroup.getDynamicFilter()));
    assetGroup.setDynamicAssets(assets.stream().distinct().toList());
    return assetGroup;
  }

  /**
   * Resolve the NON-endpoint assets (AI targets, identities, cloud / web / network / generic
   * assets, ...) matching a dynamic filter. The endpoint-scoped resolution above only returns
   * {@code Endpoint} rows, so without this a dynamic group such as {@code Category = AI_TARGET}
   * would always be empty. The endpoint injectability constraint (agent / agentless) does not apply
   * to non-endpoint assets, and {@code Endpoint} rows are excluded here to avoid duplicating the
   * endpoint branch. When the filter references a field that does not exist for the queried assets
   * (e.g. an endpoint-only {@code platform} rule) the query cannot resolve, which simply means the
   * group targets no non-endpoint assets and contributes none.
   */
  private List<Asset> resolveDynamicNonEndpointAssets(
      @NotNull final Filters.FilterGroup dynamicFilter) {
    // The specification is lazy: an unresolvable filter key (e.g. an endpoint-only
    // "platform" rule) only throws inside the repository call, which marks the
    // surrounding transaction rollback-only BEFORE the catch below runs - killing
    // startup when the starter pack seeds endpoint-scoped dynamic groups on a fresh
    // database. Validate the keys eagerly instead: subtype-only keys cannot match
    // non-endpoint assets anyway, so such groups simply contribute none.
    if (!isFilterResolvableForBaseAssets(dynamicFilter)) {
      return List.of();
    }
    // No catch here on purpose: a repository failure inside an active transaction has already
    // marked it rollback-only, so swallowing the exception would only defer the failure to an
    // opaque UnexpectedRollbackException at commit. The eager key validation above is the guard.
    Specification<Asset> filterSpec = computeFilterGroupJpa(dynamicFilter);
    Specification<Asset> nonEndpoint =
        (root, query, cb) -> cb.notEqual(root.get("type"), AssetType.Values.ENDPOINT_TYPE);
    return this.assetService.assets(filterSpec.and(nonEndpoint));
  }

  /** True when every filter key is a filterable property of the base {@link Asset} type. */
  private boolean isFilterResolvableForBaseAssets(final Filters.FilterGroup dynamicFilter) {
    Set<String> filterableKeys =
        SchemaUtils.getFilterableProperties(SchemaUtils.schema(Asset.class)).stream()
            .map(PropertySchema::getJsonName)
            .collect(Collectors.toSet());
    return Optional.ofNullable(dynamicFilter.getFilters()).orElse(List.of()).stream()
        .allMatch(filter -> filterableKeys.contains(filter.getKey()));
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
                        // A group may now resolve non-endpoint assets (e.g. AI targets); this
                        // endpoint-scoped view only keeps the endpoints. Unproxy first: a lazy
                        // proxy typed as Asset would fail the instanceof for a real endpoint.
                        .map(
                            asset ->
                                Hibernate.unproxy(asset) instanceof Endpoint endpoint
                                    ? endpoint
                                    : null)
                        .filter(Objects::nonNull)
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
