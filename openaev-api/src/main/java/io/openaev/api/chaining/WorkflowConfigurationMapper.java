package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.database.model.WorkflowConfiguration;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConfigurationMapper {

  /**
   * Applies a {@link WorkflowConfigurationInput} DTO onto an existing {@link WorkflowConfiguration}
   * entity by copying each flat field directly.
   *
   * @param input the input DTO to read from
   * @param configuration the entity to update in place
   */
  public void applyInput(WorkflowConfigurationInput input, WorkflowConfiguration configuration) {
    // Rate limit
    configuration.setRateLimitEnabled(input.isRateLimitEnabled());
    configuration.setMaxAttempts(input.getMaxAttempts());
    configuration.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
    // Timeout
    configuration.setTimeoutEnabled(input.isTimeoutEnabled());
    configuration.setTimeoutSeconds(input.getTimeoutSeconds());
    // Safe mode
    configuration.setSafeModeEnabled(input.isSafeModeEnabled());
  }

  /**
   * Maps a {@link WorkflowConfiguration} entity to its {@link WorkflowConfigurationOutput} DTO by
   * copying each flat field directly.
   *
   * @param configuration the entity to map
   * @return the mapped output DTO
   */
  public WorkflowConfigurationOutput toOutput(WorkflowConfiguration configuration) {
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(configuration.isRateLimitEnabled())
        .maxAttempts(configuration.getMaxAttempts())
        .maxTemporalRateSeconds(configuration.getMaxTemporalRateSeconds())
        .timeoutEnabled(configuration.isTimeoutEnabled())
        .timeoutSeconds(configuration.getTimeoutSeconds())
        .safeModeEnabled(configuration.isSafeModeEnabled())
        .build();
  }
}
