package io.openaev.rest.asset_group;

import static io.openaev.database.specification.AssetGroupSpecification.fromIds;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.asset_group.form.UpdateAssetsOnAssetGroupInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InputFilterOptions;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
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
  private final EndpointService endpointService;
  private final EndpointMapper endpointMapper;

  private final AssetGroupService assetGroupService;
  private final AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService;
  private final TagRepository tagRepository;
  private final AssetGroupRepository assetGroupRepository;

  @PostMapping(ASSET_GROUP_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public AssetGroup createAssetGroup(@Valid @RequestBody final AssetGroupInput input) {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setUpdateAttributes(input);
    assetGroup.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.assetGroupService.createAssetGroup(assetGroup);
  }

  @GetMapping(ASSET_GROUP_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ASSET_GROUP)
  public List<AssetGroup> assetGroups() {
    return this.assetGroupService.assetGroups();
  }

  @LogExecutionTime
  @PostMapping(ASSET_GROUP_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public Page<AssetGroupOutput> assetGroups(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return this.assetGroupCriteriaBuilderService.assetGroupPagination(searchPaginationInput);
  }

  @PostMapping(ASSET_GROUP_URI + "/{assetGroupId}/assets/search")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public Page<EndpointOutput> endpointsFromAssetGroup(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      @PathVariable @NotBlank final String assetGroupId) {

    Page<Endpoint> endpointPage =
        endpointService.searchManagedEndpoints(assetGroupId, searchPaginationInput);
    // Convert the Page of Endpoint to a Page of EndpointOutput
    List<EndpointOutput> endpointOutputs =
        endpointPage.getContent().stream()
            .map(
                endpoint -> {
                  Boolean isPresent =
                      endpoint.getAssetGroups().stream()
                          .map(AssetGroup::getId)
                          .anyMatch(id -> Objects.equals(id, assetGroupId));
                  EndpointOutput endpointOutput = endpointMapper.toEndpointOutput(endpoint);
                  endpointOutput.setIsStatic(isPresent);
                  return endpointOutput;
                })
            .toList();
    return new PageImpl<>(
        endpointOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  @PostMapping(ASSET_GROUP_URI + "/find")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  @Transactional(readOnly = true)
  public List<AssetGroupOutput> findAssetGroups(
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupCriteriaBuilderService.find(fromIds(assetGroupIds));
  }

  @GetMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
      resourceId = "#assetGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET_GROUP)
  public AssetGroup assetGroup(@PathVariable @NotBlank final String assetGroupId) {
    return this.assetGroupService.assetGroup(assetGroupId);
  }

  @PutMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
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

  @PutMapping(ASSET_GROUP_URI + "/{assetGroupId}/assets")
  @RBAC(
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

  @DeleteMapping(ASSET_GROUP_URI + "/{assetGroupId}")
  @RBAC(
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

  @GetMapping(ASSET_GROUP_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String simulationOrScenarioId,
      @RequestParam(required = false) final String inputFilterOption) {
    List<FilterUtilsJpa.Option> options = List.of();
    InputFilterOptions injectFilterOptionEnum;
    try {
      injectFilterOptionEnum = InputFilterOptions.valueOf(inputFilterOption);
    } catch (Exception e) {
      if (StringUtils.isEmpty(inputFilterOption)) {
        log.warn("InputFilterOption is null, fall back to backwards compatible case");
        if (StringUtils.isNotEmpty(simulationOrScenarioId)) {
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
          if (StringUtils.isEmpty(simulationOrScenarioId)) {
            throw new BadRequestException("Missing simulation or scenario id");
          }
        }
      case ATOMIC_TESTING:
        {
          options =
              assetGroupRepository
                  .findAllBySimulationOrScenarioIdAndName(
                      StringUtils.trimToNull(simulationOrScenarioId),
                      StringUtils.trimToNull(searchText))
                  .stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
                  .toList();
          break;
        }
    }
    return options;
  }

  @LogExecutionTime
  @GetMapping(ASSET_GROUP_URI + "/findings/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return assetGroupService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping(ASSET_GROUP_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET_GROUP)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.assetGroupRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
