package io.openaev.rest.settings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.context.TxCtx;
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
import org.springframework.transaction.annotation.Transactional;
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

  @Transactional(readOnly = true)
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

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
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

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
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

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
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

  @Transactional(readOnly = true)
  @GetMapping("/home-dashboard")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(
      summary = "Get tenant home dashboard",
      description = "Return the home dashboard configured for this tenant")
  public ResponseEntity<CustomDashboard> homeDashboard(@PathVariable String tenantId) {
    return ResponseEntity.ok(
        customDashboardTenantService.findTenantHomeDashboard(tenantId).orElse(null));
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/count/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget count")
  public EsCountInterval homeDashboardCount(TxCtx ctx,
      @PathVariable String tenantId,
                                            @PathVariable final String widgetId,
                                            @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardCount(ctx, tenantId, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/average/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget average")
  public EsAvgs homeDashboardAverage(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardAverage(ctx, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/series/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget series")
  public List<EsSeries> homeDashboardSeries(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardTenantService.homeDashboardSeries(ctx, widgetId, parameters);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/entities/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget entities")
  public EsEntities homeDashboardEntities(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) EntitiesPaginationInput input) {
    return customDashboardTenantService.homeDashboardEntities(ctx, widgetId, input);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/entities-runtime/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget entities runtime")
  public WidgetToEntitiesOutput homeWidgetToEntitiesRuntime(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @Valid @RequestBody WidgetToEntitiesInput input) {
    return customDashboardTenantService.homeDashboardEntitiesRuntime(ctx, widgetId, input);
  }

  @jakarta.transaction.Transactional(rollbackOn = Exception.class)
  @PostMapping("/home-dashboard/attack-paths/{widgetId}")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @LogExecutionTime
  @Operation(summary = "Get tenant home dashboard widget attack paths")
  public List<EsAttackPath> homeDashboardAttackPaths(
          TxCtx ctx,
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return customDashboardTenantService.homeDashboardAttackPaths(ctx, widgetId, parameters);
  }
}
