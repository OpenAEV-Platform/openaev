import { BoltOutlined, TerminalOutlined } from '@mui/icons-material';
import { Box, ButtonBase, Step, StepLabel, Stepper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback, useMemo, useState } from 'react';

import {
  createCondition,
  createStep,
  updateCondition,
  updateStep,
} from '../../../../../actions/chaining/chaining-actions';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type {
  ConditionCreateInput,
  InjectInput,
  PayloadSimple,
  ScopeAssetOutput,
  ScopeTeamOutput,
  ThreatArsenalAction,
} from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { type LogicContext } from '../AddComponentButton';
import {
  conditionGroupsToApi,
  type EventFormData,
} from '../events/event-types';
import EventCreationForm from '../events/EventCreationForm';
import { resolveConditionKeyTypes } from '../logic-flow-helpers';
import type { ActionDetailData, ActionMeta, EventMeta } from '../types';
import AddActionList from './AddActionList';
import ConfigureActionDetail from './ConfigureActionDetail';
import InjectTargetsProvider from './InjectTargetsProvider';

/**
 * Drawer navigation state, shared with the Logic container:
 * - `closed`       : drawer hidden
 * - `choose`       : step 1, pick a component type (action / event)
 * - `action`       : action path, step 2 (pick an arsenal action)
 * - `actionDetail` : action path, step 3 (configure) - also the entry point when editing a step
 * - `event`        : event path (define trigger conditions)
 */
export type DrawerView = 'closed' | 'choose' | 'action' | 'actionDetail' | 'event';

interface ComponentStepperDrawerProps {
  workflowId: string | undefined;
  context: LogicContext;
  scenarioId?: string;
  exerciseId?: string;
  validAssets: ScopeAssetOutput[];
  validTeams?: ScopeTeamOutput[];
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
  /** When set, the action list opens pre-filtered to actions producing this output type. */
  compatibleActionFilter?: string;
  onCompatibleActionFilterChange?: (field?: string) => void;
  /** When set, newly created actions are linked to this event (inline "+" on a trigger). */
  linkToEventId?: string;
  /** Output types of the action a new trigger is being added after (inline "+" on an action). */
  prefillEventFields?: string[];
}

interface ChoiceCardProps {
  icon: typeof TerminalOutlined;
  iconColor: string;
  circle?: boolean;
  title: string;
  description: string;
  onClick: () => void;
}

const ChoiceCard = ({ icon: Icon, iconColor, circle, title, description, onClick }: ChoiceCardProps) => {
  const theme = useTheme();
  return (
    <ButtonBase
      onClick={onClick}
      sx={{
        'display': 'grid',
        'gridTemplateColumns': '50px 1fr',
        'gridTemplateRows': 'auto auto',
        'alignItems': 'center',
        'columnGap': 2,
        'padding': 2,
        'width': '100%',
        'borderRadius': 1,
        'border': `1px solid ${theme.palette.divider}`,
        'textAlign': 'left',
        '&:hover': {
          backgroundColor: theme.palette.action.hover,
          borderColor: theme.palette.primary.main,
        },
      }}
    >
      <Box sx={{
        display: 'grid',
        placeItems: 'center',
        gridRow: '1 / 3',
        height: 50,
        width: 50,
        borderRadius: circle ? '50%' : 1,
        backgroundColor: `${iconColor}33`,
      }}
      >
        <Icon sx={{
          color: iconColor,
          fontSize: 24,
        }}
        />
      </Box>
      <Typography variant="subtitle1" fontWeight={600}>{title}</Typography>
      <Typography variant="body2" color="text.secondary">{description}</Typography>
    </ButtonBase>
  );
};

/**
 * Single drawer hosting an MUI Stepper for building a chaining component, replacing the previous
 * chain of drawers with back-arrow breadcrumbs. Handles both the action path (choose type -> pick
 * arsenal action -> configure) and the event path (choose type -> define conditions), plus direct
 * entry for editing an existing action or trigger.
 */
