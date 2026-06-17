package io.openaev.export;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Workflow;
import org.hibernate.Hibernate;

/** Initializes all lazy collections on a Workflow entity tree for Jackson serialization. */
public final class WorkflowExportInitializer {

  private WorkflowExportInitializer() {}

  /**
   * Eagerly loads the full workflow graph for export.
   *
   * @param workflow the workflow to initialize
   * @param isWithScopeDefinition if true, also initializes scope rules and scope variables
   */
  public static void initialize(Workflow workflow, boolean isWithScopeDefinition) {
    // Always initialize steps + conditions
    Hibernate.initialize(workflow.getSteps());
    workflow
        .getSteps()
        .forEach(
            step -> {
              Hibernate.initialize(step.getConditionSteps());
              step.getConditionSteps().forEach(cs -> initializeConditionTree(cs.getCondition()));
            });

    // Only initialize scope collections when requested (mixin will filter the rest)
    if (isWithScopeDefinition) {
      Hibernate.initialize(workflow.getWorkflowScopeRules());
      Hibernate.initialize(workflow.getWorkflowScopeVariables());
    }
  }

  private static void initializeConditionTree(Condition condition) {
    Hibernate.initialize(condition);
    Hibernate.initialize(condition.getConditionParent());
    Hibernate.initialize(condition.getStepFrom());
    Hibernate.initialize(condition.getConditionChildren());
    condition.getConditionChildren().forEach(WorkflowExportInitializer::initializeConditionTree);
  }
}
