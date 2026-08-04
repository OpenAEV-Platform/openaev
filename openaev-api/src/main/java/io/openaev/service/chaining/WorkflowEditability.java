package io.openaev.service.chaining;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Workflow;
import io.openaev.rest.exception.WorkflowNotEditableException;

/**
 * Guards mutations of a simulation's chaining logic map (step templates, condition trees and
 * workflow configuration).
 *
 * <p>A launched simulation is an immutable record of what ran: its logic map is editable only while
 * the owning simulation is {@link ExerciseStatus#SCHEDULED} (UI "Draft" / "Scheduled").
 * Scenario-owned workflows have no simulation and stay fully editable — their logic map is already
 * isolated per run by the copy performed at launch. See ADR-005.
 *
 * <p>Kept as a dependency-free static helper so it can be reused by {@code StepService}, {@code
 * ConditionService} and {@code WorkflowService} without introducing a circular bean dependency
 * ({@code WorkflowService} already depends on {@code StepService}).
 */
public final class WorkflowEditability {

  /**
   * Stable machine-readable reason surfaced to the frontend, consistent with other coded 403s (e.g.
   * {@code TENANT_ACCESS_DENIED}). The frontend maps it to a localized message.
   */
  public static final String WORKFLOW_NOT_EDITABLE = "WORKFLOW_NOT_EDITABLE";

  private WorkflowEditability() {}

  /**
   * @param workflowTemplate the TEMPLATE workflow whose logic map is about to be mutated
   * @throws WorkflowNotEditableException (HTTP 403) when the owning simulation is not SCHEDULED
   */
  public static void assertLogicMapEditable(Workflow workflowTemplate) {
    if (workflowTemplate == null) {
      return;
    }
    Exercise simulation = workflowTemplate.getSimulation();
    // Scenario-owned workflows (no simulation) are always editable: already isolated by
    // copy-on-launch.
    if (simulation == null) {
      return;
    }
    if (!ExerciseStatus.SCHEDULED.equals(simulation.getStatus())) {
      throw new WorkflowNotEditableException(WORKFLOW_NOT_EDITABLE);
    }
  }
}
