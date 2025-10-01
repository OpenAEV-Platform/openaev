package io.openaev.rest.settings;

import io.openaev.aop.RBAC;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.ResourceType;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.query.EsAttackPath;
import io.openaev.engine.query.EsCountInterval;
import io.openaev.engine.query.EsSeries;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.dashboard.model.WidgetToEntitiesInput;
import io.openaev.rest.dashboard.model.WidgetToEntitiesOutput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.form.*;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/settings")
@RestController
@UserRoleDescription
@Tag(
    name = "Settings management",
    description = "Endpoints to manage settings",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about settings",
            url = "https://docs.openaev.io/latest/administration/parameters/"))
public class PlatformSettingsApi extends RestBehavior {

  private PlatformSettingsService platformSettingsService;
  private CustomDashboardService customDashboardService;

  @Autowired
  public void setPlatformSettingsService(PlatformSettingsService platformSettingsService) {
    this.platformSettingsService = platformSettingsService;
  }

  @Autowired
  public void setCustomDashboardService(CustomDashboardService customDashboardService) {
    this.customDashboardService = customDashboardService;
  }

  @GetMapping()
  @RBAC(skipRBAC = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of settings")})
  @Operation(summary = "List settings", description = "Return the settings")
  public PlatformSettings settings() {
    return platformSettingsService.findSettings();
  }

  @PutMapping()
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update settings", description = "Update the settings")
  public PlatformSettings updateBasicConfigurationSettings(
      @Valid @RequestBody SettingsUpdateInput input) {
    return platformSettingsService.updateBasicConfigurationSettings(input);
  }

  @PutMapping("/enterprise-edition")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The updated settings"),
        @ApiResponse(responseCode = "400", description = "Invalid certificate")
      })
  @Operation(summary = "Update EE settings", description = "Update the enterprise edition settings")
  public PlatformSettings updateSettingsEnterpriseEdition(
      @Valid @RequestBody SettingsEnterpriseEditionUpdateInput input) throws Exception {
    return platformSettingsService.updateSettingsEnterpriseEdition(input);
  }

  @PutMapping("/platform_whitemark")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update Whitemark settings", description = "Update the whitemark settings")
  public PlatformSettings updateSettingsPlatformWhitemark(
      @Valid @RequestBody SettingsPlatformWhitemarkUpdateInput input) {
    return platformSettingsService.updateSettingsPlatformWhitemark(input);
  }

  @PutMapping("/theme/light")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(
      summary = "Update light theme settings",
      description = "Update the light theme settings")
  public PlatformSettings updateThemeLight(@Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeLight(input);
  }

  @PutMapping("/theme/dark")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update dark theme settings", description = "Update the dark theme settings")
  public PlatformSettings updateThemeDark(@Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeDark(input);
  }

  @PutMapping("/policies")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update policies settings", description = "Update the policies settings")
  public PlatformSettings updateSettingsPolicies(@Valid @RequestBody PolicyInput input) {
    return platformSettingsService.updateSettingsPolicies(input);
  }

  @GetMapping("/home-dashboard")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<CustomDashboard> homeDashboard() {
    return ResponseEntity.ok(customDashboardService.findHomeDashboard().orElse(null));
  }

  @PostMapping("/home-dashboard/count/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public EsCountInterval homeDashboardCount(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardCount(widgetId, parameters);
  }

  @PostMapping("/home-dashboard/series/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsSeries> homeDashboardSeries(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardSeries(widgetId, parameters);
  }

  @PostMapping("/home-dashboard/entities/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsBase> homeDashboardEntities(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardEntities(widgetId, parameters);
  }

  @PostMapping("/home-dashboard/entities-runtime/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public WidgetToEntitiesOutput homeWidgetToEntitiesRuntime(
      @PathVariable final String widgetId, @Valid @RequestBody WidgetToEntitiesInput input) {
    return customDashboardService.homeWidgetToEntitiesRuntimeOnResourceId(widgetId, input);
  }

  @PostMapping("/home-dashboard/attack-paths/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsAttackPath> homeDashboardAttackPaths(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return customDashboardService.homeDashboardAttackPaths(widgetId, parameters);
  }
}
