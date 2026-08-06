package io.openaev.database.model.autonomous;

/**
 * Kinds of timeline entry an autonomous run emits. Rendered by the OpenAEV "AI decision timeline"
 * next to the animated attack-path graph.
 */
public enum AutonomousEventType {
  /** Free-form reasoning / narration streamed from the orchestrator (thinking_text). */
  NARRATION,
  /** A concrete decision the AI took (choose technique, target, pivot). */
  DECISION,
  /** A tool invocation against OpenAEV (recon, inject, scope read...). */
  TOOL_ACTION,
  /** A handover between orchestrator sub-agents (recon -> exploit -> lateral...). */
  HANDOVER,
  /**
   * The orchestrator consulting a specialist agent (payload creation, code generation, recon,
   * exploitation support). Carries structured data {@code {phase: "start"|"result", agent_name,
   * status}}; a {@code start} with no matching {@code result} renders as "waiting for the agent".
   */
  AGENT_DELEGATION,
  /** A capability gap: no installed injector/contract can perform a needed technique. */
  GAP,
  /** A run-status transition (running, paused, waiting-input, completed...). */
  STATUS,
  /** An operator steering directive that was consumed by the run. */
  DIRECTIVE,
  /** A question raised to the operator (HITL when stuck). */
  QUESTION,
  /** A proof-of-exploitation case-file fragment for the final report. */
  PROOF
}
