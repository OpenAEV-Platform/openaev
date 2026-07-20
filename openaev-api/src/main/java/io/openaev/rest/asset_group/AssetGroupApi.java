package io.openaev.rest.asset_group;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.AssetGroupSpecification.fromIds;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.asset.dto.AssetOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.asset_group.form.UpdateAssetsOnAssetGroupInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InputFilterOptions;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AssetGroupApi extends RestBehavior {

  public static final String ASSET_GROUP_URI = "/api/asset_groups";
  private static final String TENANT_ASSET_GROUP_URI = TENANT_PREFIX + "/asset_groups";

  private final AssetGroupService assetGroupService;
  private final AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService;
  private final TagRepository tagRepository;
  private final AssetGroupRepository assetGroupRepository;

  @PostMapping({ASSET_GROUP_URI, TENANT_ASSET_GROUP_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup createAssetGroup(@Valid @RequestBody final AssetGroupInput input) {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setUpdateAttributes(input);
    assetGroup.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.assetGroupService.createAssetGroup(assetGroup);
  }

  @GetMapping({ASSET_GROUP_URI, TENANT_ASSET_GROUP_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET_GROUP)
  public List<AssetGroup> assetGroups() {
    return this.assetGroupService.assetGroups();
  }

  @LogExecutionTime
  @PostMapping({ASSET_GROUP_URI + "/search", TENANT_ASSET_GROUP_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public Page<AssetGroupOutput> assetGroups(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.assetGroupCriteriaBuilderService.assetGroupPagination(searchPaginationInput);
  }

  @PostMapping({
    ASSET_GROUP_URI + "/{assetGroupId}/assets/search",
    TENANT_ASSET_GROUP_URI + "/{assetGroupId}/assets/search"
  })
  @Transactional
  @AccessControl(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public Page<AssetOutput> assetsFromAssetGroup(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @PathVariable @NotBlank final String assetGroupId) {

    // Group members can be ANY asset type (endpoints, AI targets, identities, cloud/web/network,
    // ...). Resolve them uniformly (static + dynamic), then filter/paginate in memory since a group
    // membership is bounded and mixes discriminators the JPA endpoint search cannot span.
    AssetGroup assetGroup = this.assetGroupService.assetGroup(assetGroupId);
    Set<String> staticIds =
        assetGroup.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());

    String textSearch = StringUtils.trimToEmpty(searchPaginationInput.getTextSearch());
    List<AssetOutput> all =
        this.assetGroupService.assetsFromAssetGroup(assetGroup).stream()
            .filter(
                asset ->
                    textSearch.isEmpty()
                        || StringUtils.containsIgnoreCase(asset.getName(), textSearch))
            .map(asset -> AssetOutput.from(asset, staticIds.contains(asset.getId())))
            // Deterministic order (name, then id as tie-breaker) so page boundaries are stable
            // across requests regardless of DB iteration order.
            .sorted(
                Comparator.comparing(
                        AssetOutput::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(AssetOutput::getId))
            .toList();

    int page = Math.max(0, searchPaginationInput.getPage());
    int size = Math.max(1, searchPaginationInput.getSize());
    int fromIndex = Math.min(page * size, all.size());
    int toIndex = Math.min(fromIndex + size, all.size());
    List<AssetOutput> pageContent = all.subList(fromIndex, toIndex);
    return new PageImpl<>(pageContent, PageRequest.of(page, size), all.size());
  }

  @PostMapping({ASSET_GROUP_URI + "/find", TENANT_ASSET_GROUP_URI + "/find"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(readOnly = true)
  public List<AssetGroupOutput> findAssetGroups(
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupCriteriaBuilderService.find(fromIds(assetGroupIds));
  }

  @GetMapping({ASSET_GROUP_URI + "/{assetGroupId}", TENANT_ASSET_GROUP_URI + "/{assetGroupId}"})
  @Transactional
  @AccessControl(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public AssetGroup assetGroup(@PathVariable @NotBlank final String assetGroupId) {
    return this.assetGroupService.assetGroup(assetGroupId);
  }

  @PutMapping({ASSET_GROUP_URI + "/{assetGroupId}", TENANT_ASSET_GROUP_URI + "/{assetGroupId}"})
  @AccessControl(
      resourceId = "#assetGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup updateAssetGroup(
      @PathVariable @NotBlank final String assetGroupId,
      @Valid @RequestBody final AssetGroupInput input) {
    AssetGroup assetGroup = this.assetGroupService.assetGroup(assetGroupId);
    assetGroup.setUpdateAttributes(input);
    assetGroup.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.assetGroupService.updateAssetGroup(assetGroup);
  }

  @PutMapping({
    ASSET_GROUP_URI + "/{assetGroupId}/assets",
    TENANT_ASSET_GROUP_URI + "/{assetGroupId}/assets"
  })
  @AccessControl(
      resourceId = "#assetGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup updateAssetsOnAssetGroup(
      @PathVariable @NotBlank final String assetGroupId,
      @Valid @RequestBody final UpdateAssetsOnAssetGroupInput input) {
    AssetGroup assetGroup = this.assetGroupService.assetGroup(assetGroupId);
    return this.assetGroupService.updateAssetsOnAssetGroup(assetGroup, input.getAssetIds());
  }

  @DeleteMapping({ASSET_GROUP_URI + "/{assetGroupId}", TENANT_ASSET_GROUP_URI + "/{assetGroupId}"})
  @AccessControl(
      resourceId = "#assetGroupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public void deleteAssetGroup(@PathVariable @NotBlank final String assetGroupId) {
    try {
      assetGroupService.assetGroup(assetGroupId);
    } catch (IllegalArgumentException ex) {
      throw new ElementNotFoundException(ex.getMessage());
    }
    this.assetGroupService.deleteAssetGroup(assetGroupId);
  }

  // -- OPTION --

  @GetMapping({ASSET_GROUP_URI + "/options", TENANT_ASSET_GROUP_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    InputFilterOptions injectFilterOptionEnum;
    try {
      injectFilterOptionEnum = InputFilterOptions.valueOf(inputFilterOption);
    } catch (Exception e) {
      if (StringUtils.isEmpty(inputFilterOption)) {
        log.warn("InputFilterOption is null, fall back to backwards compatible case");
        if (StringUtils.isNotEmpty(sourceId)) {
          injectFilterOptionEnum = InputFilterOptions.SIMULATION_OR_SCENARIO;
        } else {
          injectFilterOptionEnum = InputFilterOptions.ATOMIC_TESTING;
        }
      } else {
        throw new BadRequestException(
            String.format("Invalid input filter option %s", inputFilterOption));
      }
    }
    switch (injectFilterOptionEnum) {
      case ALL_INJECTS:
        {
          options =
              assetGroupRepository
                  .findAllAssetGroupsForAtomicTestingsSimulationsAndScenarios()
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .distinct()
                  .toList();
          break;
        }
      case SIMULATION_OR_SCENARIO:
        {
          if (StringUtils.isEmpty(sourceId)) {
            throw new BadRequestException("Missing simulation or scenario id");
          }
          // fall through intentional
        }
      case ATOMIC_TESTING:
        {
          options =
              assetGroupRepository
                  .findAllBySimulationOrScenarioIdAndName(
                      StringUtils.trimToNull(sourceId), StringUtils.trimToNull(searchText))
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({ASSET_GROUP_URI + "/findings/options", TENANT_ASSET_GROUP_URI + "/findings/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return assetGroupService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping({ASSET_GROUP_URI + "/options", TENANT_ASSET_GROUP_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.assetGroupRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
