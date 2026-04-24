import { useTheme } from '@mui/material/styles';
import { useCallback } from 'react';

import { fetchAssetGroups } from '../../../../../actions/asset_groups/assetgroup-action';
import { fetchEndpoints } from '../../../../../actions/assets/endpoint-actions';
import {
  fetchWorkflowConfiguration,
  updateWorkflowConfiguration,
} from '../../../../../actions/chaining/workflow-actions';
import type { WorkflowConfigurationHelper } from '../../../../../actions/chaining/workflow-helper';
import { useHelper } from '../../../../../store';
import type { WorkflowConfigurationInput, WorkflowScopeRuleInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ScopeRateLimit from './ScopeRateLimit';
import ScopeRules from './ScopeRules';
import ScopeTimeOut from './ScopeTimeOut';
import ScopeVariables from './ScopeVariables';

interface ScopeDefinitionProps { workflowId: string }

const ScopeDefinition = ({ workflowId }: ScopeDefinitionProps) => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Fetching data
  const { workflowConfiguration } = useHelper((helper: WorkflowConfigurationHelper) => ({ workflowConfiguration: helper.getWorkflowConfiguration(workflowId) }));

  useDataLoader(() => {
    dispatch(fetchWorkflowConfiguration(workflowId));
    dispatch(fetchEndpoints());
    dispatch(fetchAssetGroups());
  });

  type WorkflowScopeRuleLike = Partial<WorkflowScopeRuleInput> & { get?: (key: keyof WorkflowScopeRuleInput) => unknown };

  const toWorkflowScopeRuleInput = (r: WorkflowScopeRuleLike): WorkflowScopeRuleInput => ({
    workflow_scope_rule_id:
            r.workflow_scope_rule_id ?? (r.get?.('workflow_scope_rule_id') as string | undefined),
    workflow_scope_rule_selected_mode:
            r.workflow_scope_rule_selected_mode
            ?? (r.get?.('workflow_scope_rule_selected_mode') as 'WHITELIST' | 'BLACKLIST'),
    workflow_scope_rule_source:
            r.workflow_scope_rule_source
            ?? (r.get?.('workflow_scope_rule_source') as 'ASSET' | 'ASSET_GROUP' | 'MANUAL' | 'CSV'),
    workflow_scope_rule_value:
            r.workflow_scope_rule_value ?? (r.get?.('workflow_scope_rule_value') as string),
  });

  const handleUpdate = useCallback((overrides: Partial<WorkflowConfigurationInput>) => {
    const input: WorkflowConfigurationInput = {
      workflow_configuration_timeout_enabled: workflowConfiguration?.workflow_configuration_timeout_enabled,
      workflow_configuration_timeout_seconds: workflowConfiguration?.workflow_configuration_timeout_seconds,
      workflow_configuration_rate_limit_enabled: workflowConfiguration?.workflow_configuration_rate_limit_enabled,
      workflow_configuration_max_attempts: workflowConfiguration?.workflow_configuration_max_attempts,
      workflow_configuration_max_temporal_rate_seconds: workflowConfiguration?.workflow_configuration_max_temporal_rate_seconds,
      workflow_configuration_safe_mode_enabled: workflowConfiguration?.workflow_configuration_safe_mode_enabled,
      workflow_scope_rules: workflowConfiguration?.workflow_scope_rules
        ? Array.from(
            workflowConfiguration.workflow_scope_rules as Iterable<WorkflowScopeRuleLike>,
          ).map(toWorkflowScopeRuleInput)
        : [],
      ...overrides,
    };
    dispatch(updateWorkflowConfiguration(workflowId, input));
  }, [workflowConfiguration, workflowId, dispatch]);

  return (
    <div style={{
      display: 'grid',
      gap: `${theme.spacing(3)} ${theme.spacing(3)}`,
    }}
    >
      <div style={{
        display: 'grid',
        gap: theme.spacing(2),
        gridTemplateColumns: '1fr 1fr',

      }}
      >
        <ScopeRules workflowConfiguration={workflowConfiguration} />
        <ScopeVariables />
      </div>
      <div style={{
        display: 'grid',
        gap: theme.spacing(2),
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <ScopeTimeOut
          workflowConfiguration={workflowConfiguration}
          onUpdate={handleUpdate}
        />
        <ScopeRateLimit
          workflowConfiguration={workflowConfiguration}
          onUpdate={handleUpdate}
        />
      </div>
    </div>
  );
};

export default ScopeDefinition;
