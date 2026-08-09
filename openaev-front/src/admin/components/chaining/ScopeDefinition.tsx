import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback } from 'react';

import { fetchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { fetchEndpoints } from '../../../actions/assets/endpoint-actions';
import {
  fetchWorkflowConfiguration,
  updateWorkflowConfiguration,
} from '../../../actions/chaining/workflow-actions';
import type { WorkflowConfigurationHelper } from '../../../actions/chaining/workflow-helper';
import { fetchTeams } from '../../../actions/teams/team-actions';
import { fetchPlayers } from '../../../actions/users/User';
import { useHelper } from '../../../store';
import type { ScopeVariableInput, WorkflowConfigurationInput, WorkflowScopeRuleInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useLivePolling from '../../../utils/hooks/useLivePolling';
import ChainingUpdatedBanner from './ChainingUpdatedBanner';
import ScopeExecutionLimits from './ScopeExecutionLimits';
import ScopeRules from './ScopeRules';
import ScopeVariables from './ScopeVariables';
import useSnapshotUpdated from './useSnapshotUpdated';

interface ScopeDefinitionProps {
  workflowId: string;
  /** Read-only inspection mode (autonomous runs OR launched simulations): the scope is rendered but
     *  made non-interactive. */
  readOnly?: boolean;
  /** The scope belongs to an autonomous (AI-driven) run: swap the chaining-engine timeout for the
     *  OpenAEV-owned session timeout and hide the per-step rate limit (it does not apply). */
  autonomous?: boolean;
  /** OpenAEV-owned autonomous session timeout in seconds (default 24h). Only used when autonomous. */
  autonomousTimeoutSeconds?: number | null;
}

const ScopeDefinition = ({
  workflowId,
  readOnly = false,
  autonomous = false,
  autonomousTimeoutSeconds,
}: ScopeDefinitionProps) => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Fetching data
  const { workflowConfiguration } = useHelper((helper: WorkflowConfigurationHelper) => ({ workflowConfiguration: helper.getWorkflowConfiguration(workflowId) }));

  // The Scope tab already loads the workflow configuration and the endpoint / asset-group inventory
  // below, so the drift is resolved from data on hand - no extra fetching by the banner.
  const updatedAssets = useSnapshotUpdated({ workflowConfiguration });

  useDataLoader(() => {
    dispatch(fetchWorkflowConfiguration(workflowId));
    dispatch(fetchEndpoints());
    dispatch(fetchAssetGroups());
    dispatch(fetchTeams());
    dispatch(fetchPlayers());
  });

  // Keep the Scope tab live: the AI edits the allow/deny lists, variables and limits during a run, so
  // re-read the workflow configuration on a visible cadence (the reference lists rarely move mid-run,
  // so only the configuration is polled). It flows through the store, so the cards reflect the latest
  // scope without a manual reload and without disturbing any open edit dialog (that is local state).
  useLivePolling(() => {
    dispatch(fetchWorkflowConfiguration(workflowId));
  }, { enabled: !!workflowId });

  type WorkflowScopeRuleLike = Partial<WorkflowScopeRuleInput> & { get?: (key: keyof WorkflowScopeRuleInput) => unknown };
  type ScopeVariableLike = Partial<ScopeVariableInput> & { get?: (key: keyof ScopeVariableInput) => unknown };

  const toWorkflowScopeRuleInput = (r: WorkflowScopeRuleLike): WorkflowScopeRuleInput => ({
    workflow_scope_rule_id:
            r.workflow_scope_rule_id ?? (r.get?.('workflow_scope_rule_id') as string | undefined),
    workflow_scope_rule_selected_mode:
            r.workflow_scope_rule_selected_mode
            ?? (r.get?.('workflow_scope_rule_selected_mode') as 'ALLOWLIST' | 'DENYLIST'),
    workflow_scope_rule_source:
            r.workflow_scope_rule_source
            ?? (r.get?.('workflow_scope_rule_source') as 'ASSET' | 'ASSET_GROUP' | 'TEAM' | 'PLAYER' | 'MANUAL' | 'CSV'),
    workflow_scope_rule_value:
            r.workflow_scope_rule_value ?? (r.get?.('workflow_scope_rule_value') as string),
  });

  const toScopeVariableInput = (v: ScopeVariableLike): ScopeVariableInput => ({
    scope_variable_id: v.scope_variable_id ?? (v.get?.('scope_variable_id') as string | undefined),
    scope_variable_key: v.scope_variable_key ?? (v.get?.('scope_variable_key') as string) ?? '',
    scope_variable_type: (v.scope_variable_type ?? v.get?.('scope_variable_type') ?? 'text') as ScopeVariableInput['scope_variable_type'],
    scope_variable_value: v.scope_variable_value ?? (v.get?.('scope_variable_value') as string | undefined) ?? '',
    scope_variable_description: v.scope_variable_description ?? (v.get?.('scope_variable_description') as string | undefined),
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
      workflow_scope_variables: workflowConfiguration?.workflow_scope_variables
        ? Array.from(
            workflowConfiguration.workflow_scope_variables as Iterable<ScopeVariableLike>,
          ).map(toScopeVariableInput)
        : [],
      ...overrides,
    };
    dispatch(updateWorkflowConfiguration(workflowId, input));
  }, [workflowConfiguration, workflowId, dispatch]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      {/* Advisory banner: warns when a launched simulation's scope drifted from its launch snapshot. */}
      <ChainingUpdatedBanner updatedAssets={updatedAssets} />
      <Box
        sx={{
          // A balanced 2x2 card grid: row 1 pairs the allow-list and deny-list; row 2 pairs the
          // variables card with a combined time-out + rate-limit card. Cells stretch to equal height
          // per row so the screen reads as four aligned cards rather than a ragged stack.
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            md: '1fr 1fr',
          },
          gap: theme.spacing(3),
          alignItems: 'stretch',
          ...(readOnly
            ? {
                // Keep the scope visible for inspection but block every mutation on autonomous runs -
                // and make the (otherwise primary-blue) add / delete / toggle affordances actually
                // read as disabled, since the AI owns the scope and they are not clickable here.
                'pointerEvents': 'none',
                'userSelect': 'text',
                '& .MuiButton-root, & .MuiIconButton-root': { color: theme.palette.text.disabled },
                '& .MuiSwitch-root': { opacity: 0.5 },
              }
            : {}),
        }}
        aria-disabled={readOnly || undefined}
      >
        {/* Row 1: allow list | deny list (ScopeRules renders both cards as a fragment). */}
        <ScopeRules workflowConfiguration={workflowConfiguration} onUpdate={handleUpdate} />
        {/* Row 2: variables | combined execution limits. */}
        <ScopeVariables workflowConfiguration={workflowConfiguration} onUpdate={handleUpdate} />
        <ScopeExecutionLimits
          workflowConfiguration={workflowConfiguration}
          onUpdate={handleUpdate}
          autonomous={autonomous}
          autonomousTimeoutSeconds={autonomousTimeoutSeconds}
        />
      </Box>
    </Box>
  );
};

export default ScopeDefinition;
