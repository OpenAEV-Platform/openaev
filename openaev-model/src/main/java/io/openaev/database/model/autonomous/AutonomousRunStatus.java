package io.openaev.database.model.autonomous;

/** Lifecycle of an autonomous (AI-driven) attack-path run. */
public enum AutonomousRunStatus {
  /** Created but not yet handed to the orchestrator. */
  CREATED,
  /**
   * Dry-run only: the orchestrator is designing the attack path (scoping, authoring steps,
   * recording decisions) but executes nothing. A plan run stays in this state until the plan is
   * complete.
   */
  PLANNING,
  /**
   * Dry-run only: the plan is complete and awaiting the operator's decision to run it for real.
   * This is a settled, non-executing state; promoting the run resets it to a fresh executing run.
   */
  PLANNED,
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
