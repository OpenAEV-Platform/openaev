package io.openaev.rest.settings.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsPlatformWhitemarkUpdateInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_whitemark")
  @Schema(description = "The whitemark of the platform")
  private String platformWhitemark;
}
