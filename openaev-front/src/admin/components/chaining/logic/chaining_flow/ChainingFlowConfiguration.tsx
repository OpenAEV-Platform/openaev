import { useCallback, useMemo, useState } from 'react';

import {
  createCondition,
  createStep,
  updateCondition,
  updateStep,
} from '../../../../../actions/chaining/chaining-actions';
import { useFormatter } from '../../../../../components/i18n';
import type {
  InjectInput,
  PayloadSimple,
  ScopeAssetOutput,
  ThreatArsenalAction,
} from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import AddActionList from '../drawer/AddActionList';
import AddComponentDrawer from '../drawer/AddComponentDrawer';
import ConfigureActionDetail from '../drawer/ConfigureActionDetail';
import { mapFieldLinksToStepConditions } from '../drawer/ConfigureActionDetail.utils';
import ConfigureEventDetail from '../events/ConfigureEventDetail';
import {
  conditionGroupsToApi,
  type EventFormData,
} from '../events/event-types';
import { resolveConditionKeyTypes } from '../logic-flow-helpers';
import type { ActionDetailData, ActionMeta, EventMeta } from '../types';

export type DrawerView = 'closed' | 'choose' | 'action' | 'actionDetail' | 'event';

interface ChainingFlowConfigurationProps {
  workflowId: string | undefined;
  validAssets: ScopeAssetOutput[];
  drawerView: DrawerView;
  onDrawerViewChange: (view: DrawerView) => void;
  editingStep: {
    stepId: string;
    meta: ActionMeta;
  } | null;
  onEditingStepChange: (step: {
    stepId: string;
    meta: ActionMeta;
  } | null) => void;
  editingEvent: {
    eventId: string;
    meta: EventMeta;
  } | null;
  onEditingEventChange: (event: {
    eventId: string;
    meta: EventMeta;
  } | null) => void;
  onStepCreated: () => void;
  onEventCreated: () => void;
  eventCount: number;
  /** When set, the action list opens pre-filtered to actions that produce this output type. */
  compatibleActionFilter?: string;
  /** When set, newly created actions are linked to this event (added via the event node "+"). */
  linkToEventId?: string;
}

