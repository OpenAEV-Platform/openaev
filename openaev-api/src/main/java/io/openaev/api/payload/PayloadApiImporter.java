package io.openaev.api.payload;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.jsonapi.ZipJsonApi;
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

@Slf4j
@RestController
@RequestMapping(PayloadApi.PAYLOAD_URI)
@RequiredArgsConstructor
public class PayloadApiImporter extends RestBehavior {

  private final ZipJsonApi<Payload> zipJsonApi;
  private final ImportService importService;

  @Operation(
      description =
          "Imports a payload from a JSON:API document. The name will be suffixed with '(import)' by default.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<JsonApiDocument<ResourceObject>> importJson(
      @RequestPart("file") @NotNull MultipartFile file) throws Exception {
    try {
      return zipJsonApi.handleImport(file, "payload_name");
    } catch (Exception ex) {
      log.warn("Fallback to old import due to {}", ex.getMessage(), ex);
      // Fall back to the legacy importer
      importService.handleFileImport(file, null, null);
      return ResponseEntity.ok().build();
    }
  }
}
