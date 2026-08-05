package io.openaev.database.repository;

import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.WorkflowScopeRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Deletes every scope rule whose value points at the given entity id for the given value type.
   *
   * <p>Scope rules reference assets, asset groups and teams by their raw id in {@code
   * workflow_scope_rule_value} with no foreign key (the column is polymorphic and can also hold
   * IPs, subnets or domains). This bulk delete is the cascade cleanup invoked when such an entity
   * is removed, so allow-list and deny-list rules pointing at the now-deleted entity do not linger
   * as unresolvable "ghost" scope entries. The value type guard prevents removing an unrelated IP
   * or domain rule that happens to share the same string value.
   *
   * @param ruleValue the deleted entity id
   * @param valueType the value type identifying which kind of entity the id refers to
   * @return the number of scope rules removed
   */
  @Modifying
  @Query(
      "delete from WorkflowScopeRule rule where rule.ruleValue = :ruleValue and rule.valueType = :valueType")
  int deleteByRuleValueAndValueType(
      @Param("ruleValue") String ruleValue, @Param("valueType") ScopeRuleValueType valueType);
}
