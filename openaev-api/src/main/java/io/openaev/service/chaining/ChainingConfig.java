package io.openaev.service.chaining;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the chaining engine.
 *
 * <p>Properties are loaded from the application configuration with the {@code openaev.chaining.*}
 * prefix.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * openaev:
 *   chaining:
 *     max-retry-count: 3
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "openaev.chaining")
@Data
public class ChainingConfig {

  /** Maximum number of times an event can be re-queued after a transactional failure. */
  private int maxRetryCount = 3;
}
