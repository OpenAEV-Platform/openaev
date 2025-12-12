package io.openaev.database.repository;

import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
  List<Workflow> findAllBySimulationId_Id(String simulationId);

  Workflow findBySimulationId_IdAndStatus(String simulationId, WORKFLOW_STATUS status);
}
