import { useCallback, useEffect, useState } from 'react';

import { createStep, fetchConditions, fetchSteps, updateStep } from '../../../../actions/chaining/chaining-actions';
import { fetchValidAssets } from '../../../../actions/chaining/workflow-actions';
import { useFormatter } from '../../../../components/i18n';
import type {
  ConditionCreateInput,
  EventOutput,
  InjectInput,
  ScopeAssetOutput,
  StepOutput,
  ThreatArsenalAction,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import LogicFlow from './chaining_flow/LogicFlow';
import AddActionList from './drawer/AddActionList';
import AddComponentDrawer from './drawer/AddComponentDrawer';
import ConfigureActionDetail from './drawer/ConfigureActionDetail';
import { type ActionDetailData, type ActionMeta } from './types';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
}

type DrawerView = 'closed' | 'choose' | 'action' | 'actionDetail';

/**
 * Resolve the primary MITRE tactic name from attack pattern IDs using the store maps.
 * Returns the tactic name with the lowest phase_order, or undefined if none found.
 */

const Logic = ({ workflowId, context }: LogicProps) => {
  const { t } = useFormatter();

  // Fetch computed valid assets (allowlist minus denylist)
  const [validAssets, setValidAssets] = useState<ScopeAssetOutput[]>([]);
  // Track whether existing steps/events exist
  const [hasExistingData, setHasExistingData] = useState<boolean | null>(null);
  // Key to force LogicFlow re-mount after adding a step
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    if (workflowId) {
      fetchValidAssets(workflowId).then((assets: ScopeAssetOutput[]) => {
        setValidAssets(assets);
      });
    }
  }, [workflowId]);

  // Check if there are existing steps or events
  useEffect(() => {
    if (!workflowId) return;
    Promise.all([
      fetchSteps(workflowId),
      fetchConditions(workflowId),
    ]).then(([stepsRes, conditionsRes]) => {
      const steps: StepOutput[] = stepsRes.data ?? [];
      const events: EventOutput[] = conditionsRes.data ?? [];
      setHasExistingData(steps.length > 0 || events.length > 0);
    });
  }, [workflowId]);

  const [drawerView, setDrawerView] = useState<DrawerView>('closed');
  const [selectedAction, setSelectedAction] = useState<ThreatArsenalAction | null>(null);
  const [editingStepId, setEditingStepId] = useState<string | null>(null);
  const [editingInitialData, setEditingInitialData] = useState<ActionDetailData | undefined>(undefined);

  const handleOpenDrawer = () => setDrawerView('choose');
  const handleCloseAll = () => {
    setDrawerView('closed');
    setSelectedAction(null);
    setEditingStepId(null);
    setEditingInitialData(undefined);
  };

  const handleSelectComponent = (type: 'action' | 'event') => {
    if (type === 'action') {
      setDrawerView('action');
    } else {
      // TODO: handle event creation
      setDrawerView('closed');
    }
  };

  const handleBackToChoose = () => setDrawerView('choose');

  const handleAddActions = async (selectedActions: ThreatArsenalAction[]) => {
    if (!workflowId || selectedActions.length === 0) return;

    // Create a step for each selected action in parallel
    const promises = selectedActions.map((action) => {
      const title = action.action_labels?.en
        ?? action.action_labels?.fr
        ?? 'Untitled action';

      return createStep({
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION' as const,
        step_data_step: {
          inject_title: title,
          inject_injector_contract: action.injector_contract_id,
          inject_assets: [],
          inject_content: {},
          inject_tags: [],
          inject_all_teams: false,
          inject_teams: [],
          inject_asset_groups: [],
          inject_documents: [],
          inject_depends_duration: 0,
          inject_depends_on: [],
        } as unknown as InjectInput,
      });
    });

    await Promise.all(promises);
    MESSAGING$.notifySuccess(
      t('{count} action(s) added successfully.').replace('{count}', String(selectedActions.length)),
    );
    setDrawerView('closed');
    setHasExistingData(true);
    setRefreshKey(k => k + 1);
  };

  const handleSelectAction = (action: ThreatArsenalAction) => {
    setSelectedAction(action);
    setDrawerView('actionDetail');
  };

  const handleBackToActionList = () => {
    setSelectedAction(null);
    setDrawerView('action');
  };

  const handleEditStep = useCallback((stepId: string, meta: ActionMeta) => {
    // Build a minimal ThreatArsenalAction-like object from the step metadata
    const pseudoAction = {
      injector_contract_id: meta.inject_injector_contract ?? '',
      action_labels: { en: meta.inject_title },
      action_attack_patterns_ids: meta.inject_attack_patterns_ids ?? [],
      action_injector_type: meta.inject_injector,
    } as unknown as ThreatArsenalAction;

    const initialData: ActionDetailData = {
      inject_title: meta.inject_title,
      inject_injector_contract: meta.inject_injector_contract ?? '',
      inject_injector: meta.inject_injector,
      inject_assets: meta.inject_assets ?? [],
      inject_content: {},
      inject_field_links: {},
      contract_fields: meta.contract_fields ?? [],
    };

    // Reconstruct field links from step_conditions
    if (meta.step_conditions) {
      const links: Record<string, {
        outputType: string;
        localScope: boolean;
      }> = {};
      for (const cond of meta.step_conditions) {
        if (cond.condition_key) {
          links[cond.condition_key] = {
            outputType: cond.condition_key_type ?? 'text',
            localScope: cond.condition_mapping_type === 'LOCAL',
          };
        }
      }
      initialData.inject_field_links = links;
    }

    setSelectedAction(pseudoAction);
    setEditingStepId(stepId);
    setEditingInitialData(initialData);
    setDrawerView('actionDetail');
  }, []);

  const handleSaveActionDetail = async (data: ActionDetailData) => {
    if (!workflowId) return;

    // Build step_conditions from field links (type + local/global scope)
    const stepConditions: ConditionCreateInput[] = Object.entries(data.inject_field_links).map(([fieldKey, link], i) => {
      const parts = link.outputType.split('.');
      return {
        condition_temporary_id: String(i),
        condition_type: 'MAPPER' as const,
        condition_key_type: parts[0] as ConditionCreateInput['condition_key_type'],
        condition_key_subtype: (parts.length > 1 ? parts.slice(1).join('.') : undefined) as ConditionCreateInput['condition_key_subtype'],
        condition_key: fieldKey,
        condition_mapping_type: (link.localScope ? 'LOCAL' : 'GLOBAL') as ConditionCreateInput['condition_mapping_type'],
      };
    });

    const stepPayload = {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION' as const,
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: data.inject_title,
        inject_injector_contract: data.inject_injector_contract,
        inject_assets: data.inject_assets,
        inject_content: data.inject_content,
        // Note: inject_attack_patterns_ids is NOT sent — derived from contract at display time.
        inject_tags: [],
        inject_all_teams: false,
        inject_teams: [],
        inject_asset_groups: [],
        inject_documents: [],
        inject_depends_duration: 0,
        inject_depends_on: [],
      } as unknown as InjectInput,
    };

    const savePromise = editingStepId
      ? updateStep(editingStepId, stepPayload)
      : createStep(stepPayload);

    savePromise.then(() => {
      const message = editingStepId
        ? t('Action updated successfully.')
        : t('Action added successfully.');
      MESSAGING$.notifySuccess(message);
      setDrawerView('closed');
      setSelectedAction(null);
      setEditingStepId(null);
      setEditingInitialData(undefined);
      setHasExistingData(true);
      setRefreshKey(k => k + 1);
    });
  };

  // Loading state
  if (hasExistingData === null) {
    return null;
  }

  return (
    <div style={{
      width: '100%',
      height: 'calc(100vh - 230px)',
      position: 'relative',
    }}
    >
      {hasExistingData && workflowId
        ? (
            <LogicFlow
              reloadTrigger={refreshKey}
              workflowId={workflowId}
              onAddComponent={handleOpenDrawer}
              onEditStep={handleEditStep}
            />
          )
        : (
            <AddComponentButton nodeCount={0} context={context} onClick={handleOpenDrawer} />
          )}
      <AddComponentDrawer
        open={drawerView === 'choose'}
        onClose={handleCloseAll}
        onSelect={handleSelectComponent}
      />
      <AddActionList
        open={drawerView === 'action'}
        onClose={handleCloseAll}
        onBack={handleBackToChoose}
        onAddActions={handleAddActions}
        onSelectAction={handleSelectAction}
      />
      <ConfigureActionDetail
        open={drawerView === 'actionDetail'}
        action={selectedAction}
        validAssets={validAssets}
        initialData={editingInitialData}
        onClose={handleCloseAll}
        onBack={editingStepId ? handleCloseAll : handleBackToActionList}
        onBackToRoot={handleCloseAll}
        onSave={handleSaveActionDetail}
      />
    </div>
  );
};

export default Logic;
