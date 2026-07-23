package io.openaev.database.repository;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

  /**
   * Resolves the tenant that owns a workflow, as a projection: workflow -&gt; simulation -&gt;
   * tenant. Used to stamp the tenant on chaining ready events (#6357). A single query with no lazy
   * association access, so it is safe on any thread with no open session (ready events are also
   * produced from scheduler jobs where open-in-view is inactive). Empty for a workflow with no
   * simulation (standalone run); the caller falls back to the default tenant.
   */
  @Query("SELECT w.simulation.tenant.id FROM Workflow w WHERE w.id = :workflowId")
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
