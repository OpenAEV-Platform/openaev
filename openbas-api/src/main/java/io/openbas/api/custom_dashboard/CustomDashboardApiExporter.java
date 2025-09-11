package io.openbas.api.custom_dashboard;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.ResourceType;
import io.openbas.jsonapi.ZipJsonApi;
import io.openbas.rest.custom_dashboard.CustomDashboardApi;
import io.openbas.rest.custom_dashboard.CustomDashboardService;
import io.openbas.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CustomDashboardApi.CUSTOM_DASHBOARDS_URI)
@RequiredArgsConstructor
public class CustomDashboardApiExporter extends RestBehavior {

  private final CustomDashboardService customDashboardService;
  private final ZipJsonApi<CustomDashboard> zipJsonApi;

  @Operation(
      description =
          "Exports a custom dashboard in JSON:API format, optionally including related entities.")
  @GetMapping(value = "/{customDashboardId}/export", produces = "application/zip")
  @Transactional(readOnly = true)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<byte[]> export(@PathVariable @NotBlank final String customDashboardId)
      throws IOException {
    CustomDashboard customDashboard = customDashboardService.customDashboard(customDashboardId);
    return zipJsonApi.handleExport(customDashboard);
  }
}
