package io.openaev.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.WorkflowScopeRuleUtils;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
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
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String WORKFLOW_STEPS = "workflow_steps";
  private static final String WORKFLOW_SCOPE_RULES = "workflow_scope_rules";
  private static final String WORKFLOW_SCOPE_RULE_TEAM_MEMBERS = "workflow_scope_rule_team_members";
  private static final String WORKFLOW_SCOPE_RULE_TEAM_MEMBER = "workflow_scope_rule_team_member";
  private static final String WORKFLOW_SCOPE_RULE_VALUE = "workflow_scope_rule_value";
  private static final String WORKFLOW_SCOPE_RULE_SOURCE = "workflow_scope_rule_source";
  private static final String INJECT_TEAMS = "inject_teams";
  private static final String INJECT_ALL_TEAMS = "inject_all_teams";
  private static final String STEP_DATA = "step_data";
  private static final String INJECT_INJECTOR_CONTRACT = "inject_injector_contract";
  private static final String INJECTOR_CONTRACT_ID = "injector_contract_id";

  @Resource private ConditionRepository conditionRepository;
  @Resource private InjectorContractRepository injectorContractRepository;
  @Resource private TeamRepository teamRepository;
  @Resource private InjectorContractContentUtils injectorContractContentUtils;

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

  public void enrichWorkflowDataForExport(
      ObjectNode exportNode, String workflowKey, ObjectMapper objectMapper) {
    JsonNode workflowNode = exportNode.get(workflowKey);
    if (!(workflowNode instanceof ObjectNode workflowObject)) {
      return;
    }
    filterAssetScopeRules(workflowObject, objectMapper);
    enrichWorkflowScopeRuleTeamMembers(workflowObject, objectMapper);
    JsonNode stepsNode = workflowObject.get(WORKFLOW_STEPS);
    if (!(stepsNode instanceof ArrayNode stepsArray)) {
      return;
    }
    stepsArray.forEach(stepNode -> enrichStepData(workflowObject, stepNode, objectMapper));
  }

  /**
   * Collects the tags referenced by every workflow step's injector contract and nested output
   * parsers so the export keeps the full chaining metadata in one payload.
   */
  public Set<Tag> collectWorkflowTags(Workflow workflow) {
    Set<Tag> tags = new HashSet<>();
    if (workflow == null || workflow.getSteps() == null) {
      return tags;
    }

    for (Step step : workflow.getSteps()) {
      if (!StringUtils.hasText(step.getData())) {
        continue;
      }
      try {
        JsonNode parsedStepData = objectMapper.readTree(step.getData());
        if (!(parsedStepData instanceof ObjectNode stepDataObject)) {
          continue;
        }
        String injectorContractId =
            extractInjectorContractId(stepDataObject.get(INJECT_INJECTOR_CONTRACT));
        if (!StringUtils.hasText(injectorContractId)) {
          continue;
        }
        injectorContractRepository
            .findById(injectorContractId)
            .ifPresent(
                injectorContract -> {
                  tags.addAll(injectorContract.getTags());
                  if (injectorContract.getPayload() != null) {
                    injectorContract.getPayload().getOutputParsers().stream()
                        .flatMap(parser -> parser.getContractOutputElements().stream())
                        .flatMap(element -> element.getTags().stream())
                        .forEach(tags::add);
                  }
                });
      } catch (Exception e) {
        log.warn("Unable to collect workflow tags from step_data", e);
      }
    }
    return tags;
  }

  // -- chained workflow export --
  //
  // The export helpers below keep chained workflows round-trippable by preserving scope
  // definitions, team membership companions, and audience targets in the JSON payload.

  /**
   * Removes asset scope rules from the export payload so chained workflows only carry the scope
   * data that matters for the chained import/export flow.
   */
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

  /**
   * Enriches a single exported step by expanding the contract snapshot, restoring absent fields,
   * and copying workflow-scope teams when the step expects an audience target.
   */
  private void enrichStepData(
      ObjectNode workflowObject, JsonNode stepNode, ObjectMapper objectMapper) {
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
          enrichedContractNode.set(
              "injector_contract_domains", objectMapper.valueToTree(injectorContract.getDomains()));
          JsonNode existingContractNode = stepDataObject.get(INJECT_INJECTOR_CONTRACT);
          if (existingContractNode instanceof ObjectNode existingContractObject) {
            preserveAbsentFieldsRecursively(enrichedContractNode, existingContractObject);
          }
          stepDataObject.set(INJECT_INJECTOR_CONTRACT, enrichedContractNode);
          enrichAudienceTeamsFromWorkflowScope(
              workflowObject, stepDataObject, injectorContract, objectMapper);
        }
      }
      normalizeStepDataFieldsForExport(stepDataObject, objectMapper);
      setStepData(stepObject, stepDataObject, isTextual, objectMapper);
    } catch (Exception e) {
      log.warn("Unable to enrich workflow step_data for export", e);
    }
  }

  // -- export payload preservation --
  //
  // These helpers keep the serialized step shape compatible with legacy imports and ensure the
  // exporter does not drop runtime-free fields that chained import still needs.

  /**
   * Restores fields omitted by mixins so the importer can round-trip the original step shape
   * instead of inferring missing values.
   */
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

  /**
   * Normalizes exported step data by clearing runtime-only asset and scenario/exercise fields while
   * keeping the chaining-specific placeholders explicit.
   */
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

  // -- export scope audience helpers --
  //
  // These helpers copy workflow-scope team ids into step data and export the team-member
  // companion collection so imports can restore the exact same audience context.

  /**
   * Copies team ids from workflow scope into step_data when the contract supports audience
   * targeting and the step does not already declare an audience.
   */
  private void enrichAudienceTeamsFromWorkflowScope(
      ObjectNode workflowObject,
      ObjectNode stepDataObject,
      InjectorContract injectorContract,
      ObjectMapper objectMapper) {
    if (stepDataObject.path(INJECT_ALL_TEAMS).asBoolean(false)) {
      return;
    }
    JsonNode existingTeamsNode = stepDataObject.get(INJECT_TEAMS);
    if (existingTeamsNode != null && existingTeamsNode.isArray() && !existingTeamsNode.isEmpty()) {
      return;
    }
    if (!injectorContractContentUtils.hasField(injectorContract, "teams")) {
      return;
    }

    List<String> workflowScopeTeamIds = collectWorkflowScopeTeamIds(workflowObject);
    if (workflowScopeTeamIds.isEmpty()) {
      return;
    }
    ArrayNode workflowScopeTeams = objectMapper.createArrayNode();
    workflowScopeTeamIds.forEach(workflowScopeTeams::add);
    stepDataObject.set(INJECT_TEAMS, workflowScopeTeams);
  }

  /**
   * Extracts the team ids referenced by the workflow scope rules for export-time audience
   * enrichment and team-member expansion.
   */
  private static List<String> collectWorkflowScopeTeamIds(ObjectNode workflowObject) {
    JsonNode scopeRulesNode = workflowObject.get(WORKFLOW_SCOPE_RULES);
    if (!(scopeRulesNode instanceof ArrayNode scopeRulesArray)) {
      return List.of();
    }

    List<String> teamIds = new ArrayList<>();
    scopeRulesArray.forEach(
        ruleNode -> {
          if (!ScopeRuleSource.TEAM
              .name()
              .equals(ruleNode.path(WORKFLOW_SCOPE_RULE_SOURCE).asText())) {
            return;
          }
          String teamId = ruleNode.path(WORKFLOW_SCOPE_RULE_VALUE).asText(null);
          if (StringUtils.hasText(teamId)) {
            teamIds.add(teamId);
          }
        });
    return teamIds;
  }

  // -- export serialization helpers --
  //
  // These helpers keep the final JSON consistent with the original textual/object step shape and
  // the serializer's injector-contract expectations.

  /** Writes the enriched step data back using the original textual or structured JSON shape. */
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

  /**
   * Extracts the injector contract id from either the legacy textual shape or the newer object
   * shape.
   */
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

  /**
   * Initializes contract collections required by the export serializer before Jackson traverses the
   * workflow graph.
   */
  private static void initializeInjectorContractForExport(InjectorContract injectorContract) {
    injectorContract
        .getAttackPatterns()
        .forEach(attackPattern -> Hibernate.initialize(attackPattern.getKillChainPhases()));
  }

  // -- workflow-scope team membership export --
  //
  // TEAM scope rules export their member list as a companion collection so imports can rebuild the
  // team membership snapshot even when the live team has changed later.

  /**
   * Serializes the users attached to each TEAM scope rule so chained imports can rebuild the same
   * audience context.
   */
  private void enrichWorkflowScopeRuleTeamMembers(
      ObjectNode workflowObject, ObjectMapper objectMapper) {
    JsonNode scopeRulesNode = workflowObject.get(WORKFLOW_SCOPE_RULES);
    if (!(scopeRulesNode instanceof ArrayNode scopeRulesArray)) {
      return;
    }
    Set<String> exportedTeamIds = new HashSet<>();
    ArrayNode teamMembersArray = objectMapper.createArrayNode();
    scopeRulesArray.forEach(
        ruleNode -> {
          if (!ScopeRuleSource.TEAM
              .name()
              .equals(ruleNode.path(WORKFLOW_SCOPE_RULE_SOURCE).asText())) {
            return;
          }
          String teamId = ruleNode.path(WORKFLOW_SCOPE_RULE_VALUE).asText(null);
          if (!StringUtils.hasText(teamId) || !exportedTeamIds.add(teamId)) {
            return;
          }
          teamRepository
              .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
              .ifPresent(
                  team -> {
                    Hibernate.initialize(team.getUsers());
                    ObjectNode teamMembersNode = objectMapper.createObjectNode();
                    teamMembersNode.put(WORKFLOW_SCOPE_RULE_VALUE, teamId);
                    ArrayNode teamUsers = objectMapper.createArrayNode();
                    team.getUsers()
                        .forEach(user -> teamUsers.add(toTeamMemberNode(objectMapper, user)));
                    teamMembersNode.set(WORKFLOW_SCOPE_RULE_TEAM_MEMBER, teamUsers);
                    teamMembersArray.add(teamMembersNode);
                  });
        });
    if (!teamMembersArray.isEmpty()) {
      workflowObject.set(WORKFLOW_SCOPE_RULE_TEAM_MEMBERS, teamMembersArray);
    }
  }

  /** Serializes a user into the compact workflow-scope team-member shape. */
  private static ObjectNode toTeamMemberNode(ObjectMapper objectMapper, User user) {
    ObjectNode memberNode = objectMapper.createObjectNode();
    memberNode.put("user_id", user.getId());
    memberNode.put("user_email", user.getEmail());
    memberNode.put("user_firstname", user.getFirstname());
    memberNode.put("user_lastname", user.getLastname());
    return memberNode;
  }
}
