import { useEffect } from 'react';

import { fetchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import type { AssetGroupsHelper } from '../../../actions/asset_groups/assetgroup-helper';
import type { EndpointHelper } from '../../../actions/assets/asset-helper';
import { fetchEndpoints } from '../../../actions/assets/endpoint-actions';
import { fetchWorkflowConfiguration } from '../../../actions/chaining/workflow-actions';
import type { WorkflowConfigurationHelper } from '../../../actions/chaining/workflow-helper';
import { fetchExercise } from '../../../actions/Exercise';
import type { ExercisesHelper } from '../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import type { WorkflowConfigurationOutput, WorkflowScopeRuleOutput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';

// Statuses signaling the frozen launch snapshot no longer matches the live composition: the scope
// updated while (DURING) or after (AFTER) the run, because an asset / group was edited (MODIFIED) or
// removed (DELETED).
const UPDATED_STATUSES = new Set<NonNullable<WorkflowScopeRuleOutput['workflow_scope_rule_status']>>([
  'MODIFIED_DURING_EXECUTION',
  'MODIFIED_AFTER_EXECUTION',
  'DELETED_DURING_EXECUTION',
  'DELETED_AFTER_EXECUTION',
]);

export interface UpdatedAsset {
  id: string;
  message: string;
}

interface UseChainingScopeDriftOptions {
  /**
     * Direct mode: the caller already holds the workflow configuration (e.g. the Scope tab, which loads
     * it - and the endpoint / asset-group inventory - itself). Mutually exclusive with simulationId.
     */
  workflowConfiguration?: WorkflowConfigurationOutput;
  /**
     * Self-resolving mode: the caller only knows which launched simulation is displayed (e.g. the attack
     * path page). The hook resolves the run's chaining workflow and loads everything it needs itself.
     */
  simulationId?: string;
}

/**
 * Resolves the drifted allow / deny entries of a chained scope into ready-to-display sentences, so the
 * {@link ChainingUpdatedBanner} can stay purely presentational.
 *
 * Direct mode (pass a resolved {@link UseChainingScopeDriftOptions.workflowConfiguration}) does no
 * fetching - the caller already loaded the configuration and the endpoint / asset-group inventory.
 * Self-resolving mode (pass a {@link UseChainingScopeDriftOptions.simulationId}) resolves the run's
 * workflow and loads that data itself.
 */
const useSnapshotUpdated = ({ workflowConfiguration, simulationId }: UseChainingScopeDriftOptions): UpdatedAsset[] => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Self-resolving mode: simulation -> chaining workflow -> configuration. In direct mode these stay
  // undefined and the caller-provided configuration is used as-is.
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: simulationId ? helper.getExercise(simulationId) : undefined }));
  const resolvedWorkflowId = exercise?.exercise_workflow_id;

  const { resolvedConfiguration, endpointsMap, assetGroupsMap } = useHelper(
    (helper: WorkflowConfigurationHelper & EndpointHelper & AssetGroupsHelper) => ({
      resolvedConfiguration: resolvedWorkflowId ? helper.getWorkflowConfiguration(resolvedWorkflowId) : undefined,
      endpointsMap: helper.getEndpointsMap(),
      assetGroupsMap: helper.getAssetGroupMaps(),
    }),
  );

  // Only fetch in self-resolving mode; direct callers (the Scope tab) already load these themselves.
  useEffect(() => {
    if (simulationId) {
      dispatch(fetchExercise(simulationId));
    }
  }, [dispatch, simulationId]);

  useEffect(() => {
    if (resolvedWorkflowId) {
      dispatch(fetchWorkflowConfiguration(resolvedWorkflowId));
      dispatch(fetchEndpoints());
      dispatch(fetchAssetGroups());
    }
  }, [dispatch, resolvedWorkflowId]);

  const effectiveConfiguration: WorkflowConfigurationOutput | undefined = simulationId ? resolvedConfiguration : workflowConfiguration;

  // Frozen-first resolution: a drift message is about a launched simulation, so the frozen launch
  // label (workflow_scope_rule_snapshot_start_label) is the authoritative name; the template-time
  // label snapshot (workflow_scope_rule_value_label) and the live inventory are fallbacks so
  // pre-snapshot rules stay readable. Team / player rules carry name labels in the same frozen
  // fields, so the shared frozen-first chain covers them too.
  const resolveLabel = (rule: WorkflowScopeRuleOutput): string => {
    const value = rule.workflow_scope_rule_value ?? '';
    const frozenLabel = rule.workflow_scope_rule_snapshot_start_label ?? rule.workflow_scope_rule_value_label ?? undefined;
    switch (rule.workflow_scope_rule_source) {
      case 'ASSET':
        return frozenLabel ?? endpointsMap[value]?.asset_name ?? t('Deleted asset');
      case 'ASSET_GROUP':
        return frozenLabel ?? assetGroupsMap[value]?.asset_group_name ?? t('Deleted asset group');
      default:
        return frozenLabel ?? (value || t('Loading...'));
    }
  };

  // Reuse the exact status label ScopeRules appends to a chip (e.g. "Modified during execution",
  // "Deleted after execution") - it is already translated for every enum value - and fold it into a
  // per-entry sentence typed by rule source (asset / team / person), so the banner reads
  // consistently with the chips it complements.
  const messageFor = (rule: WorkflowScopeRuleOutput, name: string): string => {
    const status = t(rule.workflow_scope_rule_status ?? '').toLowerCase();
    switch (rule.workflow_scope_rule_source) {
      case 'TEAM':
        return t('The team {name} has been {status}.', {
          name,
          status,
        });
      case 'PLAYER':
        return t('The person {name} has been {status}.', {
          name,
          status,
        });
      default:
        return t('The asset {name} has been {status}.', {
          name,
          status,
        });
    }
  };

  return (effectiveConfiguration?.workflow_scope_rules ?? [])
    .filter(rule => rule.workflow_scope_rule_status != null && UPDATED_STATUSES.has(rule.workflow_scope_rule_status))
    .map(rule => ({
      id: rule.workflow_scope_rule_id ?? `${rule.workflow_scope_rule_source}-${rule.workflow_scope_rule_value}`,
      message: messageFor(rule, resolveLabel(rule)),
    }));
};

export default useSnapshotUpdated;
