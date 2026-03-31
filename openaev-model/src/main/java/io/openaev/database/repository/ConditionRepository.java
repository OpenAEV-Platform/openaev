package io.openaev.database.repository;

import io.openaev.database.model.Condition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, String> {
  /**
   * Retrieves all {@link Condition} entities associated with the specified step ID through the link
   * table.
   *
   * @param stepId the ID of the step to filter conditions by
   * @return a list of conditions linked to the given step ID
   */
  @Query(
      """
      SELECT c
      FROM Condition c
      JOIN c.conditionSteps cs
      WHERE cs.step.id = :stepId
      """)
  List<Condition> findAllLinkedToStepId(@Param("stepId") String stepId);

  /**
   * Retrieves all root conditions (events) for a given workflow. A root condition has no parent.
   *
   * @param workflowId the workflow identifier
   * @return a list of root conditions for the given workflow
   */
  List<Condition> findAllByWorkflowIdAndConditionParentIsNull(String workflowId);
}
