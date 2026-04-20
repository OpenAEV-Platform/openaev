package io.openaev.api.threat_arsenal;

import io.openaev.aop.AccessControl;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping({ThreatArsenalApi.THREAT_ARSENAL_URL, ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL})
@RequiredArgsConstructor
public class ThreatArsenalApiImporter {

  private final ZipJsonApi<InjectorContract> zipJsonApi;
  private final ThreatArsenalMapper threatArsenalMapper;

  /**
   * Imports a threat arsenal action from a JSON:API document. The exported format includes the
   * injector contract with its related domains, tags, and attack patterns. The name will be
   * suffixed with '(Import)' by default.
   *
   * <p>The payload import endpoint ({@code /api/payloads/import}) remains available for importing
   * legacy payload-based exports that embed {@code payload_tags}, {@code payloads_attack_patterns},
   * and {@code payloads_domains}.
   *
   * @param file the ZIP file containing the JSON:API document
   * @return the imported threat arsenal action
   */
  @Operation(
      description =
          "Imports a threat arsenal action (injector contract) from a JSON:API document. "
              + "Domains, tags, and attack patterns are included in the injector contract model.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ThreatArsenalAction importJson(@RequestPart("file") @NotNull MultipartFile file)
      throws Exception {
    ZipJsonService.ImportOutput<InjectorContract> response =
        zipJsonApi.handleImport(
            file,
            "injector_contract_labels",
            null,
            contract -> {
              contract.setId(UUID.randomUUID().toString());
              // Append (Import) suffix to all label values
              if (contract.getLabels() != null) {
                Map<String, String> updatedLabels = new HashMap<>(contract.getLabels());
                updatedLabels.replaceAll((key, value) -> value + " (Import)");
                contract.setLabels(updatedLabels);
              }
              // Append (Import) suffix to the payload name if present
              if (contract.getPayload() != null && contract.getPayload().getName() != null) {
                contract.getPayload().setName(contract.getPayload().getName() + " (Import)");
              }
              return contract;
            });
    return threatArsenalMapper.toThreatArsenalAction(response.persistedData());
  }
}
