package io.openaev.api.injector_contract;

import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping(InjectorContractApi.INJECTOR_CONTRACT_URL)
public class InjectorContractApi extends RestBehavior {

  public static final String INJECTOR_CONTRACT_URL = "/api/injector_contracts";

  private final InjectorContractService injectorContractService;
  private final InjectorContractDomainStatsService injectorContractDomainStatsService;

  // -- CREATE --

  /**
   * Creates a new custom injector contract.
   */
  @PostMapping
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract createInjectorContract(
      @Valid @RequestBody InjectorContractAddInput input) {
    return injectorContractService.createNewInjectorContract(input);
  }

  // -- READ --


  /**
   * Retrieve all raw injector contracts.
   */
  @GetMapping
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Iterable<RawInjectorsContracts> injectContracts() {
    return injectorContractService.getAllRawInjectContracts();
  }

  /**
   * Retrieves a specific injector contract by ID
   */
  @GetMapping("/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract injectorContract(@PathVariable String injectorContractId) {
    return injectorContractService.getSingleInjectorContract(injectorContractId);
  }

  /**
   * Search injector contracts with pagination and filtering
   *
   * <p>Can return either full or base details based on the input flag.
   */
  @PostMapping("/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Page<? extends InjectorContractBaseOutput> injectorContracts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    if (input.isIncludeFullDetails()) {
      return buildPaginationCriteriaBuilder(
          this.injectorContractService::getSinglePageFullDetails,
          handleArchitectureFilter(input),
          InjectorContract.class);
    } else {
      return buildPaginationCriteriaBuilder(
          this.injectorContractService::getSinglePageBaseDetails,
          handleArchitectureFilter(input),
          InjectorContract.class);
    }
  }

    @PostMapping(INJECTOR_CONTRACT_URL + "/domain-counts")
    @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
    public List<InjectorContractDomainCountOutput> getDomainCounts(
            @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
        SearchPaginationInput filtered = handleArchitectureFilter(input);
        return injectorContractService.getDomainCounts(filtered);
    }

  // -- UPDATE --

  /**
   * Updates an existing injector contract
   */
  @PutMapping("/{injectorContractId}")
  @RBAC(
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
   */
  @PutMapping("/{injectorContractId}/mapping")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract updateInjectorContractMapping(
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateMappingInput input) {
    return injectorContractService.updateAttackPatternMappings(injectorContractId, input);
  }

  // -- DELETE --

  /**
   * Deletes a custom injector contract.
   *
   * <p>Only custom (user-created) contracts can be deleted.
   */
  @DeleteMapping("/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public void deleteInjectorContract(@PathVariable String injectorContractId) {
    this.injectorContractService.deleteInjectorContract(injectorContractId);
  }
}
