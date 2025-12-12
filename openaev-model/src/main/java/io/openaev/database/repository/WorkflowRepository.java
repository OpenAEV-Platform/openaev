package io.openaev.database.repository;

import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
  List<Workflow> findAllBySimulationId_Id(String simulationId);
  Workflow findBySimulationId_IdAndStatus(String simulationId, WORKFLOW_STATUS status);
}
