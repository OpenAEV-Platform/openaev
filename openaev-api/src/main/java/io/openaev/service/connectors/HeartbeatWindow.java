package io.openaev.service.connectors;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;

/**
 * How long a connector may stay silent before it counts as stopped. Derived from its declared run
 * period, since collectors declare anything from PT1M to P7D and a fixed threshold reads the slow
 * ones as dead between two runs. Capped, or P7D would make one undeletable for a fortnight.
 */
@Slf4j
public final class HeartbeatWindow {

  public static final Duration DEFAULT = Duration.ofMinutes(2);
  public static final Duration MAX = Duration.ofMinutes(5);

  private static final int SILENT_PERIODS_BEFORE_STOPPED = 2;

  private HeartbeatWindow() {}

  public static String periodKey(ConnectorType type) {
    return type.name() + "_PERIOD";
  }

  public static Duration forInstance(ConnectorInstance instance, ConnectorType type) {
    if (instance == null || type == null) {
      return DEFAULT;
    }
    return instance
        .configurationValue(periodKey(type))
        .map(HeartbeatWindow::fromDeclaredPeriod)
        .orElse(DEFAULT);
  }

  /** Anything unusable falls back to the default rather than failing a deletion. */
  public static Duration fromDeclaredPeriod(String isoPeriod) {
    if (isoPeriod == null || isoPeriod.isBlank()) {
      return DEFAULT;
    }
    Duration period;
    try {
      period = Duration.parse(isoPeriod);
    } catch (DateTimeParseException e) {
      log.warn("Ignoring unparsable connector period '{}', falling back to {}", isoPeriod, DEFAULT);
      return DEFAULT;
    }
    if (period.isNegative() || period.isZero()) {
      return DEFAULT;
    }
    Duration window = period.multipliedBy(SILENT_PERIODS_BEFORE_STOPPED);
    if (window.compareTo(DEFAULT) < 0) {
      return DEFAULT;
    }
    return window.compareTo(MAX) > 0 ? MAX : window;
  }
}
