package io.openaev.export;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/** Initializes all lazy collections on a Workflow entity tree for Jackson serialization. */
@Component
public class WorkflowExportInitializer {

  @Resource private ConditionRepository conditionRepository;

  /**
   * Eagerly loads the full workflow graph for export.
   *
   * @param workflow the workflow to initialize
   * @param isWithScopeDefinition if true, also initializes scope rules and scope variables
   */
  public void initialize(Workflow workflow, boolean isWithScopeDefinition) {
    // Initialize steps + their conditions
    Hibernate.initialize(workflow.getSteps());
    workflow
        .getSteps()
        .forEach(
            step -> {
              Hibernate.initialize(step.getConditionSteps());
              step.getConditionSteps().forEach(cs -> initializeConditionTree(cs.getCondition()));
            });

    // Detect standalone events: root conditions not linked to any step
    Set<String> linkedConditionIds =
        workflow.getSteps().stream()
            .flatMap(step -> step.getConditionSteps().stream())
            .map(cs -> cs.getCondition().getId())
            .collect(Collectors.toSet());

    List<Condition> standaloneRoots =
        conditionRepository.findAllByWorkflowIdAndConditionParentIsNull(workflow.getId()).stream()
            .filter(c -> !linkedConditionIds.contains(c.getId()))
            .collect(Collectors.toList());
    standaloneRoots.forEach(WorkflowExportInitializer::initializeConditionTree);

    // Flatten the tree (roots + all descendants) so the importer can reconstruct it via parent IDs
    List<Condition> standaloneFlat = new ArrayList<>();
    standaloneRoots.forEach(root -> collectConditionsFlat(root, standaloneFlat));
    workflow.setStandaloneConditions(standaloneFlat);

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

  private static void collectConditionsFlat(Condition condition, List<Condition> collector) {
    collector.add(condition);
    condition.getConditionChildren().forEach(child -> collectConditionsFlat(child, collector));
  }
}
