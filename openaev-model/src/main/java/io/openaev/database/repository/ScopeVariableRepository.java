package io.openaev.database.repository;

import io.openaev.database.model.ScopeVariable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScopeVariableRepository extends JpaRepository<ScopeVariable, String> {

  /**
   * Retrieves all {@link ScopeVariable} entities associated with the specified workflow ID.
   *
   * @param workflowId the ID of the workflow to filter by
   * @return a list of scope variables linked to the given workflow ID
   */
  List<ScopeVariable> findAllByWorkflowId(String workflowId);
}
