package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ThemeInput {

  @JsonProperty("background_color")
  @Schema(description = "Background color of the theme")
  private String backgroundColor;

  @JsonProperty("paper_color")
  @Schema(description = "Paper color of the theme")
  private String paperColor;

  @JsonProperty("navigation_color")
  @Schema(description = "Navigation color of the theme")
  private String navigationColor;

  @JsonProperty("primary_color")
  @Schema(description = "Primary color of the theme")
  private String primaryColor;

  @JsonProperty("secondary_color")
  @Schema(description = "Secondary color of the theme")
  private String secondaryColor;

  @JsonProperty("accent_color")
  @Schema(description = "Accent color of the theme")
  private String accentColor;

  @JsonProperty("logo_url")
  @Schema(description = "Url of the logo")
  private String logoUrl;

  @JsonProperty("logo_url_collapsed")
  @Schema(description = "'true' if the logo needs to be collapsed")
  private String logoUrlCollapsed;

  @JsonProperty("logo_login_url")
  @Schema(description = "Url of the login logo")
  private String logoLoginUrl;

  @JsonProperty("login_aside_color")
  @Schema(description = "Solid color of the login page aside")
  private String loginAsideColor;

  @JsonProperty("login_aside_gradient_start")
  @Schema(description = "Gradient start color of the login page aside")
  private String loginAsideGradientStart;

  @JsonProperty("login_aside_gradient_end")
  @Schema(description = "Gradient end color of the login page aside")
  private String loginAsideGradientEnd;

  @JsonProperty("login_aside_image")
  @Schema(description = "Url of the login page aside background image")
  private String loginAsideImage;
}
