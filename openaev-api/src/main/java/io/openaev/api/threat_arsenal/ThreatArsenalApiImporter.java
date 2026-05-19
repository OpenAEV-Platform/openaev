package io.openaev.api.threat_arsenal;

import io.openaev.aop.AccessControl;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
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

  private final ThreatArsenalImportService threatArsenalImportService;

  /**
   * Imports a threat arsenal action from a JSON:API document. Accepts both injector contract
   * exports and legacy payload exports.
   *
   * <p>If the document type is {@code injectors_contracts}, the document is imported directly as an
   * injector contract. Any other type (e.g. payloads, commands, file_drops) is treated as a legacy
   * payload export and an injector contract is synchronised from it.
   *
   * @param file the ZIP file containing the JSON:API document
   * @return the imported threat arsenal action
   */
  @Operation(
      description =
          "Imports a threat arsenal action from a JSON:API document. "
              + "Supports both injector contract and legacy payload export formats.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ThreatArsenalAction importJson(@RequestPart("file") @NotNull MultipartFile file)
      throws Exception {
    return threatArsenalImportService.importThreatArsenalAction(file);
  }
}
