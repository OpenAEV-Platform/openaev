package io.openaev.rest.injector_contract;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
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
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class InjectorContractApi extends RestBehavior {

  public static final String INJECTOR_CONTRACT_URL = "/api/injector_contracts";
  private static final String TENANT_INJECTOR_CONTRACT_URL = TENANT_PREFIX + "/injector_contracts";

  private final InjectorContractService injectorContractService;

  @GetMapping({INJECTOR_CONTRACT_URL, TENANT_INJECTOR_CONTRACT_URL})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Iterable<RawInjectorsContracts> injectContracts() {
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
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Page<? extends InjectorContractBaseOutput> injectorContracts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            this.injectorContractService.getSinglePage(
                spec,
                specCount,
                pageable,
                input.isIncludeFullDetails()
                    ? InjectorContractService.OutputMode.FULL
                    : InjectorContractService.OutputMode.BASE),
        handleArchitectureFilter(input),
        InjectorContract.class);
  }

  @PostMapping({
    INJECTOR_CONTRACT_URL + "/domain-counts",
    TENANT_INJECTOR_CONTRACT_URL + "/domain-counts"
  })
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public List<InjectorContractDomainCountOutput> getDomainCounts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    SearchPaginationInput filtered = handleArchitectureFilter(input);
    return injectorContractService.getDomainCounts(filtered);
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
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract injectorContract(@PathVariable String injectorContractId) {
    return injectorContractService.getSingleInjectorContract(injectorContractId);
  }

  /**
   * Creates a new custom injector contract.
   *
   * @param input the creation input with contract details
   * @return the created injector contract
   */
  @PostMapping({INJECTOR_CONTRACT_URL, TENANT_INJECTOR_CONTRACT_URL})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract createInjectorContract(
      @Valid @RequestBody InjectorContractAddInput input) {
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
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract updateInjectorContract(
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateInput input) {
    return injectorContractService.updateInjectorContract(injectorContractId, input);
  }

  /**
   * Updates the attack pattern and vulnerability mappings for a contract.
   *
   * @param injectorContractId the contract ID to update
   * @param input the mapping update data
   * @return the updated injector contract
   */
  @PutMapping({
    INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping",
    TENANT_INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping"
  })
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract updateInjectorContractMapping(
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateMappingInput input) {
    return injectorContractService.updateAttackPatternMappings(injectorContractId, input);
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
  @AccessControl(
      resourceId = "#injectorContractId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public void deleteInjectorContract(@PathVariable String injectorContractId) {
    this.injectorContractService.deleteInjectorContract(injectorContractId);
  }
}
