package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsRuntimeUpdateInput {
  @NotEmpty
  @JsonProperty("settings")
  @Schema(
      description =
          "Runtime settings to store, by key (inject_execution_threshold_minutes,"
              + " agent_max_simultaneous_jobs, agent_queue_threshold, agent_auto_update_enabled)."
              + " A setting whose environment variable is set is refused")
  private Map<String, Object> settings;
}
