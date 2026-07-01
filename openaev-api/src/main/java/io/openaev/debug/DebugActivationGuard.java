package io.openaev.debug;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the production-barrier outcome. Exists whenever {@code enabled=true} (so a refusal is
 * visible), independently of {@link DebugEnabledCondition} which gates the actual debug beans.
 */
@Component
@ConditionalOnProperty(prefix = "openaev.debug", name = "enabled", havingValue = "true")
public class DebugActivationGuard {

  private static final Logger log = LoggerFactory.getLogger(DebugActivationGuard.class);

  private final Environment environment;

  public DebugActivationGuard(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  public void check() {
    boolean production = DebugEnabledCondition.isProduction(environment);
    boolean override =
        environment.getProperty("openaev.debug.allow-in-production", Boolean.class, false);

    if (production && !override) {
      log.error(
          "Debug mode was requested (openaev.debug.enabled=true) but REFUSED: production environment "
              + "detected (no dev/test/ci profile active). Set openaev.debug.allow-in-production=true "
              + "to override deliberately.");
    } else if (production) {
      log.warn(
          "Debug mode is ACTIVE IN PRODUCTION via openaev.debug.allow-in-production=true. This is a "
              + "deliberate override; do not leave it on.");
    }
  }
}
