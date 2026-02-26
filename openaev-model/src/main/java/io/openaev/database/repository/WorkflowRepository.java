package io.openaev.database.repository;

import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
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

  Optional<Workflow> findByIdAndStatus(String workflowId, WorkflowStatus status);
}
