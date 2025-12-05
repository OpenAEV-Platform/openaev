package io.openaev.database.repository;

import io.openaev.database.model.Step;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StepRepository extends JpaRepository<Step, Long> {
  // STEP TEMPLATE
  List<Step> findAllByStepTemplateIdEmptyAndWorkflowId(String workflowId);

  // STEP EXECUTED
  List<Step> findAllByStepTemplateIdAndWorkflowId(String stepTemplateId, String workflowId);
}
