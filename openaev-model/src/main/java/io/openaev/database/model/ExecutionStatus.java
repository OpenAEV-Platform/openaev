package io.openaev.database.model;

import java.util.Locale;

public enum ExecutionStatus {
  // Inject Status
  EXECUTED,
  PARTIAL,
  ERROR,

  // -- Deprecated (kept for backward compatibility with existing DB data) --
  @Deprecated
  MAYBE_PREVENTED,
  @Deprecated
  MAYBE_PARTIAL_PREVENTED,

  // Inject Execution Progress
  DRAFT,
  QUEUING,
  EXECUTING,
  PENDING,
  ;

  /**
   * Alias-aware parser: maps legacy and trace-level success values to their canonical {@link
   * ExecutionStatus} equivalent, so implant callbacks and injector payloads that send the old or
   * trace-level status name are still accepted without error.
   *
   * <p>Mappings:
   *
   * <ul>
   *   <li>{@code SUCCESS} → {@code EXECUTED} (pre-rename inject-level success)
   *   <li>{@code SUCCESS_WITH_CLEANUP_FAIL}, {@code SUCCESS_WITH_CLEANUP_FAILURE}, {@code
   *       EXECUTED_WITH_CLEANUP_FAILURE}, {@code EXECUTED_WITH_CLEANUP_FAIL} → {@code EXECUTED}
   *       (cleanup-failure is still a successful inject at the inject level; the detail lives in
   *       {@link ExecutionTraceStatus})
   * </ul>
   */
  public static ExecutionStatus fromName(String status) {
    String normalized = normalize(status);
    return switch (normalized) {
      case "SUCCESS",
              "SUCCESS_WITH_CLEANUP_FAIL",
              "SUCCESS_WITH_CLEANUP_FAILURE",
              "EXECUTED_WITH_CLEANUP_FAILURE",
              "EXECUTED_WITH_CLEANUP_FAIL" ->
          EXECUTED;
      default -> ExecutionStatus.valueOf(normalized);
    };
  }

  private static String normalize(String status) {
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("Execution status must not be null or blank");
    }
    return status.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
  }
}
