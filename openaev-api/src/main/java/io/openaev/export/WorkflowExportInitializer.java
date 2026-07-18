package io.openaev.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.utils.WorkflowScopeRuleUtils;
import jakarta.annotation.Resource;
import java.io.IOException;
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

  private static final String WORKFLOW_STEPS = "workflow_steps";
  private static final String WORKFLOW_SCOPE_RULES = "workflow_scope_rules";
  private static final String STEP_DATA = "step_data";
  private static final String INJECT_INJECTOR_CONTRACT = "inject_injector_contract";
  private static final String INJECTOR_CONTRACT_ID = "injector_contract_id";

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
        conditionRepository
            .findAllByWorkflowIdAndConditionParentIsNullAndTypeNot(
                workflow.getId(), ConditionType.MAPPER)
            .stream()
            .filter(c -> !linkedConditionIds.contains(c.getId()))
            .toList();
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
    filterAssetScopeRules(workflowObject, objectMapper);
    JsonNode stepsNode = workflowObject.get(WORKFLOW_STEPS);
    if (!(stepsNode instanceof ArrayNode stepsArray)) {
      return;
    }
    stepsArray.forEach(stepNode -> enrichStepData(stepNode, objectMapper));
  }

  private static void filterAssetScopeRules(ObjectNode workflowObject, ObjectMapper objectMapper) {
    JsonNode scopeRulesNode = workflowObject.get(WORKFLOW_SCOPE_RULES);
    if (!(scopeRulesNode instanceof ArrayNode scopeRulesArray)) {
      return;
    }

    ArrayNode filteredScopeRules = objectMapper.createArrayNode();
    scopeRulesArray.forEach(
        ruleNode -> {
          if (!WorkflowScopeRuleUtils.isAssetScopeRule(ruleNode)) {
            filteredScopeRules.add(ruleNode);
          }
        });
    workflowObject.set(WORKFLOW_SCOPE_RULES, filteredScopeRules);
  }

  private void enrichStepData(JsonNode stepNode, ObjectMapper objectMapper) {
    if (!(stepNode instanceof ObjectNode stepObject)) {
      return;
    }
    JsonNode stepDataNode = stepObject.get(STEP_DATA);
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
          extractInjectorContractId(stepDataObject.get(INJECT_INJECTOR_CONTRACT));
      if (StringUtils.hasText(injectorContractId)) {
        InjectorContract injectorContract =
            injectorContractRepository.findById(injectorContractId).orElse(null);
        if (injectorContract != null) {
          initializeInjectorContractForExport(injectorContract);
          ObjectNode enrichedContractNode = objectMapper.valueToTree(injectorContract);
          JsonNode existingContractNode = stepDataObject.get(INJECT_INJECTOR_CONTRACT);
          if (existingContractNode instanceof ObjectNode existingContractObject) {
            preserveAbsentFieldsRecursively(enrichedContractNode, existingContractObject);
          }
          stepDataObject.set(INJECT_INJECTOR_CONTRACT, enrichedContractNode);
        }
      }
      normalizeStepDataFieldsForExport(stepDataObject, objectMapper);
      setStepData(stepObject, stepDataObject, isTextual, objectMapper);
    } catch (Exception e) {
      log.warn("Unable to enrich workflow step_data for export", e);
    }
  }

  private static void preserveAbsentFieldsRecursively(ObjectNode target, ObjectNode source) {
    source
        .fields()
        .forEachRemaining(
            field -> {
              String key = field.getKey();
              JsonNode sourceValue = field.getValue();
              JsonNode targetValue = target.get(key);
              if (targetValue == null || targetValue.isNull()) {
                target.set(key, sourceValue);
                return;
              }
              if (targetValue.isObject() && sourceValue.isObject()) {
                preserveAbsentFieldsRecursively((ObjectNode) targetValue, (ObjectNode) sourceValue);
              }
            });
  }

  private static void normalizeStepDataFieldsForExport(
      ObjectNode stepDataObject, ObjectMapper objectMapper) {
    if (!stepDataObject.has("inject_id")) {
      stepDataObject.putNull("inject_id");
    }
    if (!stepDataObject.has("inject_status")) {
      stepDataObject.putNull("inject_status");
    }
    if (!stepDataObject.has("inject_depends_on")) {
      stepDataObject.set("inject_depends_on", objectMapper.createArrayNode());
    }
    stepDataObject.remove("inject_assets");
    stepDataObject.remove("inject_asset_groups");
    stepDataObject.remove("inject_exercise");
    stepDataObject.remove("inject_scenario");
    stepDataObject.putNull("inject_exercise");
    stepDataObject.putNull("inject_scenario");
  }

  private static void setStepData(
      ObjectNode stepObject,
      ObjectNode stepDataObject,
      boolean isTextual,
      ObjectMapper objectMapper)
      throws IOException {
    if (isTextual) {
      stepObject.put(STEP_DATA, objectMapper.writeValueAsString(stepDataObject));
      return;
    }
    stepObject.set(STEP_DATA, stepDataObject);
  }

  private static String extractInjectorContractId(JsonNode injectorContractNode) {
    if (injectorContractNode == null || injectorContractNode.isNull()) {
      return null;
    }
    if (injectorContractNode.isTextual()) {
      return injectorContractNode.asText();
    }
    JsonNode idNode = injectorContractNode.get(INJECTOR_CONTRACT_ID);
    return idNode != null && idNode.isTextual() ? idNode.asText() : null;
  }

  private static void initializeInjectorContractForExport(InjectorContract injectorContract) {
    injectorContract
        .getAttackPatterns()
        .forEach(attackPattern -> Hibernate.initialize(attackPattern.getKillChainPhases()));
  }
}
