package io.openaev.rest.custom_dashboard;

import io.openaev.aop.RBAC;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(CustomDashboardApi.CUSTOM_DASHBOARDS_URI)
@RequiredArgsConstructor
public class CustomDashboardApi extends RestBehavior {

  public static final String CUSTOM_DASHBOARDS_URI = "/api/custom-dashboards";
  private final CustomDashboardService customDashboardService;

  // -- CRUD --

  @PostMapping
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<CustomDashboard> createCustomDashboard(
      @RequestBody @Valid @NotNull final CustomDashboardInput input) {
    return ResponseEntity.ok(
        this.customDashboardService.createCustomDashboard(
            input.toCustomDashboard(new CustomDashboard())));
  }

  @GetMapping
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<List<CustomDashboardOutput>> customDashboards() {
    return ResponseEntity.ok(this.customDashboardService.customDashboards());
  }

  @PostMapping("/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Page<CustomDashboard>> customDashboards(
      @RequestBody @NotNull @Valid final SearchPaginationInput searchPaginationInput) {
    return ResponseEntity.ok(this.customDashboardService.customDashboards(searchPaginationInput));
  }

  @GetMapping("/{customDashboardId}")
  @RBAC(
      resourceId = "#customDashboardId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<CustomDashboard> customDashboard(
      @PathVariable @NotBlank final String customDashboardId) {
    return ResponseEntity.ok(this.customDashboardService.customDashboard(customDashboardId));
  }

  @PutMapping("/{customDashboardId}")
  @RBAC(
      resourceId = "#customDashboardId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<CustomDashboard> updateCustomDashboard(
      @PathVariable @NotBlank final String customDashboardId,
      @RequestBody @Valid @NotNull final CustomDashboardInput input) {
    CustomDashboard existingCustomDashboard =
        this.customDashboardService.customDashboard(customDashboardId);
    CustomDashboard updatedCustomDashboard = input.toCustomDashboard(existingCustomDashboard);
    return ResponseEntity.ok(
        this.customDashboardService.updateCustomDashboard(updatedCustomDashboard));
  }

  @DeleteMapping("/{customDashboardId}")
  @RBAC(
      resourceId = "#customDashboardId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.DASHBOARD)
  public ResponseEntity<Void> deleteCustomDashboard(
      @PathVariable @NotBlank final String customDashboardId) {
    this.customDashboardService.deleteCustomDashboard(customDashboardId);
    return ResponseEntity.noContent().build();
  }

  // -- OPTION --

  @GetMapping("/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return this.customDashboardService.findAllAsOptions(searchText);
  }

  @PostMapping("/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.DASHBOARD)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return this.customDashboardService.findAllByIdsAsOptions(ids);
  }

  @GetMapping("/resource/{resourceId}/options")
  @RBAC(
      resourceId = "#resourceId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @Operation(summary = "Get the dashboard used in a resource")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Dashboard used in the resource")})
  public List<FilterUtilsJpa.Option> optionsByResourceId(
      @PathVariable @NotBlank final String resourceId) {
    return this.customDashboardService.findAllByResourceIdAsOptions(resourceId);
  }
}
