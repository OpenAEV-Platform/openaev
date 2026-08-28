package io.openaev.rest.asset.endpoint;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.specification.AssetAgentJobSpecification;
import io.openaev.database.specification.EndpointSpecification;
import io.openaev.rest.asset.endpoint.form.*;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectSearchService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.InputFilterOptions;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@Slf4j
public class EndpointApi extends RestBehavior {

  public static final String ENDPOINT_URI = "/api/endpoints";
  private static final String TENANT_ENDPOINT_URI = TENANT_PREFIX + "/endpoints";
  public static final String ASSET_URI = "/api/assets";
  private static final String TENANT_ASSET_URI = TENANT_PREFIX + "/assets";

  private final EndpointService endpointService;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final InjectSearchService injectSearchService;
  private final InjectStatusService injectStatusService;
  private final EndpointRepository endpointRepository;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final io.openaev.config.TenantWriteScopeResolver writeScopeResolver;

  private final EndpointMapper endpointMapper;
  private final AssetGroupMapper assetGroupMapper;

  /**
   * Complete an overview output with the asset groups the asset belongs to (static or dynamic
   * membership), so the asset detail page can show its group memberships.
   */
  private EndpointOverviewOutput withAssetGroups(EndpointOverviewOutput output, Asset asset) {
    output.setAssetGroups(
        this.assetGroupService.assetGroupsOfAsset(asset).stream()
            .map(assetGroupMapper::toAssetGroupSimple)
            .toList());
    return output;
  }

