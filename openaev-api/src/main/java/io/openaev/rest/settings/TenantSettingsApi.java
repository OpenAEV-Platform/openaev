package io.openaev.rest.settings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.query.*;
import io.openaev.rest.custom_dashboard.CustomDashboardTenantService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.form.TenantSettingsUpdateInput;
import io.openaev.rest.settings.form.ThemeInput;
import io.openaev.rest.settings.response.TenantSettingsOutput;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.es.EntitiesPaginationInput;
import io.openaev.utils.es.WidgetToEntitiesInput;
import io.openaev.utils.es.WidgetToEntitiesOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(TENANT_PREFIX + "/tenant-settings")
@RequiredArgsConstructor
@UserRoleDescription
@Tag(name = "Tenant Settings", description = "Endpoints to manage tenant-scoped settings")
public class TenantSettingsApi extends RestBehavior {

  private final TenantSettingsService tenantSettingsService;
  private final CustomDashboardTenantService customDashboardTenantService;

  // -- READ --

  @GetMapping
  @AccessControl(skipRBAC = true)
  @LogExecutionTime
  @Operation(
      summary = "Get tenant settings",
      description = "Return the tenant settings with optional platform fallback")
  @ApiResponses(@ApiResponse(responseCode = "200", description = "The tenant settings"))
  public TenantSettingsOutput findSettings(@PathVariable String tenantId) {
    return tenantSettingsService.findSettings(tenantId);
  }

  // -- UPDATE --

  @PutMapping
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(
      summary = "Update tenant settings",
      description = "Update the tenant settings (home dashboard)")
  @ApiResponses(@ApiResponse(responseCode = "200", description = "The updated tenant settings"))
  public TenantSettingsOutput updateSettings(
      @PathVariable String tenantId, @Valid @RequestBody TenantSettingsUpdateInput input) {
    return tenantSettingsService.updateSettings(tenantId, input);
  }

  // -- THEME --

  @PutMapping("/theme/light")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(
      summary = "Update tenant light theme",
      description = "Update the light theme for this tenant")
  @ApiResponses(@ApiResponse(responseCode = "200", description = "The updated tenant settings"))
  public TenantSettingsOutput updateThemeLight(
      @PathVariable String tenantId, @Valid @RequestBody ThemeInput input) {
    tenantSettingsService.updateTheme(tenantId, TenantSettingsService.THEME_TYPE_LIGHT, input);
    return tenantSettingsService.findSettings(tenantId);
  }

  @PutMapping("/theme/dark")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(
      summary = "Update tenant dark theme",
      description = "Update the dark theme for this tenant")
  @ApiResponses(@ApiResponse(responseCode = "200", description = "The updated tenant settings"))
  public TenantSettingsOutput updateThemeDark(
      @PathVariable String tenantId, @Valid @RequestBody ThemeInput input) {
    tenantSettingsService.updateTheme(tenantId, TenantSettingsService.THEME_TYPE_DARK, input);
    return tenantSettingsService.findSettings(tenantId);
  }

  // -- HOME DASHBOARD --

  @GetMapping("/home-dashboard")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(
      summary = "Get tenant home dashboard",
      description = "Return the home dashboard configured for this tenant")
  public ResponseEntity<CustomDashboard> homeDashboard(@PathVariable String tenantId) {
    return ResponseEntity.ok(
        customDashboardTenantService.findTenantHomeDashboard(tenantId).orElse(null));
  }

  @PostMapping("/home-dashboard/count/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget count")
  public EsCountInterval homeDashboardCount(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardCount(tenantId, widgetId, parameters);
  }

  @PostMapping("/home-dashboard/average/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget average")
  public EsAvgs homeDashboardAverage(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardAverage(tenantId, widgetId, parameters);
  }

  @PostMapping("/home-dashboard/series/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget series")
  public List<EsSeries> homeDashboardSeries(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardSeries(tenantId, widgetId, parameters);
  }

  @PostMapping("/home-dashboard/entities/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget entities")
  public EsEntities homeDashboardEntities(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) EntitiesPaginationInput input) {
    return customDashboardTenantService.homeDashboardEntities(tenantId, widgetId, input);
  }

  @PostMapping("/home-dashboard/entities-runtime/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget entities runtime")
  public WidgetToEntitiesOutput homeWidgetToEntitiesRuntime(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @Valid @RequestBody WidgetToEntitiesInput input) {
    return customDashboardTenantService.homeDashboardEntitiesRuntime(tenantId, widgetId, input);
  }

  @PostMapping("/home-dashboard/attack-paths/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget attack paths")
  public List<EsAttackPath> homeDashboardAttackPaths(
      @PathVariable String tenantId,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return customDashboardTenantService.homeDashboardAttackPaths(tenantId, widgetId, parameters);
  }
}
