package io.openaev.rest.settings.response;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.settings.form.PolicyInput;
import io.openaev.rest.settings.form.ThemeInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class PublicPlatformSettings {

  @NotBlank
  @JsonProperty("platform_theme")
  @Schema(description = "Theme of the platform")
  private String platformTheme;

  @NotBlank
  @JsonProperty("platform_lang")
  @Schema(description = "Language of the platform")
  private String platformLang;

  @JsonProperty("platform_openid_providers")
  @Schema(description = "List of OpenID providers")
  private List<OAuthProvider> platformOpenIdProviders;

  @JsonProperty("platform_saml2_providers")
  @Schema(description = "List of Saml2 providers")
  private List<OAuthProvider> platformSaml2Providers;

  @JsonProperty("auth_openid_enable")
  @Schema(description = "True if OpenID is enabled")
  private Boolean authOpenidEnable;

  @JsonProperty("auth_saml2_enable")
  @Schema(description = "True if Saml2 is enabled")
  private Boolean authSaml2Enable;

  @JsonProperty("auth_local_enable")
  @Schema(description = "True if local authentication is enabled")
  private Boolean authLocalEnable;

  @JsonProperty("platform_light_theme")
  @Schema(description = "Definition of the light theme")
  private ThemeInput themeLight;

  @JsonProperty("platform_dark_theme")
  @Schema(description = "Definition of the dark theme")
  private ThemeInput themeDark;

  @JsonProperty("platform_policies")
  @Schema(description = "Policies of the platform")
  private PolicyInput policies;

  @JsonProperty("platform_banner_by_level")
  @Getter(NONE)
  @Schema(
      description =
          "Map of the messages to display on the screen by their level (the level available are DEBUG, INFO, WARN, ERROR, FATAL)")
  private Map<String, List<String>> platformBannerByLevel;

  public Map<String, List<String>> getPlatformBannerByLevel() {
    Map<String, List<String>> platformBannerByLevelLowerCase = new HashMap<>();
    if (this.platformBannerByLevel != null) {
      this.platformBannerByLevel.forEach(
          (key, value) -> platformBannerByLevelLowerCase.put(key.toLowerCase(), value));
      return platformBannerByLevelLowerCase;
    }
    return null;
  }

  @JsonProperty("enabled_dev_features")
  @Schema(description = "List of enabled dev features")
  private List<PreviewFeature> enabledDevFeatures = new ArrayList<>();

  @JsonProperty("platform_whitemark")
  @Schema(description = "'true' if the platform has the whitemark activated")
  private String platformWhitemark;
}
