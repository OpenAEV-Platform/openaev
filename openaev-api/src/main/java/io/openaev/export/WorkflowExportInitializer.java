package io.openaev.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Condition;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectorContractRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Initializes all lazy collections on a Workflow entity tree for Jackson serialization. */
@Component
@Slf4j
public class WorkflowExportInitializer {

  @Resource private ConditionRepository conditionRepository;
  @Resource private InjectorContractRepository injectorContractRepository;

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

  public void enrichWorkflowStepDataForExport(
      ObjectNode exportNode, String workflowKey, ObjectMapper objectMapper) {
    JsonNode workflowNode = exportNode.get(workflowKey);
    if (!(workflowNode instanceof ObjectNode workflowObject)) {
      return;
    }
    JsonNode stepsNode = workflowObject.get("workflow_steps");
    if (!(stepsNode instanceof ArrayNode stepsArray)) {
      return;
    }
    stepsArray.forEach(stepNode -> enrichStepData(stepNode, objectMapper));
  }

  private void enrichStepData(JsonNode stepNode, ObjectMapper objectMapper) {
    if (!(stepNode instanceof ObjectNode stepObject)) {
      return;
    }
    JsonNode stepDataNode = stepObject.get("step_data");
    if (stepDataNode == null || stepDataNode.isNull()) {
      return;
    }

    boolean isTextual = stepDataNode.isTextual();
    try {
      JsonNode parsedStepData =
          isTextual ? objectMapper.readTree(stepDataNode.asText()) : stepDataNode;
      if (!(parsedStepData instanceof ObjectNode stepDataObject)) {
        return;
      }

      String injectorContractId =
          extractInjectorContractId(stepDataObject.get("inject_injector_contract"));
      if (!StringUtils.hasText(injectorContractId)) {
        return;
      }

      InjectorContract injectorContract =
          injectorContractRepository.findById(injectorContractId).orElse(null);
      if (injectorContract == null) {
        return;
      }

      initializeInjectorContractForExport(injectorContract);
      stepDataObject.set("inject_injector_contract", objectMapper.valueToTree(injectorContract));
      if (isTextual) {
        stepObject.put("step_data", objectMapper.writeValueAsString(stepDataObject));
      } else {
        stepObject.set("step_data", stepDataObject);
      }
    } catch (Exception e) {
      log.warn("Unable to enrich workflow step_data for export", e);
    }
  }

  private static String extractInjectorContractId(JsonNode injectorContractNode) {
    if (injectorContractNode == null || injectorContractNode.isNull()) {
      return null;
    }
    if (injectorContractNode.isTextual()) {
      return injectorContractNode.asText();
    }
    JsonNode idNode = injectorContractNode.get("injector_contract_id");
    return idNode != null && idNode.isTextual() ? idNode.asText() : null;
  }

  private static void initializeInjectorContractForExport(InjectorContract injectorContract) {
    injectorContract
        .getAttackPatterns()
        .forEach(attackPattern -> Hibernate.initialize(attackPattern.getKillChainPhases()));
  }
}
