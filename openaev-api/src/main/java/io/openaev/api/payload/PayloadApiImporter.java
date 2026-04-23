package io.openaev.api.payload;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.payload.PayloadApi;
import io.openaev.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping({PayloadApi.PAYLOAD_URI, PayloadApi.TENANT_PAYLOAD_URI})
@RequiredArgsConstructor
public class PayloadApiImporter extends RestBehavior {

  /**
   * Import options that configure the collector relationship on detection remediations to only
   * include collectors that already exist in the target database. If a collector is not found by
   * its business key, the entire detection remediation is skipped.
   */
  private static final IncludeOptions IMPORT_OPTIONS =
          IncludeOptions.of(
                  Map.of("detection_remediation_collector_type", IncludeOptions.IncludeMode.IF_EXISTS_IN_DB));


  private final PayloadImportService payloadImportService;
  private final ImportService importService;

  /**
   * Imports a payload from a JSON:API document (legacy format).
   *
   * <p>Legacy payload exports may contain {@code payload_attack_patterns}, {@code payload_domains},
   * and {@code payload_tags} as relationships. Since these fields now live on {@code
   * InjectorContract}, they are extracted from the document and passed to the synchronisation
   * method that creates/updates the associated injector contract.
   */
  @Operation(
      description =
          "Imports a payload from a JSON:API document. The name will be suffixed with '(Import)' by default.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<JsonApiDocument<ResourceObject>> importJson(
      @RequestPart("file") @NotNull MultipartFile file) throws Exception {
    try {
      PayloadImportService.PayloadImportResult result = payloadImportService.importPayload(file);
      return ResponseEntity.ok(result.payloadOutput().jsonApiDocument());
    } catch (Exception ex) {
      log.warn("Fallback to old import due to {}", ex.getMessage(), ex);
      importService.handleFileImport(file, null, null);
      return ResponseEntity.ok().build();
    }
  }
}