const ChainingFlowConfiguration = ({
  workflowId,
  validAssets,
  drawerView,
  onDrawerViewChange,
  editingStep,
  onEditingStepChange,
  editingEvent,
  onEditingEventChange,
  onStepCreated,
  onEventCreated,
  eventCount,
  compatibleActionFilter,
  linkToEventId,
}: ChainingFlowConfigurationProps) => {
  const { t } = useFormatter();

  // -- Action-specific state --
  const [selectedAction, setSelectedAction] = useState<ThreatArsenalAction | null>(null);

  // Derive editing data from editingStep
  const { editingStepId, editingInitialData, editingAction } = useMemo(() => {
    if (!editingStep) return {
      editingStepId: null,
      editingInitialData: undefined,
      editingAction: null,
    };

    const { stepId, meta } = editingStep;

    const pseudoAction = {
      injector_contract_id: meta.inject_injector_contract ?? '',
      action_labels: { en: meta.inject_title },
      action_attack_patterns_ids: meta.inject_attack_patterns_ids ?? [],
      action_injector_type: meta.inject_injector,
      action_payload: meta.inject_payload_type ? { payload_type: meta.inject_payload_type } as PayloadSimple : undefined,
    } as unknown as ThreatArsenalAction;

    const initialData: ActionDetailData = {
      inject_title: meta.inject_title,
      inject_injector_contract: meta.inject_injector_contract ?? '',
      inject_injector: meta.inject_injector,
      inject_assets: meta.inject_assets ?? [],
      inject_content: meta.inject_content ?? {},
      inject_field_links: {},
      contract_fields: meta.contract_fields ?? [],
    };

    if (meta.step_conditions) {
      const links: Record<string, {
        outputTypes: string[];
        localScope: boolean;
      }> = {};
      for (const cond of meta.step_conditions) {
        if (cond.condition_key) {
          const outputTypes = resolveConditionKeyTypes(cond as unknown as Record<string, unknown>);
          links[cond.condition_key] = {
            outputTypes,
            localScope: cond.condition_mapping_type === 'LOCAL',
          };
        }
      }
      initialData.inject_field_links = links;
    }

    return {
      editingStepId: stepId,
      editingInitialData: initialData,
      editingAction: pseudoAction,
    };
  }, [editingStep]);

  const handleCloseAll = useCallback(() => {
    onDrawerViewChange('closed');
    setSelectedAction(null);
    onEditingStepChange(null);
    onEditingEventChange(null);
  }, [onDrawerViewChange, onEditingStepChange, onEditingEventChange]);

  const handleSelectComponent = (type: 'action' | 'event') => {
    if (type === 'action') {
      onDrawerViewChange('action');
    } else {
      onDrawerViewChange('event');
    }
  };

  const handleBackToChoose = () => onDrawerViewChange('choose');

  // -- Actions --

  const handleAddActions = async (selectedActions: ThreatArsenalAction[]) => {
    if (!workflowId || selectedActions.length === 0) return;

    const promises = selectedActions.map((action) => {
      const title = action.action_labels?.en
        ?? action.action_labels?.fr
        ?? 'Untitled action';

      // No step_conditions: the backend applies the contract auto-links.
      return createStep({
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION' as const,
        step_condition_ids: linkToEventId ? [linkToEventId] : [],
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

    try {
      const results = await Promise.allSettled(promises);
      const successCount = results.filter(r => r.status === 'fulfilled').length;
      if (successCount > 0) {
        MESSAGING$.notifySuccess(
          t('{count} action(s) added successfully.', { count: String(successCount) }),
        );
      }
    } finally {
      onDrawerViewChange('closed');
      onStepCreated();
    }
  };

  const handleSelectAction = (action: ThreatArsenalAction) => {
    setSelectedAction(action);
    onDrawerViewChange('actionDetail');
  };

  const handleBackToActionList = () => {
    setSelectedAction(null);
    onDrawerViewChange('action');
  };

  const handleSaveActionDetail = async (data: ActionDetailData) => {
    if (!workflowId) return;

    // Always sent, even empty: it tells the backend not to re-apply the contract auto-links.
    const stepConditions = mapFieldLinksToStepConditions(data.inject_field_links);

    const stepPayload = {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION' as const,
      step_condition_ids: editingStep?.meta.step_condition_ids ?? (linkToEventId ? [linkToEventId] : []),
      step_conditions: stepConditions,
      step_data_step: {
        inject_title: data.inject_title,
        inject_injector_contract: data.inject_injector_contract,
        inject_assets: data.inject_assets,
        inject_content: data.inject_content,
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
      handleCloseAll();
      onStepCreated();
    });
  };

  // -- Events --
  const handleSaveEvent = async (data: EventFormData) => {
    if (!workflowId) return;

    const apiConditions = conditionGroupsToApi(data.conditionGroups, data.groupOperators);
    const event = {
      event_name: data.name,
      event_description: data.description || undefined,
      event_workflow_id: workflowId,
      event_conditions: apiConditions,
    };

    try {
      if (editingEvent) {
        await updateCondition(editingEvent.eventId, event);
        MESSAGING$.notifySuccess(t('Event updated successfully.'));
      } else {
        await createCondition(event);
        MESSAGING$.notifySuccess(t('Event added successfully.'));
        onEventCreated();
      }
      handleCloseAll();
      onStepCreated();
    } catch {
      if (editingEvent) MESSAGING$.notifyError(t('Failed to update event.'));
      else MESSAGING$.notifyError(t('Failed to create event.'));
    }
  };

  // Resolve the action to show in ConfigureActionDetail
  const activeAction = editingAction ?? selectedAction;

  return (
    <>
      <AddComponentDrawer
        open={drawerView === 'choose'}
        onClose={handleCloseAll}
        onSelect={handleSelectComponent}
      />
      <AddActionList
        key={compatibleActionFilter ?? ''}
        open={drawerView === 'action'}
        onClose={handleCloseAll}
        onBack={handleBackToChoose}
        onAddActions={handleAddActions}
        onSelectAction={handleSelectAction}
        compatibleActionFilter={compatibleActionFilter}
      />
      <ConfigureActionDetail
        open={drawerView === 'actionDetail'}
        action={activeAction}
        validAssets={validAssets}
        initialData={editingInitialData}
        onClose={handleCloseAll}
        onBack={editingStepId ? handleCloseAll : handleBackToActionList}
        onBackToRoot={handleCloseAll}
        onSave={handleSaveActionDetail}
      />
      <ConfigureEventDetail
        open={drawerView === 'event'}
        onClose={handleCloseAll}
        onBack={editingEvent ? handleCloseAll : handleBackToChoose}
        onSave={handleSaveEvent}
        initialData={editingEvent?.meta.formData}
        isEditing={!!editingEvent}
        defaultEventName={!editingEvent ? `Event ${eventCount + 1}` : undefined}
      />
    </>
  );
};

export default ChainingFlowConfiguration;
