package io.openaev.api.threat_arsenal;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ThreatArsenalApi.THREAT_ARSENAL_URL, ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL})
@RequiredArgsConstructor
public class ThreatArsenalApiExporter {

  private final ZipJsonApi<InjectorContract> zipJsonApi;
  private final InjectorContractService injectorContractService;

  @Operation(
      description =
          "Exports a threat arsenal action in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{actionId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String actionId)
      throws IOException {
    Map<String, IncludeOptions.IncludeMode> opts = new HashMap<>();
    opts.put("exclude from action export", IncludeOptions.IncludeMode.FALSE);
    IncludeOptions includeOptions = IncludeOptions.of(opts);
    InjectorContract injectorContract = injectorContractService.injectorContract(actionId);
    if (injectorContract.getPayload() == null) {
      throw new ElementNotFoundException(
          "Only injector contract based on payload can be exported ");
    }
    return zipJsonApi.handleExport(injectorContract, null, includeOptions);
  }
}
