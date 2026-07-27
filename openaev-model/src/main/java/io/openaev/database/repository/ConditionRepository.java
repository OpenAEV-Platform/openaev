package io.openaev.database.repository;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;
import java.util.Set;
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
   * Batched variant of {@link #findAllLinkedToStepId}: retrieves the conditions linked to any of
   * the given step ids, each paired with its step id so the caller can group by step in one read
   * (issue 5048). Like the single-step query, this returns the conditions linked to a step (the
   * tree roots); a composite AND/OR filter's leaf keys live in its {@code conditionChildren} and
   * are reached by walking the tree, not by this query.
   *
   * @param stepIds the step ids to retrieve conditions for
   * @return one row per (step, linked condition)
   */
  @Query(
      """
          SELECT new io.openaev.database.repository.StepConditionRow(cs.step.id, c)
          FROM Condition c
          JOIN c.conditionSteps cs
          WHERE cs.step.id IN :stepIds
          """)
  List<StepConditionRow> findAllLinkedToStepIdIn(@Param("stepIds") Set<String> stepIds);

  /**
   * Retrieves all root conditions (events) for a given workflow. A root condition has no parent.
   *
   * @param workflowId the workflow identifier
   * @return a list of root conditions for the given workflow
   */
  List<Condition> findAllByWorkflowIdAndConditionParentIsNull(String workflowId);

  /**
   * Retrieves all root conditions (events) for a given workflow, excluding those of type MAPPER.
   *
   * @param workflowId the workflow identifier
   * @param excludedType the condition type to exclude (MAPPER)
   * @return a list of non-MAPPER root conditions for the given workflow
   */
  List<Condition> findAllByWorkflowIdAndConditionParentIsNullAndTypeNot(
      String workflowId, ConditionType excludedType);

  /**
   * Retrieves all conditions (roots AND descendants) for a given workflow, excluding those of the
   * specified type. Used to build complete event trees without relying on lazy-loaded children.
   *
   * @param workflowId the workflow identifier
   * @param excludedType the condition type to exclude (e.g. MAPPER)
   * @return all non-excluded conditions for the given workflow
   */
  List<Condition> findAllByWorkflowIdAndTypeNot(String workflowId, ConditionType excludedType);

  List<Condition> findAllByKeyTypeIn(Set<PrimitiveType> outputKeyTypes);

  /**
   * Retrieves root filter conditions for a given workflow that have at least one child condition
   * with a matching key type. The root condition is linked to steps (via conditionSteps), while the
   * keyType is on child conditions (the actual filter leaves).
   *
   * @param workflowId the workflow identifier
   * @param keyTypes the key types to look for in child conditions
   * @param excludedTypes the condition types to exclude from child matching
   * @return a list of root conditions whose children match the criteria
   */
  @Query(
      """
          SELECT DISTINCT root
          FROM Condition root
          JOIN FETCH root.conditionSteps cs
          JOIN FETCH cs.step
          WHERE root.workflowId = :workflowId
            AND root.conditionParent IS NULL
            AND root.conditionSteps IS NOT EMPTY
            AND EXISTS (
              SELECT 1 FROM Condition child
              WHERE child.conditionParent = root
                AND child.keyType IN :keyTypes
                AND child.type NOT IN :excludedTypes
            )
          """)
  List<Condition> findFilterConditionsByWorkflowIdAndKeyTypes(
      @Param("workflowId") String workflowId,
      @Param("keyTypes") Set<PrimitiveType> keyTypes,
      @Param("excludedTypes") Set<ConditionType> excludedTypes);
}
