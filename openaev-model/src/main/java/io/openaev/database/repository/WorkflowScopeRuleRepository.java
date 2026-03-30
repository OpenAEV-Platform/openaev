package io.openaev.database.repository;

import io.openaev.database.model.WorkflowScopeRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowScopeRuleRepository extends JpaRepository<WorkflowScopeRule, String> {
  /**
   * Retrieves all {@link WorkflowScopeRule} entities associated with the specified workflow ID.
   *
   * @param workflowId the ID of the workflow to filter by
   * @return a list of workflows scope rules linked to the given workflow ID.
   */
  List<WorkflowScopeRule> findAllByWorkflowId(String workflowId);
}
