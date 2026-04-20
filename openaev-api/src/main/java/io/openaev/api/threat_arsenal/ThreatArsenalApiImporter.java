package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.api.payload.PayloadImportService;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

  private static final String INJECTOR_CONTRACTS_TYPE = "injectors_contracts";

  private final ZipJsonApi<InjectorContract> injectorContractZipJsonApi;
  private final PayloadImportService payloadImportService;
  private final ThreatArsenalMapper threatArsenalMapper;
  private final ObjectMapper objectMapper;

  /**
   * Imports a threat arsenal action from a JSON:API document. Accepts both injector contract
   * exports and legacy payload exports.
   *
   * <p>If the document type is {@code injectors_contracts}, the document is imported directly as
   * an injector contract. Any other type (e.g. payloads, commands, file_drops) is treated as a
   * legacy payload export and an injector contract is synchronised from it.
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
    if (isInjectorContractExport(file)) {
      return importFromInjectorContract(file);
    }
    return importFromPayload(file);
  }

  private ThreatArsenalAction importFromInjectorContract(MultipartFile file) throws Exception {
    ZipJsonService.ImportOutput<InjectorContract> response =
        injectorContractZipJsonApi.handleImport(
            file,
            "injector_contract_labels",
            null,
            contract -> {
              contract.setId(UUID.randomUUID().toString());
              if (contract.getLabels() != null) {
                Map<String, String> updatedLabels = new HashMap<>(contract.getLabels());
                updatedLabels.replaceAll((key, value) -> value + " (Import)");
                contract.setLabels(updatedLabels);
              }
              if (contract.getPayload() != null && contract.getPayload().getName() != null) {
                contract.getPayload().setName(contract.getPayload().getName() + " (Import)");
              }
              return contract;
            });
    return threatArsenalMapper.toThreatArsenalAction(response.persistedData());
  }

  private ThreatArsenalAction importFromPayload(MultipartFile file) throws Exception {
    PayloadImportService.PayloadImportResult result = payloadImportService.importPayload(file);
    return threatArsenalMapper.toThreatArsenalAction(result.injectorContract());
  }

  /**
   * Peeks into the ZIP to detect if the JSON:API document represents an injector contract export
   * by checking the {@code data.type} field. Any other type (e.g. payloads, commands, file_drops)
   * is treated as a legacy payload export.
   */
  private boolean isInjectorContractExport(MultipartFile file) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(file.getBytes()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
          byte[] content = zis.readAllBytes();
          JsonApiDocument<ResourceObject> doc =
              objectMapper.readValue(
                  content,
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(JsonApiDocument.class, ResourceObject.class));
          return doc.data() != null && INJECTOR_CONTRACTS_TYPE.equals(doc.data().type());
        }
        zis.closeEntry();
      }
    }
    return false;
  }
}
