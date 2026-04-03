package io.openaev.rest.settings;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.form.*;
import io.openaev.rest.settings.response.CalderaSettings;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.CalderaSettingsService;
import io.openaev.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PlatformSettingsApi extends RestBehavior {

  private final PlatformSettingsService platformSettingsService;
  private final CalderaSettingsService calderaSettingsService;

  @GetMapping()
  @AccessControl(skipRBAC = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of settings")})
  @Operation(summary = "List settings", description = "Return the settings")
  public PlatformSettings settings() {
    return platformSettingsService.findSettings();
  }

  @GetMapping("/caldera")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of the first caldera instance settings")
      })
  @Operation(summary = "List caldera settings", description = "Return the settings")
  @Deprecated
  public List<CalderaSettings> getCalderaSettings() {
    return calderaSettingsService.getCalderaSettings();
  }

  @GetMapping("/version")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The platform version")})
  @Operation(summary = "Get platform version", description = "Return the platform version")
  public String platformVersion() {
    return platformSettingsService.getPlatformVersion();
  }

  @PutMapping()
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update settings", description = "Update the settings")
  public PlatformSettings updateBasicConfigurationSettings(
      @Valid @RequestBody SettingsUpdateInput input) {
    return platformSettingsService.updateBasicConfigurationSettings(input);
  }

  @PutMapping("/enterprise-edition")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
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
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update Whitemark settings", description = "Update the whitemark settings")
  public PlatformSettings updateSettingsPlatformWhitemark(
      @Valid @RequestBody SettingsPlatformWhitemarkUpdateInput input) {
    return platformSettingsService.updateSettingsPlatformWhitemark(input);
  }

  @PutMapping("/theme/light")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(
      summary = "Update light theme settings",
      description = "Update the light theme settings")
  public PlatformSettings updateThemeLight(@Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeLight(input);
  }

  @PutMapping("/theme/dark")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update dark theme settings", description = "Update the dark theme settings")
  public PlatformSettings updateThemeDark(@Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeDark(input);
  }

  @PutMapping("/policies")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update policies settings", description = "Update the policies settings")
  public PlatformSettings updateSettingsPolicies(@Valid @RequestBody PolicyInput input) {
    return platformSettingsService.updateSettingsPolicies(input);
  }
}
