package io.openaev.database.model;

import java.util.Locale;

/**
 * Lifecycle of an OpenCTI IOC validation request inside OpenAEV.
 *
 * <p>The OpenCTI lifecycle mutation uses the snake_case form of these values ({@link #toOpenCti()}),
 * which matches the {@code IocValidationRequestStatus} enum of the cross-repository contract.
 */
public enum IocValidationStatus {
  AWAITING_APPROVAL,
  RUNNING,
  COMPLETED,
  PARTIAL,
  FAILED,
  REJECTED;

  /** Value of the OpenCTI {@code IocValidationRequestStatus} enum for this status. */
  public String toOpenCti() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Whether no further transition can happen from this status. */
  public boolean isTerminal() {
    return this == COMPLETED || this == PARTIAL || this == FAILED || this == REJECTED;
  }
}
