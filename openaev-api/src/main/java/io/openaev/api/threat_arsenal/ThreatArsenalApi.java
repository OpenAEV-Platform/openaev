package io.openaev.api.threat_arsenal;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.threat_arsenal.dto.*;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.schema.model.PropertySchemaDTO;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ThreatArsenalApi {
  public static final String THREAT_ARSENAL_URL = "/api/threat_arsenals";
  public static final String TENANT_THREAT_ARSENAL_URL = TENANT_PREFIX + "/threat_arsenals";

  private final ThreatArsenalService threatArsenalService;

  @GetMapping({THREAT_ARSENAL_URL + "/{actionId}", TENANT_THREAT_ARSENAL_URL + "/{actionId}"})
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  public ThreatArsenalActionFullOutput threatArsenal(@PathVariable String actionId) {
    return threatArsenalService.findById(actionId);
  }

  @Operation(summary = "Get filterable property schemas for threat arsenal")
  @PostMapping({THREAT_ARSENAL_URL + "/schemas", TENANT_THREAT_ARSENAL_URL + "/schemas"})
  @AccessControl(skipRBAC = true)
  public List<PropertySchemaDTO> schemas(
      @RequestParam final boolean filterableOnly,
      @RequestBody @Valid @NotNull List<String> filterNames)
      throws ClassNotFoundException {
    return threatArsenalService.getSchemas(filterableOnly, filterNames);
  }

  @PostMapping({
    THREAT_ARSENAL_URL + "/domain-counts",
    TENANT_THREAT_ARSENAL_URL + "/domain-counts"
  })
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public List<InjectorContractDomainCountOutput> getDomainCounts(
      @RequestBody @Valid final SearchPaginationInput input) {
    return threatArsenalService.getDomainCounts(input);
  }

  @Operation(summary = "Search threat arsenal")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {
                        ThreatArsenalAction.class,
                        ThreatArsenalActionWithContentOutput.class,
                      })))
  @PostMapping({THREAT_ARSENAL_URL + "/search", TENANT_THREAT_ARSENAL_URL + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECTOR_CONTRACT)
  public Page<? extends InjectorContractBaseOutput> injectorContracts(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    InjectorContractService.OutputMode outputMode =
        input.isIncludeContentDetails()
            ? InjectorContractService.OutputMode.THREAT_ARSENAL_CONTENT
            : InjectorContractService.OutputMode.THREAT_ARSENAL;
    return this.threatArsenalService.searchInjectorContracts(outputMode, input);
  }

  @PostMapping({THREAT_ARSENAL_URL, TENANT_THREAT_ARSENAL_URL})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public ThreatArsenalAction createAction(
      @Valid @RequestBody ThreatArsenalActionCreateInput input) {
    return threatArsenalService.create(input);
  }

  @PutMapping({THREAT_ARSENAL_URL + "/{actionId}", TENANT_THREAT_ARSENAL_URL + "/{actionId}"})
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public ThreatArsenalAction updateAction(
      @NotBlank @PathVariable final String actionId,
      @Valid @RequestBody ThreatArsenalActionUpdateInput input) {
    return threatArsenalService.update(actionId, input);
  }

  @PostMapping({
    THREAT_ARSENAL_URL + "/{actionId}/duplicate",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/duplicate"
  })
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public ThreatArsenalAction duplicateAction(@NotBlank @PathVariable final String actionId) {
    return threatArsenalService.duplicate(actionId);
  }
}
