package io.openaev.service.stix;

import java.time.Duration;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the OpenCTI hunt validation loop: once an inject emulating an ATT&CK technique
 * has a computed verdict from a security platform, OpenAEV asks OpenCTI to run its hunts covering
 * that technique on that platform, over the emulation window.
 *
 * <p>Properties are loaded with the {@code openaev.security-coverage.hunt-validation.*} prefix.
 * Out-of-range values fall back to the nearest valid value instead of failing the startup.
 *
 * <pre>{@code
 * openaev:
 *   security-coverage:
 *     hunt-validation:
 *       enabled: true
 *       window-padding: PT5M
 *       max-age: P7D
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "openaev.security-coverage.hunt-validation")
@Setter
public class SecurityCoverageHuntValidationConfig {

  static final Duration DEFAULT_WINDOW_PADDING = Duration.ofMinutes(5);
  static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
  static final int DEFAULT_BATCH_SIZE = 50;
  static final int DEFAULT_MAX_ATTEMPTS = 5;
  static final Duration DEFAULT_MAX_AGE = Duration.ofDays(7);

  /** Off by default: the OpenCTI hunt validation requires OpenCTI Enterprise Edition. */
  private boolean enabled = false;

  /** Added before the start and after the end of the inject execution window. */
  private Duration windowPadding = DEFAULT_WINDOW_PADDING;

  /** Bound of the connect, the TLS handshake and every read of one OpenCTI call. */
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

  /** Maximum number of validations sent per tenant and per delivery run. */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /** Attempts OpenCTI refuses before a validation is given up (outages do not count). */
  private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

  /** Time after its planning a validation still not delivered is given up, outages included. */
  private Duration maxAge = DEFAULT_MAX_AGE;

  public boolean isEnabled() {
    return enabled;
  }

  /** The configured padding, or zero when negative. */
  public Duration getWindowPadding() {
    return windowPadding == null || windowPadding.isNegative() ? Duration.ZERO : windowPadding;
  }

  /** The configured timeout, or the default when missing, zero or negative. */
  public Duration getRequestTimeout() {
    return requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
        ? DEFAULT_REQUEST_TIMEOUT
        : requestTimeout;
  }

  /** The configured batch size, at least one. */
  public int getBatchSize() {
    return Math.max(1, batchSize);
  }

  /** The configured attempts, at least one. */
  public int getMaxAttempts() {
    return Math.max(1, maxAttempts);
  }

  /** The configured maximum age, or the default when missing, zero or negative. */
  public Duration getMaxAge() {
    return maxAge == null || maxAge.isZero() || maxAge.isNegative() ? DEFAULT_MAX_AGE : maxAge;
  }
}
