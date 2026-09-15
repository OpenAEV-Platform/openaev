package io.openaev.database.repository;

import io.openaev.database.model.WorkflowStateCorrelationProgress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowStateCorrelationProgressRepository
    extends JpaRepository<WorkflowStateCorrelationProgress, byte[]> {

  List<WorkflowStateCorrelationProgress> findByWorkflowState_IdAndIsCompleteTrue(
      String workflowStateId);
}
