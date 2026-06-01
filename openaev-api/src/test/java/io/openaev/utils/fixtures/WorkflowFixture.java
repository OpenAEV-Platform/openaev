package io.openaev.utils.fixtures;

import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WorkflowFixture {

  public static Workflow getDefaultWorkflowTemplate() {
    Workflow workflow = new Workflow();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setWorkflowTemplate(null);
    workflow.setWorkflowsExecuted(new ArrayList<>());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }

  public static Workflow getDefaultWorkflowExecution(WorkflowStatus status) {
    Workflow workflow = new Workflow();
    workflow.setStatus(status);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }

  public static List<WorkflowScopeRuleInput> getDefaultWorkflowScopeRuleInputList() {
    WorkflowScopeRuleInput ipRule =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
            .ruleSource(ScopeRuleSource.MANUAL)
            .ruleValue("10.10.10.10")
            .build();
    WorkflowScopeRuleInput domainRule =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
            .ruleSource(ScopeRuleSource.MANUAL)
            .ruleValue("example.org")
            .build();
    WorkflowScopeRuleInput assetRule =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
            .ruleSource(ScopeRuleSource.ASSET)
            .ruleValue("asset-123")
            .build();
    WorkflowScopeRuleInput subnetRule =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.DENYLIST)
            .ruleSource(ScopeRuleSource.MANUAL)
            .ruleValue("10.10.10.0/24")
            .build();
    WorkflowScopeRuleInput assetGroupRule =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.DENYLIST)
            .ruleSource(ScopeRuleSource.ASSET_GROUP)
            .ruleValue("asset-group-1")
            .build();

    return List.of(ipRule, domainRule, assetRule, subnetRule, assetGroupRule);
  }
}
