package io.openaev.database.repository;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

  /**
   * Reads the tenant a workflow belongs to from its own {@code tenant_id}, which is set from the
   * owning simulation or scenario when the row is written. Used to stamp the tenant on chaining
   * ready events (#6357). A scalar projection of the foreign key: no entity or lazy association is
   * loaded, so it needs no open session, and {@code Workflow} carries no Hibernate filter, so the
   * ambient {@code TenantContext} of a producer thread does not change the result. Empty when no
   * such workflow exists or, once {@code workflows} is tenant-active, when the transaction's scope
   * does not include its tenant.
   */
  @Query("SELECT w.tenant.id FROM Workflow w WHERE w.id = :workflowId")
  Optional<String> findTenantIdByWorkflowId(@Param("workflowId") String workflowId);

  /**
   * Retrieves all {@link Workflow} entities associated with the specified simulation ID.
   *
   * @param simulationId the ID of the simulation to filter workflows by
   * @return a list of workflows linked to the given simulation ID
   */
  List<Workflow> findAllBySimulation_Id(String simulationId);

  /**
   * Retrieves a {@link Workflow} entity by simulation ID and workflow status.
   *
   * @param simulationId the ID of the simulation
   * @param status the status of the workflow
   * @return the workflow matching the given simulation ID and status, or null if not found
   */
  Workflow findBySimulation_IdAndStatus(String simulationId, WorkflowStatus status);

  List<Workflow> findAllBySimulation_IdAndStatus(String simulationId, WorkflowStatus status);

  List<Workflow> findAllBySimulation_IdAndStatusIn(
      String simulationId, List<WorkflowStatus> status);

  /**
   * Retrieves the most recent workflow of a given status for a simulation. A simulation reuses the
   * same id across launch / reset / relaunch cycles. Reset now deletes its executions (ADR-014),
   * but a database reset before that may still hold several RUN rows; the latest (by creation date)
   * is the current execution view. See ADR-006.
   *
   * @param simulationId the ID of the simulation
   * @param status the status of the workflow
   * @return the latest matching workflow, or empty if none
   */
  Optional<Workflow> findFirstBySimulation_IdAndStatusInOrderByWorkflowCreatedAtDesc(
      @NotBlank String simulationId, List<WorkflowStatus> status);

  Optional<Workflow> findByIdAndStatus(String workflowId, WorkflowStatus status);

  /**
   * {@link #findByIdAndStatus} under a row lock (SELECT ... FOR UPDATE), for a status transition
   * that must happen once: a concurrent caller waits, then the status no longer matches and it gets
   * nothing (e.g. two simultaneous resumes, only the first takes the run from STOP to RUN).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Workflow w WHERE w.id = :id AND w.status = :status")
  Optional<Workflow> findForUpdateByIdAndStatus(
      @Param("id") String workflowId, @Param("status") WorkflowStatus status);

  boolean existsByIdAndStatus(String workflowId, WorkflowStatus status);

  List<Workflow> findByScenario_IdAndStatus(String scenarioId, WorkflowStatus workflowStatus);

  /**
   * Finds all RUN workflows that have timeout enabled and whose timeout has expired.
   *
   * @return list of expired workflows
   */
  @Query(
      value =
          """
        SELECT workflow_id FROM workflows
        WHERE workflow_status = 'RUN'
          AND workflow_timeout_enabled = true
          AND workflow_timeout_seconds IS NOT NULL
          AND workflow_created_at
              + ((workflow_timeout_seconds + COALESCE(workflow_pause_second, 0)) || ' seconds')::interval <= now()
        """,
      nativeQuery = true)
  List<String> findAllExpiredRunWorkflowIds();

  @Query("SELECT w FROM Workflow w LEFT JOIN FETCH w.workflowScopeRules WHERE w.id IN :ids")
  List<Workflow> findAllByIdWithScopeRules(@Param("ids") List<String> ids);

  @Query("SELECT count(1) > 0 FROM Workflow w WHERE w.simulation.id = :simulationId")
  boolean existsBySimulationId(@Param("simulationId") String simulationId);
}
