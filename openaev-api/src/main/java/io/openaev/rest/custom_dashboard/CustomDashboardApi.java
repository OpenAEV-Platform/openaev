package io.openaev.rest.custom_dashboard;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.rest.custom_dashboard.form.CustomDashboardOutput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({
  CustomDashboardApi.CUSTOM_DASHBOARDS_URI,
  CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI
})
@RequiredArgsConstructor
public class CustomDashboardApi extends RestBehavior {

  public static final String CUSTOM_DASHBOARDS_URI = "/api/custom-dashboards";
  public static final String TENANT_CUSTOM_DASHBOARDS_URI = TENANT_PREFIX + "/custom-dashboards";
  private final CustomDashboardService customDashboardService;

  // -- CRUD --

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<CustomDashboard> createCustomDashboard(
      TxCtx ctx, @RequestBody @Valid @NotNull final CustomDashboardInput input) {
    return ResponseEntity.ok(
        this.customDashboardService.createCustomDashboard(
            input.toCustomDashboard(CustomDashboard.fromTenant(ctx.tenantIdFromUri()))));
  }

  @GetMapping
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  @Transactional(readOnly = true)
  public ResponseEntity<List<CustomDashboardOutput>> customDashboards() {
    return ResponseEntity.ok(this.customDashboardService.customDashboards());
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Page<CustomDashboard>> customDashboards(
      @RequestBody @NotNull @Valid final SearchPaginationInput searchPaginationInput) {
    return ResponseEntity.ok(this.customDashboardService.customDashboards(searchPaginationInput));
  }

  @GetMapping("/{customDashboardId}")
  @AccessControl(
      resourceId = "#customDashboardId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  @Transactional(readOnly = true)
  public ResponseEntity<CustomDashboard> customDashboard(
      @PathVariable @NotBlank final String customDashboardId) {
    return ResponseEntity.ok(this.customDashboardService.customDashboard(customDashboardId));
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PutMapping("/{customDashboardId}")
  @AccessControl(
      resourceId = "#customDashboardId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<CustomDashboard> updateCustomDashboard(
      TxCtx ctx,
      @PathVariable @NotBlank final String customDashboardId,
      @RequestBody @Valid @NotNull final CustomDashboardInput input) {
    CustomDashboard existingCustomDashboard =
        this.customDashboardService.customDashboard(customDashboardId);
    CustomDashboard updatedCustomDashboard = input.toCustomDashboard(existingCustomDashboard);
    return ResponseEntity.ok(
        this.customDashboardService.updateCustomDashboard(updatedCustomDashboard));
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @DeleteMapping("/{customDashboardId}")
  @AccessControl(
      resourceId = "#customDashboardId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Void> deleteCustomDashboard(
      TxCtx ctx, @PathVariable @NotBlank final String customDashboardId) {
    this.customDashboardService.deleteCustomDashboard(ctx.tenantIdFromUri(), customDashboardId);
    return ResponseEntity.noContent().build();
  }

  // -- OPTION --

  @GetMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return this.customDashboardService.findAllAsOptions(searchText);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return this.customDashboardService.findAllByIdsAsOptions(ids);
  }

  @GetMapping("/resource/{resourceId}/options")
  @AccessControl(
      resourceId = "#resourceId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @Operation(summary = "Get the dashboard used in a resource")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Dashboard used in the resource")})
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsByResourceId(
      @PathVariable @NotBlank final String resourceId) {
    return this.customDashboardService.findAllByResourceIdAsOptions(resourceId);
  }
}
