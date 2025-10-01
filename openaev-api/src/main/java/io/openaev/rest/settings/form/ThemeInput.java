package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

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
  private String NavigationColor;

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
}
