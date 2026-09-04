package io.openaev.rest.settings;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UserRoleDescription;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.form.*;
import io.openaev.rest.settings.response.CalderaSettings;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.rest.settings.response.PublicPlatformSettings;
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
import org.springframework.transaction.annotation.Transactional;
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

  // -- READ --

  @GetMapping("/public")
  @AccessControl(skipRBAC = true)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Non-sensitive settings for login page and initial rendering")
      })
  @Operation(
      summary = "List public settings",
      description =
          "Return only non-sensitive settings (auth providers, theme, language, policies)")
  @Transactional
  public PublicPlatformSettings publicSettings(TxCtx ctx) {
    return platformSettingsService.findPublicSettings();
  }

  @GetMapping()
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of settings")})
  @Operation(
      summary = "List settings",
      description = "Return the full settings (authenticated users only)")
  @Transactional
  public PlatformSettings settings(TxCtx ctx) {
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
  @Transactional
  @Deprecated
  public List<CalderaSettings> getCalderaSettings(TxCtx ctx) {
    return calderaSettingsService.getCalderaSettings();
  }

  @GetMapping("/version")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The platform version")})
  @Operation(summary = "Get platform version", description = "Return the platform version")
  @Transactional
  public String platformVersion(TxCtx ctx) {
    return platformSettingsService.getPlatformVersion();
  }

  @PutMapping("/enterprise-edition")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The updated settings"),
        @ApiResponse(responseCode = "400", description = "Invalid certificate")
      })
  @Operation(summary = "Update EE settings", description = "Update the enterprise edition settings")
  public PlatformSettings updateSettingsEnterpriseEdition(
      TxCtx ctx, @Valid @RequestBody SettingsEnterpriseEditionUpdateInput input) throws Exception {
    return platformSettingsService.updateSettingsEnterpriseEdition(input);
  }

  @PutMapping("/platform_whitemark")
  @Transactional
  @AccessControl(
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_SETTING,
      isEnterpriseEdition = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update Whitemark settings", description = "Update the whitemark settings")
  public PlatformSettings updateSettingsPlatformWhitemark(
      TxCtx ctx, @Valid @RequestBody SettingsPlatformWhitemarkUpdateInput input) {
    return platformSettingsService.updateSettingsPlatformWhitemark(input);
  }

  @PutMapping("/sessions")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(
      summary = "Update session settings",
      description = "Update the session management settings (max concurrent sessions per user)")
  public PlatformSettings updateSettingsSessions(
      TxCtx ctx, @Valid @RequestBody SettingsSessionsUpdateInput input) {
    return platformSettingsService.updateSettingsSessions(input);
  }

  @PutMapping("/theme/light")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(
      summary = "Update light theme settings",
      description = "Update the light theme settings")
  public PlatformSettings updateThemeLight(TxCtx ctx, @Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeLight(input);
  }

  @PutMapping("/theme/dark")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update dark theme settings", description = "Update the dark theme settings")
  public PlatformSettings updateThemeDark(TxCtx ctx, @Valid @RequestBody ThemeInput input) {
    return platformSettingsService.updateThemeDark(input);
  }

  @PutMapping("/policies")
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(summary = "Update policies settings", description = "Update the policies settings")
  public PlatformSettings updateSettingsPolicies(TxCtx ctx, @Valid @RequestBody PolicyInput input) {
    return platformSettingsService.updateSettingsPolicies(input);
  }

  @PutMapping("/chatbot-ai-cgu")
  @Transactional
  @AccessControl(
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_SETTING,
      isEnterpriseEdition = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The updated settings")})
  @Operation(
      summary = "Update chatbot AI CGU status",
      description = "Accept or revoke the Filigran AI Terms for the chatbot feature")
  public PlatformSettings updateChatbotAiCguStatus(
      TxCtx ctx, @Valid @RequestBody SettingsChatbotAiCguUpdateInput input) {
    return platformSettingsService.updateChatbotAiCguStatus(input);
  }
}
