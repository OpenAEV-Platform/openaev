package io.openbas.rest.settings;

import io.openbas.aop.RBAC;
import io.openbas.aop.UserRoleDescription;
import io.openbas.database.model.Action;
import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.ResourceType;
import io.openbas.engine.model.EsBase;
import io.openbas.engine.query.EsAttackPath;
import io.openbas.engine.query.EsSeries;
import io.openbas.rest.custom_dashboard.CustomDashboardService;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.settings.form.*;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.service.PlatformSettingsService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/settings")
@RestController
@UserRoleDescription
@Tag(
    name = "Settings management",
    description = "Endpoints to manage settings",
    externalDocs =
        @ExternalDocumentation(
            description = "Documentation about settings",
            url = "https://docs.openbas.io/latest/administration/parameters/"))
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

  @GetMapping("/home_dashboard")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public ResponseEntity<CustomDashboard> homeDashboard() {
    return ResponseEntity.ok(customDashboardService.findHomeDashboard().orElse(null));
  }

  @PostMapping("/home_dashboard/count/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public long homeDashboardCount(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardCount(widgetId, parameters);
  }

  @PostMapping("/home_dashboard/series/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsSeries> homeDashboardSeries(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardSeries(widgetId, parameters);
  }

  @PostMapping("/home_dashboard/entities/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsBase> homeDashboardEntities(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters) {
    return customDashboardService.homeDashboardEntities(widgetId, parameters);
  }

  @PostMapping("/home_dashboard/attack-paths/{widgetId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  public List<EsAttackPath> homeDashboardAttackPaths(
      @PathVariable final String widgetId,
      @RequestBody(required = false) Map<String, String> parameters)
      throws ExecutionException, InterruptedException {
    return customDashboardService.homeDashboardAttackPaths(widgetId, parameters);
  }
}
