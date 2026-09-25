package io.openaev.utils.fixtures;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import java.util.List;

public class ConditionFixture {

  public static Condition getDefaultCondition(PrimitiveType key, String value) {
    Condition condition = new Condition();
    condition.setKeyTypes(List.of(key));
    condition.setValue(value);
    condition.setType(ConditionType.EQ);
    return condition;
  }

  /**
   * A {@code DEPEND_ON} condition. Its value is the id of the prerequisite <b>step template</b>,
   * not a comparison value: the chaining engine resolves it as "has a run step been created for
   * this step template in this run?".
   *
   * @param stepTemplateId the prerequisite step template id
   * @param workflowId the workflow the condition belongs to
   */
  public static Condition getDependOnCondition(String stepTemplateId, String workflowId) {
    Condition condition = new Condition();
    condition.setType(ConditionType.DEPEND_ON);
    condition.setValue(stepTemplateId);
    condition.setWorkflowId(workflowId);
    return condition;
  }
}
