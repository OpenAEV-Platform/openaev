import { type ChainedScenarioInstallInput, type WorkflowConfigurationInput } from '../../../../utils/api-types';
import { EMPTY_SCOPE, type ScopeSelection, scopeSelectionCount, selectionToRules } from '../../autonomous/useAutonomousRunConfig';

type ExecutionLimits = Omit<WorkflowConfigurationInput, 'workflow_scope_rules' | 'workflow_scope_variables'>;

export interface ChainedScope {
  allow: ScopeSelection;
  deny: ScopeSelection;
  limits: ExecutionLimits;
}

export const NO_SCOPE: ChainedScope = {
  allow: EMPTY_SCOPE,
  deny: EMPTY_SCOPE,
  limits: {
    workflow_configuration_timeout_enabled: false,
    workflow_configuration_timeout_seconds: 3600,
    workflow_configuration_rate_limit_enabled: false,
  },
};

// A chained scenario acts only inside its scope: it needs an entry to allow.
export const hasScope = (scope: ChainedScope) => scopeSelectionCount(scope.allow) > 0;

export const toChainedInstallInput = (scope: ChainedScope): ChainedScenarioInstallInput => ({
  scenario_type: 'chained',
  workflow_configuration: {
    ...scope.limits,
    workflow_scope_rules: [...selectionToRules(scope.allow, 'ALLOWLIST'), ...selectionToRules(scope.deny, 'DENYLIST')],
  },
});
