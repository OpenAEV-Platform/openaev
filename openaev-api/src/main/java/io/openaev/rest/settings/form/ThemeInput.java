package io.openaev.rest.settings.form;

import static io.openaev.config.AppConfig.HEX_COLOR_FORMAT;
import static io.openaev.config.AppConfig.OPTIONAL_HEX_COLOR_REGEXP;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
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
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Background color of the theme")
  private String backgroundColor;

  @JsonProperty("paper_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Paper color of the theme")
  private String paperColor;

  @JsonProperty("navigation_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Navigation color of the theme")
  private String navigationColor;

  @JsonProperty("primary_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Primary color of the theme")
  private String primaryColor;

  @JsonProperty("secondary_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Secondary color of the theme")
  private String secondaryColor;

  @JsonProperty("accent_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Accent color of the theme")
  private String accentColor;

  @JsonProperty("text_color")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Text color of the theme")
  private String textColor;

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
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Solid color of the login page aside")
  private String loginAsideColor;

  @JsonProperty("login_aside_gradient_start")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Gradient start color of the login page aside")
  private String loginAsideGradientStart;

  @JsonProperty("login_aside_gradient_end")
  @Pattern(regexp = OPTIONAL_HEX_COLOR_REGEXP, message = HEX_COLOR_FORMAT)
  @Schema(description = "Gradient end color of the login page aside")
  private String loginAsideGradientEnd;

  @JsonProperty("login_aside_image")
  @Schema(description = "Url of the login page aside background image")
  private String loginAsideImage;
}
