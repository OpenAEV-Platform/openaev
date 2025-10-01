package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class CheckScenarioRulesOutput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("rules_found")
  @Schema(description = "Are there rules that can be applied?")
  boolean rulesFound = false;
}
