package io.openaev.database.model;

/**
 * Lifecycle of a finding.
 *
 * <ul>
 *   <li>{@link #OPEN}: detected, and not disproved by a later re-test.
 *   <li>{@link #NOT_REPRODUCED}: a re-test of the injects that produced it ran on the same targets
 *       and did not detect it again.
 *   <li>{@link #RESOLVED}: closed by a person or an integration once the fix is confirmed.
 * </ul>
 *
 * A new detection of the finding always sets it back to {@link #OPEN}.
 */
public enum FindingStatus {
  OPEN,
  NOT_REPRODUCED,
  RESOLVED,
}