  @PostMapping({ENDPOINT_URI + "/agentless", TENANT_ENDPOINT_URI + "/agentless"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the created endpoint's agents eager-load their executor).
  public Endpoint createEndpoint(TxCtx ctx, @Valid @RequestBody final EndpointInput input) {
    return this.endpointService.createEndpoint(input, TenantContext.getCurrentTenant());
  }

  @PostMapping({ENDPOINT_URI + "/agentless/upsert", TENANT_ENDPOINT_URI + "/agentless/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the upserted endpoint's agents eager-load their executor).
  public Endpoint upsertAgentLessEndpoint(
      TxCtx ctx, @Valid @RequestBody final EndpointInput input) {
    return this.endpointService.upsertEndpoint(input, TenantContext.getCurrentTenant());
  }

  @PostMapping({ENDPOINT_URI + "/register", TENANT_ENDPOINT_URI + "/register"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.AGENT)
  @Transactional(rollbackFor = Exception.class)
  public Endpoint upsertEndpoint(TxCtx ctx, @Valid @RequestBody final EndpointRegisterInput input)
      throws IOException {
    input.setSeenIp(HttpReqRespUtils.getClientIpAddressIfServletRequestExist());
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return this.endpointService.register(input, tenantId);
  }

  @LogExecutionTime
  @PostMapping({ENDPOINT_URI + "/jobs", TENANT_ENDPOINT_URI + "/jobs"})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.JOB)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (job resolution loads the endpoint's agents, which eager-load their executor).
  public List<AssetAgentJob> getEndpointJobs(
      TxCtx ctx, @RequestBody final EndpointRegisterInput input) {
    return this.endpointService.getEndpointJobs(input);
  }

  @Deprecated(since = "1.11.0")
  @LogExecutionTime
  @GetMapping({
    ENDPOINT_URI + "/jobs/{endpointExternalReference}",
    TENANT_ENDPOINT_URI + "/jobs/{endpointExternalReference}"
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public List<AssetAgentJob> getEndpointJobs(
      TxCtx ctx, @PathVariable @NotBlank final String endpointExternalReference) {
    return this.assetAgentJobRepository.findAll(
        AssetAgentJobSpecification.forEndpoint(endpointExternalReference));
  }

  @DeleteMapping({
    ENDPOINT_URI + "/jobs/{assetAgentJobId}",
    TENANT_ENDPOINT_URI + "/jobs/{assetAgentJobId}"
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.JOB)
  @Transactional(rollbackFor = Exception.class)
  public void cleanupAssetAgentJob(
      TxCtx ctx, @PathVariable @NotBlank final String assetAgentJobId) {
    this.assetAgentJobRepository
        .findById(assetAgentJobId)
        .ifPresent(
            assetAgentJob -> {
              this.injectStatusService.addJobRetrievalTraces(assetAgentJob);
              this.assetAgentJobRepository.deleteById(assetAgentJobId);
            });
  }

  @Deprecated(since = "1.11.0")
  @PostMapping({
    ENDPOINT_URI + "/jobs/{assetAgentJobId}",
    TENANT_ENDPOINT_URI + "/jobs/{assetAgentJobId}"
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.JOB)
  @Transactional(rollbackFor = Exception.class)
  public void cleanupAssetAgentJobDepreacted(
      TxCtx ctx, @PathVariable @NotBlank final String assetAgentJobId) {
    this.assetAgentJobRepository.deleteById(assetAgentJobId);
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({ENDPOINT_URI, TENANT_ENDPOINT_URI})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<Endpoint> endpoints(TxCtx ctx) {
    return this.endpointService.endpoints(
        EndpointSpecification.findEndpointsForInjectionOrAgentlessEndpoints());
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({ENDPOINT_URI + "/{endpointId}", TENANT_ENDPOINT_URI + "/{endpointId}"})
  @AccessControl(
      resourceId = "#endpointId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the endpoint's agents eager-load their executor).
  public EndpointOverviewOutput endpoint(
      TxCtx ctx, @PathVariable @NotBlank final String endpointId) {
    Endpoint endpoint =
        this.endpointService.getEndpoint(endpointId, TenantContext.getCurrentTenant());
    return withAssetGroups(endpointMapper.toEndpointOverviewOutput(endpoint), endpoint);
  }

  @LogExecutionTime
  @PostMapping({ENDPOINT_URI + "/search", TENANT_ENDPOINT_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public Page<EndpointOutput> endpoints(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    Page<Endpoint> endpointPage = endpointService.searchEndpoints(searchPaginationInput);
    // Convert the Page of Endpoint to a Page of EndpointOutput
    List<EndpointOutput> endpointOutputs =
        endpointPage.getContent().stream().map(endpointMapper::toEndpointOutput).toList();
    return new PageImpl<>(
        endpointOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  /**
   * Unified asset inventory: paginated search over EVERY asset type (endpoints, AI targets,
   * identities, cloud / web / network / generic assets). Endpoints keep their agents/platform in
   * the output; other asset types list with those fields empty. Filters/sorts must reference base
   * {@link Asset} attributes (endpoint-only facets such as platform/arch cannot resolve here).
   */
  @LogExecutionTime
  @PostMapping({ASSET_URI + "/search", TENANT_ASSET_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (endpoint-type assets' agents eager-load their executor - without this, the
  // executors LEFT JOIN's can_access_tenant() always denies, so every agent shows "Unknown").
  public Page<EndpointOutput> assets(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    Page<Asset> assetPage = assetService.searchAssets(searchPaginationInput);
    List<EndpointOutput> assetOutputs =
        assetPage.getContent().stream().map(endpointMapper::toAssetOutput).toList();
    return new PageImpl<>(assetOutputs, assetPage.getPageable(), assetPage.getTotalElements());
  }

  @LogExecutionTime
  @PostMapping({ENDPOINT_URI + "/targets", TENANT_ENDPOINT_URI + "/targets"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public Page<EndpointTargetOutput> targetEndpoints(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {

    Page<Endpoint> endpointPage = endpointService.searchManagedEndpoints(searchPaginationInput);
    List<EndpointTargetOutput> endpointTargetOutputs =
        endpointPage.getContent().stream().map(endpointMapper::toEndpointTargetOutput).toList();
    return new PageImpl<>(
        endpointTargetOutputs, endpointPage.getPageable(), endpointPage.getTotalElements());
  }

  @LogExecutionTime
  @PostMapping({ENDPOINT_URI + "/find", TENANT_ENDPOINT_URI + "/find"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Transactional(readOnly = true)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<Endpoint> findEndpoints(
      TxCtx ctx, @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpoints(endpointIds);
  }

  @PutMapping({ENDPOINT_URI + "/{endpointId}", TENANT_ENDPOINT_URI + "/{endpointId}"})
  @AccessControl(
      resourceId = "#endpointId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the endpoint's agents eager-load their executor).
  public EndpointOverviewOutput updateEndpoint(
      TxCtx ctx,
      @PathVariable @NotBlank final String endpointId,
      @Valid @RequestBody final EndpointInput input) {
    Endpoint endpoint =
        this.endpointService.updateEndpoint(endpointId, input, TenantContext.getCurrentTenant());
    return withAssetGroups(endpointMapper.toEndpointOverviewOutput(endpoint), endpoint);
  }

  @DeleteMapping({ENDPOINT_URI + "/{endpointId}", TENANT_ENDPOINT_URI + "/{endpointId}"})
  @AccessControl(
      resourceId = "#endpointId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (deletion loads the endpoint's agents, which eager-load their executor).
  public void deleteEndpoint(TxCtx ctx, @PathVariable @NotBlank final String endpointId) {
    this.endpointService.deleteEndpoint(endpointId);
  }

  /**
   * Generic asset overview for the unified asset detail page: returns any asset type (endpoint, AI
   * target or any other category). Endpoints keep their full representation; other types expose
   * their category-relevant fields (AI targets include their connection metadata, token excluded).
   */
  @Transactional
  @GetMapping({ASSET_URI + "/{assetId}", TENANT_ASSET_URI + "/{assetId}"})
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (an endpoint-type asset's agents eager-load their executor).
  public EndpointOverviewOutput asset(TxCtx ctx, @PathVariable @NotBlank final String assetId) {
    Asset asset = assetService.asset(assetId);
    return withAssetGroups(endpointMapper.toAssetOverviewOutput(asset), asset);
  }

  /**
   * "Injects played" for the asset detail page: every inject (atomic testing or simulation inject)
   * that concerns this asset, whether it was targeted directly, through an asset group (static or
   * dynamic) or evidenced by the expectations persisted at execution time. This matches the scope
   * of the asset posture score, unlike the plain atomic-testing search which only sees direct
   * targeting of standalone injects.
   */
  @LogExecutionTime
  @PostMapping({
    ASSET_URI + "/{assetId}/injects/search",
    TENANT_ASSET_URI + "/{assetId}/injects/search"
  })
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Transactional(readOnly = true)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // injectors/executors tables (inject results resolve their injector and, through agents, their
  // executor).
  public Page<InjectResultOutput> searchInjectsForAsset(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return injectSearchService.getPageOfInjectResultsForAsset(assetId, searchPaginationInput);
  }

  /**
   * Generic asset deletion for the unified inventory: deletes any asset type (endpoint, AI target
   * or any other category) by id. Security platforms are rejected (managed in their own area).
   */
  @DeleteMapping({ASSET_URI + "/{assetId}", TENANT_ASSET_URI + "/{assetId}"})
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (deletion loads an endpoint-type asset's agents, which eager-load their
  // executor).
  public void deleteAsset(TxCtx ctx, @PathVariable @NotBlank final String assetId) {
    this.assetService.deleteAsset(assetId);
  }

  /**
   * Bulk deletion for the unified asset inventory: deletes assets of any category from an explicit
   * id list or from a search input (select-all with optional exclusions). Security platforms are
   * always excluded from the scope.
   */
  @LogExecutionTime
  @DeleteMapping({ASSET_URI, TENANT_ASSET_URI})
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ASSET)
  // SUPPORTS (not REQUIRED) on purpose: the service deletes in small independent transactions
  // (chunked, with deadlock retry) - a request-wide transaction would defeat that.
  @Transactional(propagation = Propagation.SUPPORTS)
  public List<String> bulkDeleteAssets(@RequestBody @Valid final AssetBulkProcessingInput input) {
    return this.assetService.bulkDeleteAssets(input);
  }

  @GetMapping({ENDPOINT_URI + "/resolve", TENANT_ENDPOINT_URI + "/resolve"})
  // DNS resolution is network I/O and touches no DB. The endpoint @Transactional rule still
  // requires the annotation, so NOT_SUPPORTED keeps it while suspending any DB transaction.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<String> resolveHostname(@RequestParam @NotBlank final String hostname) {
    return this.endpointService.resolveHostnameToIps(hostname);
  }

  // -- OPTION --

  @GetMapping({ENDPOINT_URI + "/options", TENANT_ENDPOINT_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
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
              endpointRepository.findAllEndpointsForAtomicTestingsSimulationsAndScenarios().stream()
                  .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
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
              endpointRepository
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
  @GetMapping({ENDPOINT_URI + "/findings/options", TENANT_ENDPOINT_URI + "/findings/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return endpointService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @PostMapping({ENDPOINT_URI + "/options", TENANT_ENDPOINT_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.endpointRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
