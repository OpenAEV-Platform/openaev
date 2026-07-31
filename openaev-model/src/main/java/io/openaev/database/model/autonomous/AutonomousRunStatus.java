package io.openaev.database.model.autonomous;

/** Lifecycle of an autonomous (AI-driven) attack-path run. */
public enum AutonomousRunStatus {
  /** Created but not yet handed to the orchestrator. */
  CREATED,
  /** The orchestrator is actively planning and executing. */
  RUNNING,
  /** Paused by the operator; the underlying chained simulation is paused too. */
  PAUSED,
  /** The orchestrator is blocked and asked the operator a question (HITL). */
  WAITING_INPUT,
  /** The objective was reached (or the orchestrator decided to stop) successfully. */
  COMPLETED,
  /** The run ended with an error. */
  FAILED,
  /** Stopped by the operator. */
  CANCELED
}
