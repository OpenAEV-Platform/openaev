package io.openaev.database.model;

/**
 * Computed (never stored) change status of a launched simulation's scope rule, recomputed on every
 * read from the two frozen snapshots (launch + end) and the current live state. Distinguishes a
 * change that happened <b>during</b> the run (may have altered results) from one that happened
 * <b>after</b> it ended (cosmetic). See ADR-006.
 */
public enum ScopeRuleSnapshotStatus {
  /** Unchanged across launch, end and current. */
  RESOLVED,
  /**
   * Differs between launch and end: changed while the run was active (may have altered results).
   */
  MODIFIED_DURING_EXECUTION,
  /** Referenced target disappeared while the run was active. */
  DELETED_DURING_EXECUTION,
  /** Identical launch↔end but differs from current: changed after the run ended (cosmetic). */
  MODIFIED_AFTER_EXECUTION,
  /** Referenced target disappeared after the run ended (cosmetic). */
  DELETED_AFTER_EXECUTION
}
