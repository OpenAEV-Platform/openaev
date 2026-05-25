package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsChatbotAiCguUpdateInput {
  @NotBlank
  @Pattern(regexp = "pending|enabled|disabled")
  @JsonProperty("status")
  @Schema(description = "Chatbot AI CGU acceptance status: pending, enabled, or disabled")
  private String status;
}
