package io.openaev.rest.injector_contract;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.aop.AccessControl;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractAuthorCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFacetCountsOutput;
import io.openaev.rest.injector_contract.output.InjectorContractFullOutput;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class InjectorContractApi extends RestBehavior {

  public static final String INJECTOR_CONTRACT_URL = "/api/injector_contracts";
  private static final String TENANT_INJECTOR_CONTRACT_URL = TENANT_PREFIX + "/injector_contracts";

  private final InjectorContractService injectorContractService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @GetMapping({INJECTOR_CONTRACT_URL, TENANT_INJECTOR_CONTRACT_URL})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx is unused directly: TenantScopeTransactionAspect reads it to scope the transaction, which
  // the eager injectorLinks -> injector fetch behind this contract read needs (#7026-class gap).
  public Iterable<RawInjectorsContracts> injectContracts(TxCtx ctx) {
    return injectorContractService.getAllRawInjectContracts();
  }

  /**
   * Searches injector contracts with pagination and filtering.
   *
   * <p>When {@code input.includeFullDetails} is {@code true} (the default), the response contains
   * {@link InjectorContractFullOutput} entries; otherwise {@link InjectorContractBaseOutput}
   * entries are returned.
   *
   * @param input the search and pagination parameters
   * @return a paged list of injector contract outputs in the selected format
   */
  @Operation(summary = "Search injector contracts")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {
                        InjectorContractBaseOutput.class,
                        InjectorContractFullOutput.class,
                      })))
  @PostMapping({INJECTOR_CONTRACT_URL + "/search", TENANT_INJECTOR_CONTRACT_URL + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx scopes the tuple query's explicit injector join (ctx.injectorJoin() in
  // InjectorContractService#mapFull), which resolves against the activated injectors table.
  public Page<? extends InjectorContractBaseOutput> injectorContracts(
      TxCtx ctx, @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            this.injectorContractService.getSinglePage(
                spec,
                specCount,
                pageable,
                input.isIncludeFullDetails()
                    ? InjectorContractService.OutputMode.FULL
                    : InjectorContractService.OutputMode.BASE,
                input.getInjectorContractIdsToIgnore(),
                input.getInjectorContractIdsToProcess()),
        handleArchitectureFilter(input),
        InjectorContract.class);
  }

  @PostMapping({
    INJECTOR_CONTRACT_URL + "/domain-counts",
    TENANT_INJECTOR_CONTRACT_URL + "/domain-counts"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public List<InjectorContractDomainCountOutput> getDomainCounts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    SearchPaginationInput filtered = handleArchitectureFilter(input);
    return injectorContractService.getDomainCounts(filtered);
  }

  /**
   * Platform, kill-chain-phase and payload-status facet counts under the current filters, powering
   * the live count badges of the inject-contract picker sidebar (the domain and author facets have
   * their own endpoints).
   */
  @Operation(summary = "Platform, kill chain phase and status facet counts for the contract picker")
  @PostMapping({
    INJECTOR_CONTRACT_URL + "/facet-counts",
    TENANT_INJECTOR_CONTRACT_URL + "/facet-counts"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContractFacetCountsOutput getFacetCounts(
      TxCtx ctx, @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    SearchPaginationInput filtered = handleArchitectureFilter(input);
    return new InjectorContractFacetCountsOutput(
        injectorContractService.getPlatformCounts(filtered),
        injectorContractService.getKillChainPhaseCounts(filtered),
        injectorContractService.getStatusCounts(filtered));
  }

  /**
   * Author facet counts under the current filters, so the inject-contract picker sidebar can show
   * every author and grey out the zero-count ones (mirrors the Threat Arsenal author facet).
   */
  @Operation(summary = "Author facet counts for the inject contract picker")
  @PostMapping({
    INJECTOR_CONTRACT_URL + "/author-counts",
    TENANT_INJECTOR_CONTRACT_URL + "/author-counts"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public List<InjectorContractAuthorCountOutput> getAuthorCounts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    SearchPaginationInput filtered = handleArchitectureFilter(input);
    return injectorContractService.getAuthorCounts(filtered);
  }

  /**
   * Retrieves a specific injector contract by ID.
   *
   * @param injectorContractId the contract ID or external ID
   * @return the injector contract
   */
  @GetMapping({
    INJECTOR_CONTRACT_URL + "/{injectorContractId}",
    TENANT_INJECTOR_CONTRACT_URL + "/{injectorContractId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx scopes the eager injectorLinks -> injector fetch triggered when the contract loads.
  public InjectorContract injectorContract(TxCtx ctx, @PathVariable String injectorContractId) {
    return injectorContractService.injectorContract(injectorContractId);
  }

  /**
   * Creates a new custom injector contract.
   *
   * @param input the creation input with contract details
   * @return the created injector contract
   */
  @PostMapping({INJECTOR_CONTRACT_URL, TENANT_INJECTOR_CONTRACT_URL})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract createInjectorContract(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody InjectorContractAddInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return injectorContractService.createNewInjectorContract(input);
  }

  /**
   * Updates an existing injector contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the update data
   * @return the updated injector contract
   */
  @PutMapping({
    INJECTOR_CONTRACT_URL + "/{injectorContractId}",
    TENANT_INJECTOR_CONTRACT_URL + "/{injectorContractId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx scopes the eager injectorLinks -> injector fetch triggered when the contract loads.
  public InjectorContract updateInjectorContract(
      TxCtx ctx,
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateInput input) {
    return injectorContractService.updateInjectorContract(injectorContractId, input);
  }

  /**
   * Updates the attack pattern tags and domains mappings for a contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the mapping update data
   * @return the updated injector contract
   */
  @PutMapping({
    INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping",
    TENANT_INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx scopes the eager injectorLinks -> injector fetch triggered when the contract loads.
  public InjectorContract updateInjectorContractMapping(
      TxCtx ctx,
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateMappingInput input) {
    return injectorContractService.updateInjectorContractTTPDomainsAndTags(
        injectorContractId, input);
  }

  /**
   * Deletes a custom injector contract.
   *
   * <p>Only custom (user-created) contracts can be deleted.
   *
   * @param injectorContractId the contract ID to delete
   */
  @DeleteMapping({
    INJECTOR_CONTRACT_URL + "/{injectorContractId}",
    TENANT_INJECTOR_CONTRACT_URL + "/{injectorContractId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  // ctx scopes the eager injectorLinks -> injector fetch triggered when the contract loads.
  public void deleteInjectorContract(TxCtx ctx, @PathVariable String injectorContractId) {
    this.injectorContractService.deleteInjectorContract(injectorContractId);
  }
}
