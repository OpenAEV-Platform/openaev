package io.openaev.api.threat_arsenal;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.MapperService;
import io.openaev.utils.CsvType;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL)
@RequiredArgsConstructor
public class ThreatArsenalApiExporter {

  private final ZipJsonApi<InjectorContract> zipJsonApi;
  private final MapperService mapperService;
  private final InjectorContractService injectorContractService;

  @Operation(summary = "Export threat arsenal actions as CSV")
  @PostMapping("/export/csv")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.THREAT_ARSENAL)
  public void exportCsv(
      @RequestBody @Valid final SearchPaginationInput input,
      HttpServletResponse response,
      TxCtx ctx) {
    mapperService.exportMappersCsv(CsvType.INJECTOR_CONTRACTS, input, response);
  }

  @Operation(
      description =
          "Exports a threat arsenal action in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{actionId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.THREAT_ARSENAL)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String actionId, TxCtx ctx)
      throws IOException {
    Map<String, IncludeOptions.IncludeMode> opts = new HashMap<>();
    opts.put("exclude from action export", IncludeOptions.IncludeMode.FALSE);
    // Also honor the payload-level exclusions (collector type, author): an
    // action export embeds the payload, and re-importing an included author
    // user/organization would duplicate it in the target environment.
    opts.put("exclude from payload export", IncludeOptions.IncludeMode.FALSE);
    IncludeOptions includeOptions = IncludeOptions.of(opts);
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    if (injectorContract.getPayload() == null) {
      throw new ElementNotFoundException(
          "Only threat arsenal items based on a payload can be exported");
    }
    // User-facing wording is "threat arsenal item": the technical JSON:API type
    // (injectors_contracts) must not leak into the downloaded filename.
    return zipJsonApi.handleExport(injectorContract, null, includeOptions, "threat_arsenal_item");
  }
}
