package io.openaev.api.custom_dashboard;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.rest.custom_dashboard.CustomDashboardApi;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(CustomDashboardApi.CUSTOM_DASHBOARDS_URI)
@RequiredArgsConstructor
public class CustomDashboardApiImporter extends RestBehavior {

  private final ZipJsonApi<CustomDashboard> zipJsonApi;

  @Operation(
      description =
          "Imports a custom dashboard from a JSON:API document. The name will be suffixed with '(import)' by default.")
  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<JsonApiDocument<ResourceObject>> importJson(
      @RequestPart("file") @NotNull MultipartFile file) throws IOException {
    return zipJsonApi.handleImport(
        file, "custom_dashboard_name", null, CustomDashboardService::sanityCheck);
  }
}
