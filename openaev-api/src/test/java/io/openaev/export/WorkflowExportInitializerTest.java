package io.openaev.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectorContractRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WorkflowExportInitializerTest {

  @Test
  void given_workflowExportData_should_preservePropertiesAndEnrichContractMetadata()
      throws Exception {
    // -- Arrange --
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    WorkflowExportInitializer workflowExportInitializer = new WorkflowExportInitializer();
    InjectorContractRepository injectorContractRepository = mock(InjectorContractRepository.class);
    ReflectionTestUtils.setField(
        workflowExportInitializer, "injectorContractRepository", injectorContractRepository);

    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId("contract-id");
    injectorContract.setDomains(Set.of(domain("domain-id-1"), domain("domain-id-2")));
    injectorContract.setTags(Set.of(tag("tag-id-1"), tag("tag-id-2")));
    when(injectorContractRepository.findById("contract-id"))
        .thenReturn(Optional.of(injectorContract));

    ObjectNode exportNode = objectMapper.createObjectNode();
    ObjectNode workflowNode = objectMapper.createObjectNode();
    workflowNode.put("workflow_rate_limit_enabled", true);
    workflowNode.put("workflow_max_attempts", 7);
    workflowNode.put("workflow_max_temporal_rate_seconds", 42L);
    workflowNode.put("workflow_timeout_enabled", true);
    workflowNode.put("workflow_timeout_seconds", 300L);
    workflowNode.put("workflow_safe_mode_enabled", true);

    ArrayNode standaloneConditions = objectMapper.createArrayNode();
    ObjectNode standaloneCondition = objectMapper.createObjectNode();
    standaloneCondition.put("condition_id", "event-condition-id");
    standaloneCondition.put("condition_type", "EQ");
    standaloneCondition.put("condition_value", "SUCCESS");
    standaloneConditions.add(standaloneCondition);
    workflowNode.set("workflow_standalone_conditions", standaloneConditions);

    ArrayNode scopeRules = objectMapper.createArrayNode();
    scopeRules.add(
        scopeRule(
            objectMapper,
            ScopeRuleSelectedMode.ALLOWLIST.name(),
            ScopeRuleSource.MANUAL.name(),
            "10.0.0.1",
            ScopeRuleValueType.IP.name()));
    scopeRules.add(
        scopeRule(
            objectMapper,
            ScopeRuleSelectedMode.DENYLIST.name(),
            ScopeRuleSource.CSV.name(),
            "corp.local",
            ScopeRuleValueType.DOMAIN.name()));
    scopeRules.add(
        scopeRule(
            objectMapper,
            ScopeRuleSelectedMode.ALLOWLIST.name(),
            ScopeRuleSource.MANUAL.name(),
            "10.0.0.0/24",
            ScopeRuleValueType.IP_SUBNET.name()));
    scopeRules.add(
        scopeRule(
            objectMapper,
            ScopeRuleSelectedMode.ALLOWLIST.name(),
            ScopeRuleSource.ASSET.name(),
            "asset-id-1",
            ScopeRuleValueType.ASSET_ID.name()));
    scopeRules.add(
        scopeRule(
            objectMapper,
            ScopeRuleSelectedMode.DENYLIST.name(),
            ScopeRuleSource.ASSET_GROUP.name(),
            "asset-group-id-1",
            ScopeRuleValueType.ASSET_GROUP_ID.name()));
    workflowNode.set("workflow_scope_rules", scopeRules);

    ArrayNode steps = objectMapper.createArrayNode();
    ObjectNode textualStep = objectMapper.createObjectNode();
    textualStep.put("step_action_class", "INJECT");
    textualStep.put(
        "step_data",
        """
        {
          "inject_exercise": "old-exercise-id",
          "inject_scenario": "old-scenario-id",
          "inject_injector_contract": {
            "injector_contract_id": "contract-id",
            "listened": true,
            "injector_contract_payload": {
              "listened": true
            }
          }
        }
        """);
    steps.add(textualStep);

    ObjectNode objectStep = objectMapper.createObjectNode();
    objectStep.put("step_action_class", "INJECT");
    ObjectNode objectStepData = objectMapper.createObjectNode();
    objectStepData.put("inject_exercise", "old-exercise-id");
    objectStepData.put("inject_scenario", "old-scenario-id");
    ObjectNode objectStepContract = objectMapper.createObjectNode();
    objectStepContract.put("injector_contract_id", "contract-id");
    objectStepContract.put("listened", true);
    ObjectNode objectStepPayload = objectMapper.createObjectNode();
    objectStepPayload.put("listened", true);
    objectStepContract.set("injector_contract_payload", objectStepPayload);
    objectStepData.set("inject_injector_contract", objectStepContract);
    objectStep.set("step_data", objectStepData);
    steps.add(objectStep);

    ObjectNode stepWithoutContract = objectMapper.createObjectNode();
    stepWithoutContract.put("step_action_class", "INJECT");
    ObjectNode stepWithoutContractData = objectMapper.createObjectNode();
    stepWithoutContractData.put("inject_exercise", "old-exercise-id");
    stepWithoutContractData.put("inject_scenario", "old-scenario-id");
    stepWithoutContractData.putArray("inject_assets").add("asset-1");
    stepWithoutContractData.putArray("inject_asset_groups").add("asset-group-1");
    stepWithoutContract.set("step_data", stepWithoutContractData);
    steps.add(stepWithoutContract);
    workflowNode.set("workflow_steps", steps);

    exportNode.set("exercise_workflow", workflowNode);

    // -- Act --
    workflowExportInitializer.enrichWorkflowStepDataForExport(
        exportNode, "exercise_workflow", objectMapper);

    // -- Assert --
    ObjectNode exportedWorkflow = (ObjectNode) exportNode.get("exercise_workflow");
    assertEquals(true, exportedWorkflow.get("workflow_rate_limit_enabled").asBoolean());
    assertEquals(7, exportedWorkflow.get("workflow_max_attempts").asInt());
    assertEquals(42L, exportedWorkflow.get("workflow_max_temporal_rate_seconds").asLong());
    assertEquals(true, exportedWorkflow.get("workflow_timeout_enabled").asBoolean());
    assertEquals(300L, exportedWorkflow.get("workflow_timeout_seconds").asLong());
    assertEquals(true, exportedWorkflow.get("workflow_safe_mode_enabled").asBoolean());
    assertEquals(
        "event-condition-id",
        exportedWorkflow.get("workflow_standalone_conditions").get(0).get("condition_id").asText());

    ArrayNode exportedRules = (ArrayNode) exportedWorkflow.get("workflow_scope_rules");
    assertEquals(3, exportedRules.size());
    assertScopeRule(
        (ObjectNode) exportedRules.get(0),
        ScopeRuleSelectedMode.ALLOWLIST.name(),
        ScopeRuleSource.MANUAL.name(),
        "10.0.0.1",
        ScopeRuleValueType.IP.name());
    assertScopeRule(
        (ObjectNode) exportedRules.get(1),
        ScopeRuleSelectedMode.DENYLIST.name(),
        ScopeRuleSource.CSV.name(),
        "corp.local",
        ScopeRuleValueType.DOMAIN.name());
    assertScopeRule(
        (ObjectNode) exportedRules.get(2),
        ScopeRuleSelectedMode.ALLOWLIST.name(),
        ScopeRuleSource.MANUAL.name(),
        "10.0.0.0/24",
        ScopeRuleValueType.IP_SUBNET.name());

    assertEquals(
        "INJECT", exportedWorkflow.get("workflow_steps").get(0).get("step_action_class").asText());
    assertEquals(
        "INJECT", exportedWorkflow.get("workflow_steps").get(1).get("step_action_class").asText());

    ObjectNode textualStepData =
        (ObjectNode)
            objectMapper.readTree(
                exportedWorkflow.get("workflow_steps").get(0).get("step_data").asText());
    ObjectNode objectStepDataAfter =
        (ObjectNode) exportedWorkflow.get("workflow_steps").get(1).get("step_data");
    ObjectNode stepDataWithoutContract =
        (ObjectNode) exportedWorkflow.get("workflow_steps").get(2).get("step_data");

    ObjectNode textualContract = (ObjectNode) textualStepData.get("inject_injector_contract");
    ObjectNode objectContract = (ObjectNode) objectStepDataAfter.get("inject_injector_contract");
    assertContractMetadata(textualContract);
    assertContractMetadata(objectContract);
    assertEquals(true, textualContract.get("listened").asBoolean());
    assertEquals(true, objectContract.get("listened").asBoolean());
    assertEquals(
        true, textualContract.get("injector_contract_payload").get("listened").asBoolean());
    assertEquals(true, objectContract.get("injector_contract_payload").get("listened").asBoolean());
    assertStepDataDefaults(textualStepData);
    assertStepDataDefaults(objectStepDataAfter);
    assertStepDataDefaults(stepDataWithoutContract);
  }

  private static void assertStepDataDefaults(ObjectNode stepData) {
    assertTrue(stepData.has("inject_id"));
    assertTrue(stepData.get("inject_id").isNull());
    assertTrue(stepData.has("inject_status"));
    assertTrue(stepData.get("inject_status").isNull());
    assertTrue(stepData.has("inject_depends_on"));
    assertTrue(stepData.get("inject_depends_on").isArray());
    assertEquals(0, stepData.get("inject_depends_on").size());
    assertFalse(stepData.has("inject_assets"));
    assertFalse(stepData.has("inject_asset_groups"));
    assertTrue(stepData.has("inject_exercise"));
    assertTrue(stepData.get("inject_exercise").isNull());
    assertTrue(stepData.has("inject_scenario"));
    assertTrue(stepData.get("inject_scenario").isNull());
  }

  private static void assertContractMetadata(ObjectNode contractNode) {
    assertEquals("contract-id", contractNode.get("injector_contract_id").asText());
    ArrayNode domains = (ArrayNode) contractNode.get("injector_contract_domains");
    assertEquals(2, domains.size());
    assertTrue(containsText(domains, "domain-id-1"));
    assertTrue(containsText(domains, "domain-id-2"));

    ArrayNode tags = (ArrayNode) contractNode.get("injector_contract_tags");
    assertEquals(2, tags.size());
    assertTrue(containsText(tags, "tag-id-1"));
    assertTrue(containsText(tags, "tag-id-2"));
  }

  private static boolean containsText(ArrayNode values, String expected) {
    for (int i = 0; i < values.size(); i++) {
      if (expected.equals(values.get(i).asText())) {
        return true;
      }
    }
    return false;
  }

  private static ObjectNode scopeRule(
      ObjectMapper objectMapper, String mode, String source, String value, String valueType) {
    ObjectNode rule = objectMapper.createObjectNode();
    rule.put("workflow_scope_rule_selected_mode", mode);
    rule.put("workflow_scope_rule_source", source);
    rule.put("workflow_scope_rule_value", value);
    rule.put("workflow_scope_rule_value_type", valueType);
    return rule;
  }

  private static void assertScopeRule(
      ObjectNode rule, String mode, String source, String value, String valueType) {
    assertEquals(mode, rule.get("workflow_scope_rule_selected_mode").asText());
    assertEquals(source, rule.get("workflow_scope_rule_source").asText());
    assertEquals(value, rule.get("workflow_scope_rule_value").asText());
    assertEquals(valueType, rule.get("workflow_scope_rule_value_type").asText());
  }

  private static Domain domain(String id) {
    Domain domain = new Domain();
    domain.setId(id);
    domain.setName(id);
    domain.setColor("#000000");
    return domain;
  }

  private static Tag tag(String id) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(id);
    return tag;
  }

  @Test
  void given_initializeWorkflow_should_requestStandaloneConditionsExcludingMapperType() {
    // -- Arrange --
    WorkflowExportInitializer workflowExportInitializer = new WorkflowExportInitializer();
    ConditionRepository conditionRepository = mock(ConditionRepository.class);
    ReflectionTestUtils.setField(
        workflowExportInitializer, "conditionRepository", conditionRepository);

    Workflow workflow = new Workflow();
    workflow.setId("workflow-id");
    workflow.setSteps(new java.util.ArrayList<>());
    when(conditionRepository.findAllByWorkflowIdAndConditionParentIsNullAndTypeNot(
            "workflow-id", ConditionType.MAPPER))
        .thenReturn(java.util.List.of(new Condition()));

    // -- Act --
    workflowExportInitializer.initialize(workflow, false);

    // -- Assert --
    verify(conditionRepository)
        .findAllByWorkflowIdAndConditionParentIsNullAndTypeNot("workflow-id", ConditionType.MAPPER);
  }
}
