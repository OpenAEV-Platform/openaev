package io.openaev.database.model.autonomous;

/** Lifecycle of an operator steering directive injected into a live autonomous run. */
public enum AutonomousDirectiveStatus {
  /** Queued, not yet read by the orchestrator's next decision cycle. */
  PENDING,
  /** Read and applied by the orchestrator. */
  CONSUMED
}
