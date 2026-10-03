package io.openaev.database.model;

import java.util.Locale;

/**
 * Result of one (indicator, security platform) pair. Precedence when several expectations report:
 * {@link #PREVENTED} over {@link #DETECTED} over {@link #MISSED}; {@link #ERROR} when the test could
 * not run. The STIX form ({@link #toStix()}) is the deployed-on {@code validation_status} value.
 */
public enum IocValidationOutcome {
  PREVENTED,
  DETECTED,
  MISSED,
  ERROR;

  public String toStix() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Outcomes backed by an evaluated expectation, which OpenCTI records as a sighting. */
  public boolean isEvaluated() {
    return this != ERROR;
  }
}
