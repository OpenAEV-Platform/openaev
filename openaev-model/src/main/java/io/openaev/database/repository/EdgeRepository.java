package io.openaev.database.repository;

import io.openaev.database.model.Edge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EdgeRepository extends JpaRepository<Edge, String> {
  List<Edge> findAllByEdgeTemplateId(String edgeTemplateId);

  List<Edge> findAllByEdgeTemplateIdNullAndWorkflowId(String workflowId);

  List<Edge> findAllByWorkflowId(String workflowId);

  List<Edge> findAllByEdgeTemplateIdEmptyAndWorkflowId(String workflowId);
}
