package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Workflow;
import io.openaev.rest.exception.WorkflowNotEditableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the logic-map freeze guard (ADR-005). A simulation's logic map is editable only
 * while the owning simulation is SCHEDULED; scenario-owned workflows (no simulation) are always
 * editable.
 */
@DisplayName("WorkflowEditability.assertLogicMapEditable (ADR-005)")
class WorkflowEditabilityTest {

  private Workflow workflowWithSimulation(ExerciseStatus status) {
    Exercise simulation = new Exercise();
    simulation.setStatus(status);
    return Workflow.builder().simulation(simulation).build();
  }

  @Nested
  @DisplayName("editable states")
  class Editable {

    @Test
    @DisplayName("given a SCHEDULED simulation should not throw")
    void given_scheduledSimulation_should_allow() {
      // Arrange
      Workflow workflow = workflowWithSimulation(ExerciseStatus.SCHEDULED);

      // Act & Assert
      assertDoesNotThrow(() -> WorkflowEditability.assertLogicMapEditable(workflow));
    }

    @Test
    @DisplayName("given a scenario-owned workflow (no simulation) should not throw")
    void given_scenarioOwnedWorkflow_should_allow() {
      // Arrange
      Workflow workflow = Workflow.builder().build();

      // Act & Assert
      assertDoesNotThrow(() -> WorkflowEditability.assertLogicMapEditable(workflow));
    }

    @Test
    @DisplayName("given a null workflow should not throw")
    void given_nullWorkflow_should_allow() {
      // Act & Assert
      assertDoesNotThrow(() -> WorkflowEditability.assertLogicMapEditable(null));
    }
  }

  @Nested
  @DisplayName("frozen states")
  class Frozen {

    @ParameterizedTest(name = "given a {0} simulation should reject")
    @EnumSource(
        value = ExerciseStatus.class,
        names = {"RUNNING", "PAUSED", "CANCELED", "FINISHED"})
    void given_nonScheduledSimulation_should_reject(ExerciseStatus status) {
      // Arrange
      Workflow workflow = workflowWithSimulation(status);

      // Act & Assert
      assertThrows(
          WorkflowNotEditableException.class,
          () -> WorkflowEditability.assertLogicMapEditable(workflow));
    }
  }
}
