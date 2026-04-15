package io.openaev.rest.settings.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_name")
  @Schema(description = "Name of the platform")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_theme")
  @Schema(description = "Theme of the platform")
  private String theme;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_lang")
  @Schema(description = "Language of the platform")
  private String lang;
}
