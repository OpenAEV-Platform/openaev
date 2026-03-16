package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.database.model.ChainingConfiguration;
import org.springframework.stereotype.Component;

@Component
public class ChainingConfigurationMapper {

  /**
   * Applies a {@link ChainingConfigurationInput} DTO onto an existing {@link ChainingConfiguration}
   * entity by copying each flat field directly.
   *
   * @param input the input DTO to read from
   * @param configuration the entity to update in place
   */
  public void applyInput(ChainingConfigurationInput input, ChainingConfiguration configuration) {
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
   * Maps a {@link ChainingConfiguration} entity to its {@link ChainingConfigurationOutput} DTO by
   * copying each flat field directly.
   *
   * @param configuration the entity to map
   * @return the mapped output DTO
   */
  public ChainingConfigurationOutput toOutput(ChainingConfiguration configuration) {
    return ChainingConfigurationOutput.builder()
        .rateLimitEnabled(configuration.isRateLimitEnabled())
        .maxAttempts(configuration.getMaxAttempts())
        .maxTemporalRateSeconds(configuration.getMaxTemporalRateSeconds())
        .timeoutEnabled(configuration.isTimeoutEnabled())
        .timeoutSeconds(configuration.getTimeoutSeconds())
        .safeModeEnabled(configuration.isSafeModeEnabled())
        .build();
  }
}
