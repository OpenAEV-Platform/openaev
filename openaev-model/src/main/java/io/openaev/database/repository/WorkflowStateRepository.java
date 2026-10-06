package io.openaev.database.repository;

import io.openaev.database.model.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface WorkflowStateRepository extends JpaRepository<WorkflowState, String> {

  WorkflowState findByStepTemplate_IdAndWorkflowExecution_Id(
      String stepTemplateId, String workflowExecutionId);

  WorkflowState findByStepTemplateIsNullAndWorkflowExecutionId(String id);

  /**
   * Creates the global state of a run unless it already exists (unique partial index {@code
   * uq_workflow_state_global}), so that concurrent first writes cannot create two global states.
   */
  // Native: atomic insert-or-ignore (ON CONFLICT), not expressible in JPA; WorkflowState is not
  // indexed, audited nor streamed (no entity listener).
  @Transactional
  @Modifying
  @Query(
      value =
          "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id,"
              + " workflow_state_created_at, workflow_state_updated_at)"
              + " VALUES (:id, :workflowExecutionId, now(), now())"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertGlobalStateIfAbsent(
      @Param("id") String id, @Param("workflowExecutionId") String workflowExecutionId);

  /**
   * Creates the local state of a step template in a run unless it already exists (unique constraint
   * {@code uq_workflow_state_workflow_step}).
   */
  // Native: atomic insert-or-ignore (ON CONFLICT), not expressible in JPA; WorkflowState is not
  // indexed, audited nor streamed (no entity listener).
  @Transactional
  @Modifying
  @Query(
      value =
          "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id,"
              + " workflow_step_template_id, workflow_state_created_at, workflow_state_updated_at)"
              + " VALUES (:id, :workflowExecutionId, :stepTemplateId, now(), now())"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertLocalStateIfAbsent(
      @Param("id") String id,
      @Param("workflowExecutionId") String workflowExecutionId,
      @Param("stepTemplateId") String stepTemplateId);

  /**
   * Deletes every state (global and local) of the runs of a simulation in a single statement; the
   * database cascade removes their {@code workflow_state_entries}. A bulk delete is safe here:
   * WorkflowState is not indexed, audited nor streamed (no entity listener).
   */
  @Transactional
  @Modifying
  @Query(
      "DELETE FROM WorkflowState s WHERE s.workflowExecution.id IN"
          + " (SELECT w.id FROM Workflow w WHERE w.simulation.id = :simulationId)")
  int deleteAllBySimulationId(@Param("simulationId") String simulationId);
}
