package io.openaev.database.repository;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

  /**
   * Resolves the tenant that owns a workflow: workflow -&gt; simulation -&gt; tenant. Used to stamp
   * the tenant on chaining ready events (#6357). NATIVE on purpose: {@code exercises} is a v1
   * {@code @Filter} entity ({@code tenantFilter}), so a JPQL path through it would be re-filtered
   * by the ambient {@code TenantContext} and, on a scheduler producer thread ({@code
   * QueueChainingJob}) that carries the default (or wrong) tenant, could silently return empty and
   * stamp the event with null. A native query bypasses the Hibernate filter, so the stamp is truly
   * context-free. Also no lazy navigation, so it is safe with no open session. Empty for a workflow
   * with no simulation (standalone run); the caller falls back to the default tenant.
   */
  @Query(
      value =
          """
      SELECT e.tenant_id
      FROM workflows w
      JOIN exercises e ON e.exercise_id = w.workflow_simulation_id
      WHERE w.workflow_id = :workflowId
      """,
      nativeQuery = true)
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

  /**
   * Retrieves the most recent workflow of a given status for a simulation. A simulation reuses the
   * same id across launch / reset / relaunch cycles and old RUN rows are not deleted, so it may own
   * several RUN rows; the latest (by creation date) is the current execution view. See ADR-006.
   *
   * @param simulationId the ID of the simulation
   * @param status the status of the workflow
   * @return the latest matching workflow, or empty if none
   */
  Optional<Workflow> findFirstBySimulation_IdAndStatusInOrderByWorkflowCreatedAtDesc(
      @NotBlank String simulationId, List<WorkflowStatus> status);

  Optional<Workflow> findByIdAndStatus(String workflowId, WorkflowStatus status);

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
        SELECT * FROM workflows
        WHERE workflow_status = 'RUN'
          AND workflow_timeout_enabled = true
          AND workflow_timeout_seconds IS NOT NULL
          AND workflow_created_at + (workflow_timeout_seconds || ' seconds')::interval <= now()
        """,
      nativeQuery = true)
  List<Workflow> findAllExpiredRunWorkflows();
}
