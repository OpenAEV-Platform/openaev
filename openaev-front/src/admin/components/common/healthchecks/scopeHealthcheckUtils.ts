import type { HealthCheck, WorkflowScopeRuleOutput } from '../../../../utils/api-types';

const SCOPE_DEFINITION_EMPTY_WARNING: HealthCheck = {
  creation_date: '',
  detail: 'EMPTY',
  status: 'WARNING',
  type: 'SCOPE_DEFINITION',
};

const isScopeDefinitionEmptyHealthcheck = (healthcheck: HealthCheck): boolean =>
  healthcheck.type === 'SCOPE_DEFINITION' && healthcheck.detail === 'EMPTY';

export const hasAllowlistEntry = (workflowScopeRules: WorkflowScopeRuleOutput[] = []): boolean =>
  workflowScopeRules.some((rule: WorkflowScopeRuleOutput) =>
    rule.workflow_scope_rule_selected_mode === 'ALLOWLIST'
    && !!rule.workflow_scope_rule_value?.trim(),
  );

export const isScopeMissingForChaining = ({
  isChaining,
  workflowScopeRules,
  healthchecks,
}: {
  isChaining: boolean;
  workflowScopeRules?: WorkflowScopeRuleOutput[];
  healthchecks: HealthCheck[];
}): boolean =>
  isChaining
  && (
    !hasAllowlistEntry(workflowScopeRules ?? [])
    || healthchecks.some(isScopeDefinitionEmptyHealthcheck)
  );

export const getScopeAwareHealthchecks = ({
  healthchecks,
  isChaining,
  isScopeMissing,
}: {
  healthchecks: HealthCheck[];
  isChaining: boolean;
  isScopeMissing: boolean;
}): HealthCheck[] => {
  if (!isChaining) {
    return healthchecks;
  }
  const withoutScopeDefinition = healthchecks.filter((healthcheck: HealthCheck) => healthcheck.type !== 'SCOPE_DEFINITION');
  if (!isScopeMissing) {
    return withoutScopeDefinition;
  }
  const scopeDefinitionHealthcheck = healthchecks.find(isScopeDefinitionEmptyHealthcheck) ?? SCOPE_DEFINITION_EMPTY_WARNING;
  return [...withoutScopeDefinition, scopeDefinitionHealthcheck];
};
