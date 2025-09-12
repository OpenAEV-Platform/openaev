package io.openbas.api.custom_dashboard;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.ResourceType;
import io.openbas.jsonapi.JsonApiDocument;
import io.openbas.jsonapi.ResourceObject;
import io.openbas.jsonapi.ZipJsonApi;
import io.openbas.rest.custom_dashboard.CustomDashboardApi;
import io.openbas.rest.helper.RestBehavior;
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
    return zipJsonApi.handleImport(file, "custom_dashboard_name");
  }
}
