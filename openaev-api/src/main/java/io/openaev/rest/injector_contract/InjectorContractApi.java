package io.openaev.rest.injector_contract;

import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.database.raw.RawInjectorsContrats;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.injector_contract.form.InjectorContractAddInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateInput;
import io.openaev.rest.injector_contract.form.InjectorContractUpdateMappingInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class InjectorContractApi extends RestBehavior {

  private final ObjectMapper mapper;

  public static final String INJECTOR_CONTRACT_URL = "/api/injector_contracts";

  private final InjectorContractService injectorContractService;

  @GetMapping(INJECTOR_CONTRACT_URL)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Iterable<RawInjectorsContrats> injectContracts() {
    return injectorContractService.getAllRawInjectContracts();
  }

  @PostMapping(INJECTOR_CONTRACT_URL + "/search")
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

  @GetMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract injectorContract(@PathVariable String injectorContractId) {
    return injectorContractService.getSingleInjectorContract(injectorContractId);
  }

  @PostMapping(INJECTOR_CONTRACT_URL)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract createInjectorContract(
      @Valid @RequestBody InjectorContractAddInput input) {
    return injectorContractService.createNewInjectorContract(input);
  }

  @PutMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract updateInjectorContract(
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateInput input) {
    return injectorContractService.updateInjectorContract(injectorContractId, input);
  }

  @PutMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}/mapping")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public InjectorContract updateInjectorContractMapping(
      @PathVariable String injectorContractId,
      @Valid @RequestBody InjectorContractUpdateMappingInput input) {
    return injectorContractService.updateAttackPatternMappings(injectorContractId, input);
  }

  @DeleteMapping(INJECTOR_CONTRACT_URL + "/{injectorContractId}")
  @RBAC(
      resourceId = "#injectorContractId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECTOR_CONTRACT)
  public void deleteInjectorContract(@PathVariable String injectorContractId) {
    this.injectorContractService.deleteInjectorContract(injectorContractId);
  }
}