const ComponentStepperDrawer = ({
  workflowId,
  context,
  scenarioId,
  exerciseId,
  validAssets,
  validTeams = [],
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
  onCompatibleActionFilterChange,
  linkToEventId,
  prefillEventFields,
}: ComponentStepperDrawerProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [selectedAction, setSelectedAction] = useState<ThreatArsenalAction | null>(null);

  const isActionPath = drawerView === 'action' || drawerView === 'actionDetail';
  const isEventPath = drawerView === 'event';
  const isEditing = !!editingStep || !!editingEvent;

  // Derive the pre-populated data when editing a step.
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
      inject_asset_groups: meta.inject_asset_groups ?? [],
      inject_teams: meta.inject_teams ?? [],
      inject_all_teams: meta.inject_all_teams ?? false,
      inject_documents: meta.inject_documents ?? [],
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
          links[cond.condition_key] = {
            outputTypes: resolveConditionKeyTypes(cond as unknown as Record<string, unknown>),
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

  const handleClose = useCallback(() => {
    onDrawerViewChange('closed');
    setSelectedAction(null);
    onEditingStepChange(null);
    onEditingEventChange(null);
    onCompatibleActionFilterChange?.(undefined);
  }, [onDrawerViewChange, onEditingStepChange, onEditingEventChange, onCompatibleActionFilterChange]);

  // -- Actions --
  const handleAddActions = async (selectedActions: ThreatArsenalAction[]) => {
    if (!workflowId || selectedActions.length === 0) return;
    const promises = selectedActions.map((action) => {
      const title = action.action_labels?.en ?? action.action_labels?.fr ?? 'Untitled action';
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
        MESSAGING$.notifySuccess(t('{count} action(s) added successfully.', { count: String(successCount) }));
      }
    } finally {
      handleClose();
      onStepCreated();
    }
  };

  const handleSelectAction = (action: ThreatArsenalAction) => {
    setSelectedAction(action);
    onDrawerViewChange('actionDetail');
  };

  const handleSaveActionDetail = async (data: ActionDetailData) => {
    if (!workflowId) return;
    const stepConditions: ConditionCreateInput[] = Object.entries(data.inject_field_links).map(([fieldKey, link], i) => {
      const keyTypes = link.outputTypes && link.outputTypes.length > 0 ? link.outputTypes : ['text'];
      return {
        condition_temporary_id: String(i),
        condition_type: 'MAPPER' as const,
        condition_key_types: keyTypes as ConditionCreateInput['condition_key_types'],
        condition_key: fieldKey,
        condition_mapping_type: (link.localScope ? 'LOCAL' : 'GLOBAL') as ConditionCreateInput['condition_mapping_type'],
      };
    });

    const stepPayload = {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION' as const,
      step_condition_ids: editingStep?.meta.step_condition_ids ?? (linkToEventId ? [linkToEventId] : []),
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: data.inject_title,
        inject_injector_contract: data.inject_injector_contract,
        inject_assets: data.inject_assets,
        inject_content: data.inject_content,
        inject_tags: [],
        inject_all_teams: data.inject_all_teams,
        inject_teams: data.inject_teams,
        inject_asset_groups: data.inject_asset_groups,
        inject_documents: data.inject_documents,
        inject_depends_duration: 0,
        inject_depends_on: [],
      } as unknown as InjectInput,
    };

    const savePromise = editingStepId ? updateStep(editingStepId, stepPayload) : createStep(stepPayload);
    savePromise.then(() => {
      MESSAGING$.notifySuccess(editingStepId ? t('Action updated successfully.') : t('Action added successfully.'));
      handleClose();
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
      handleClose();
      onStepCreated();
    } catch {
      MESSAGING$.notifyError(editingEvent ? t('Failed to update event.') : t('Failed to create event.'));
    }
  };

  const activeAction = editingAction ?? selectedAction;

  // Stepper header: steps + active index depend on the current path.
  const steps = useMemo(() => {
    if (isEventPath) return [t('Choose type'), t('Define conditions')];
    if (isActionPath) return [t('Choose type'), t('Select action'), t('Configure')];
    return [t('Choose type')];
  }, [isActionPath, isEventPath, t]);

  const activeStep = useMemo(() => {
    if (drawerView === 'choose') return 0;
    if (drawerView === 'action') return 1;
    if (drawerView === 'actionDetail') return 2;
    if (drawerView === 'event') return 1;
    return 0;
  }, [drawerView]);

  // Back-navigation via the stepper header (create flow only; editing enters directly).
  const handleStepClick = (index: number) => {
    if (isEditing || index >= activeStep) return;
    if (index === 0) {
      setSelectedAction(null);
      onCompatibleActionFilterChange?.(undefined);
      onDrawerViewChange('choose');
    } else if (index === 1 && isActionPath) {
      setSelectedAction(null);
      onDrawerViewChange('action');
    }
  };

  const title = useMemo(() => {
    if (drawerView === 'event') return editingEvent ? t('Update trigger') : t('Add trigger');
    if (drawerView === 'actionDetail') return editingStep ? t('Configure action') : t('Configure action');
    if (drawerView === 'action') return t('Select an action');
    return t('Add component');
  }, [drawerView, editingEvent, editingStep, t]);

  return (
    <Drawer
      open={drawerView !== 'closed'}
      handleClose={handleClose}
      title={title}
    >
      <Box>
        <Stepper activeStep={activeStep} sx={{ mb: 3 }}>
          {steps.map((label, index) => (
            <Step key={label} completed={!isEditing && index < activeStep}>
              <StepLabel
                onClick={() => handleStepClick(index)}
                sx={{ cursor: !isEditing && index < activeStep ? 'pointer' : 'default' }}
              >
                {label}
              </StepLabel>
            </Step>
          ))}
        </Stepper>

        {drawerView === 'choose' && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            <ChoiceCard
              icon={TerminalOutlined}
              iconColor={theme.palette.primary.main}
              title={t('Action')}
              description={t('Execute an injector contract with configured parameters')}
              onClick={() => onDrawerViewChange('action')}
            />
            <ChoiceCard
              icon={BoltOutlined}
              iconColor={theme.palette.warning.main}
              circle
              title={t('Event')}
              description={t('Define conditions to trigger the next actions')}
              onClick={() => onDrawerViewChange('event')}
            />
          </Box>
        )}

        {drawerView === 'action' && (
          <AddActionList
            key={compatibleActionFilter ?? ''}
            onAddActions={handleAddActions}
            onSelectAction={handleSelectAction}
            compatibleActionFilter={compatibleActionFilter}
            onClearCompatibleFilter={() => onCompatibleActionFilterChange?.(undefined)}
          />
        )}

        {drawerView === 'actionDetail' && (
          <InjectTargetsProvider context={context} scenarioId={scenarioId} exerciseId={exerciseId}>
            <ConfigureActionDetail
              action={activeAction}
              validAssets={validAssets}
              validTeams={validTeams}
              initialData={editingInitialData}
              onClose={handleClose}
              onSave={handleSaveActionDetail}
            />
          </InjectTargetsProvider>
        )}

        {drawerView === 'event' && (
          <EventCreationForm
            onSubmit={handleSaveEvent}
            onCancel={handleClose}
            initialData={editingEvent?.meta.formData}
            submitLabel={editingEvent ? t('Update trigger') : t('Add trigger')}
            defaultName={!editingEvent ? `Event ${eventCount + 1}` : undefined}
            prefillFields={prefillEventFields}
          />
        )}
      </Box>
    </Drawer>
  );
};

export default ComponentStepperDrawer;
