import { RocketLaunchOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import {
  createStep,
  deleteStep,
  updateStep,
  createConditionTree,
  deleteConditionTree,
  fetchWorkflow,
  type StepCreateInput,
  type EventCreateInput,
  type ConditionItemInput,
} from '../../../../../actions/chaining/workflow-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Scenario, InjectInput, AtomicTestingInput, InjectorContract } from '../../../../../utils/api-types';
import type { Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';
import { isFeatureEnabled } from '../../../../../utils/utils';
import LogicFlow from './flow/LogicFlow';
import LogicActionEditDrawer from './LogicActionEditDrawer';
import LogicActionForm from './LogicActionForm';
import LogicAddComponentDialog from './LogicAddComponentDialog';
import { type ConditionRule } from './LogicConditionRuleRow';
import LogicEventForm from './LogicEventForm';
import LogicHealthWarnings from './LogicHealthWarnings';

const ScenarioLogic: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const chainingSwimlanesEnabled = isFeatureEnabled('CHAINING_SWIMLANES');

  const { scenario, attackPatternsMap } = useHelper((helper: ScenariosHelper & AttackPatternHelper) => ({
    scenario: helper.getScenario(scenarioId),
    attackPatternsMap: helper.getAttackPatternsMap(),
  }));

  const workflowId = scenario?.scenario_workflow_id;

  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [loading, setLoading] = useState(true);

  const [actionFormOpen, setActionFormOpen] = useState(false);
  const [eventFormOpen, setEventFormOpen] = useState(false);
  const [editingStep, setEditingStep] = useState<WorkflowStep | null>(null);
  const [parentActionStepId, setParentActionStepId] = useState<string | null>(null);
  const linkToEventStepIdRef = useRef<string | null>(null);

  // Action edit drawer state
  const [actionEditOpen, setActionEditOpen] = useState(false);
  const [actionEditStep, setActionEditStep] = useState<WorkflowStep | null>(null);

  const loadWorkflow = useCallback(() => {
    if (!workflowId) return;
    setLoading(true);
    fetchWorkflow(workflowId)
      .then((result: Workflow) => setWorkflow(result))
      .finally(() => setLoading(false));
  }, [workflowId]);

  useEffect(() => { loadWorkflow(); }, [loadWorkflow]);

  if (!chainingSwimlanesEnabled) {
    return null;
  }

  // -- Action handlers --

  const handleCreateAction = async (data: InjectInput | AtomicTestingInput) => {
    if (!workflowId) return;
    const injectData = data as InjectInput;
    // inject_injector_contract may be a string ID or an object with injector_contract_id
    const rawContract = injectData.inject_injector_contract as unknown;
    const contractId = typeof rawContract === 'object' && rawContract !== null
      ? (rawContract as { injector_contract_id: string }).injector_contract_id
      : rawContract as string | undefined;

    // Build the step_data_step from InjectInput
    const stepDataStep: InjectInput = {
      inject_title: injectData.inject_title,
      inject_injector_contract: contractId,
      inject_content: injectData.inject_content,
    };

    // If linking to an event, include condition_ids for DEPEND_ON
    const conditionIds: string[] = [];
    const eventStepId = linkToEventStepIdRef.current;
    if (eventStepId) {
      // The event step ID corresponds to an event root condition
      conditionIds.push(eventStepId);
    }

    const input: StepCreateInput = {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION',
      step_data_step: stepDataStep,
      step_condition_ids: conditionIds.length > 0 ? conditionIds : undefined,
    };

    const newStepOutput = await createStep(input);

    // Enrich with contract data if available
    if (contractId) {
      try {
        const contractResult = await directFetchInjectorContract(contractId);
        const contract = contractResult.data as InjectorContract;
        const enrichedData: InjectInput = {
          ...stepDataStep,
          inject_content: {
            ...stepDataStep.inject_content,
            injector_type: contract.injector_contract_injector_type ?? undefined,
          },
        };
        await updateStep(newStepOutput.step_id, {
          step_workflow_id: workflowId,
          step_action: 'INJECT_EXECUTION',
          step_data_step: enrichedData,
        });
      } catch { /* contract enrichment is best-effort */ }
    }

    setActionFormOpen(false);
    linkToEventStepIdRef.current = null;
    loadWorkflow();
  };

  const handleEditAction = (step: WorkflowStep) => {
    setActionEditStep(step);
    setActionEditOpen(true);
  };

  const handleSaveActionEdit = async (step: WorkflowStep, title: string, fieldScopes: Record<string, string>, injectContent?: Record<string, unknown>, modifiedStepData?: string) => {
    if (!workflowId) return;
    try {
      const baseData = modifiedStepData ?? step.step_data ?? '{}';
      const currentData = JSON.parse(baseData) as Record<string, unknown>;
      const updatedData = {
        ...currentData,
        inject_title: title,
        field_scopes: fieldScopes,
        ...(injectContent ? { inject_content: { ...currentData.inject_content as Record<string, unknown>, ...injectContent } } : {}),
      } as InjectInput;
      await updateStep(step.step_id, {
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION',
        step_data_step: updatedData,
      });
    } catch { /* ignore */ }
    setActionEditOpen(false);
    setActionEditStep(null);
    loadWorkflow();
  };

  const handleCreateOrUpdateEvent = async (data: {
    label: string;
    description: string;
    conditions: ConditionRule[];
    logicOperator: 'AND' | 'OR';
  }) => {
    if (!workflowId) return;
    const savedParentActionStepId = parentActionStepId;

    // Build condition tree for the Event API
    const rootCondition: ConditionItemInput = {
      condition_temporary_id: 'root',
      condition_type: data.logicOperator,
    };
    const childConditions: ConditionItemInput[] = data.conditions.map((rule, idx) => ({
      condition_temporary_id: `child-${idx}`,
      condition_temporary_id_condition_parent: 'root',
      condition_type: rule.operator,
      condition_key: rule.key,
      condition_value: rule.value || undefined,
    }));

    const eventConditions = [rootCondition, ...childConditions];

    // Link to parent action step if applicable
    const stepIds: string[] = [];
    if (savedParentActionStepId) {
      stepIds.push(savedParentActionStepId);
    }

    if (editingStep) {
      // Update existing event
      const eventInput: EventCreateInput = {
        event_name: data.label,
        event_description: data.description,
        event_workflow_id: workflowId,
        event_conditions: eventConditions,
        event_step_ids: stepIds.length > 0 ? stepIds : undefined,
      };
      // The editingStep.step_id corresponds to the event root condition ID
      await updateConditionTree(editingStep.step_id, eventInput);
      setEditingStep(null);
    } else {
      // Create new event
      const eventInput: EventCreateInput = {
        event_name: data.label,
        event_description: data.description,
        event_workflow_id: workflowId,
        event_conditions: eventConditions,
        event_step_ids: stepIds.length > 0 ? stepIds : undefined,
      };
      await createConditionTree(eventInput);
    }

    setParentActionStepId(null);
    loadWorkflow();
  };

  const handleDeleteStep = (stepId: string) => {
    deleteStep(stepId).then(() => loadWorkflow());
  };

  const handleEditEvent = (step: WorkflowStep) => {
    setEditingStep(step);
    setEventFormOpen(true);
  };

  const handleAddActionForEvent = (eventStepId: string) => {
    linkToEventStepIdRef.current = eventStepId;
    setEditingStep(null);
    setActionFormOpen(true);
  };

  const handleAddActionFromContract = async (contractId: string, contractLabel: string) => {
    if (!workflowId) return;
    const stepDataStep: InjectInput = {
      inject_title: contractLabel,
      inject_injector_contract: contractId,
    };
    const input: StepCreateInput = {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION',
      step_data_step: stepDataStep,
    };

    const newStepOutput = await createStep(input);

    // Enrich with contract metadata
    try {
      const contractResult = await directFetchInjectorContract(contractId);
      const contract = contractResult.data as InjectorContract;
      const enrichedData: InjectInput = {
        ...stepDataStep,
        inject_content: {
          injector_type: contract.injector_contract_injector_type ?? undefined,
        },
      };
      await updateStep(newStepOutput.step_id, {
        step_workflow_id: workflowId,
        step_action: 'INJECT_EXECUTION',
        step_data_step: enrichedData,
      });
    } catch { /* contract enrichment is best-effort */ }

    loadWorkflow();
  };

  if (loading || !workflow) {
    return <Typography>{t('Loading...')}</Typography>;
  }

  const steps = workflow.workflow_steps;

  return (
    <div>
      <LogicHealthWarnings workflow={workflow} onAddActionFromContract={handleAddActionFromContract} />

      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginBottom: theme.spacing(2) }}>
        <LogicAddComponentDialog
          onAddAction={() => {
            linkToEventStepIdRef.current = null;
            setEditingStep(null);
            setActionFormOpen(true);
          }}
          onAddEvent={() => {
            setParentActionStepId(null);
            setEditingStep(null);
            setEventFormOpen(true);
          }}
        />
      </div>

      {steps.length === 0 ? (
        <div style={{ textAlign: 'center', padding: theme.spacing(8, 0) }}>
          <RocketLaunchOutlined sx={{ fontSize: 64, color: theme.palette.text.disabled, mb: 2 }} />
          <Typography variant="h5" gutterBottom>
            {t("Let's Start!")}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('Click "Add component" to start building your attack chain.')}
          </Typography>
        </div>
      ) : (
        <div style={{ width: '100%', height: 'calc(100vh - 340px)' }}>
          <LogicFlow
            steps={steps}
            onDeleteStep={handleDeleteStep}
            onEditAction={handleEditAction}
            onEditEvent={handleEditEvent}
            onAddActionForEvent={handleAddActionForEvent}
          />
        </div>
      )}

      {/* Drawers */}
      <LogicActionForm
        open={actionFormOpen}
        handleClose={() => {
          setActionFormOpen(false);
          setEditingStep(null);
          linkToEventStepIdRef.current = null;
        }}
        onContractSelected={handleCreateAction}
      />
      <LogicEventForm
        open={eventFormOpen}
        handleClose={() => {
          setEventFormOpen(false);
          setEditingStep(null);
          setParentActionStepId(null);
        }}
        onSubmit={handleCreateOrUpdateEvent}
        editingStep={editingStep}
        allSteps={steps}
        parentActionStepId={parentActionStepId}
      />

      {/* Action edit drawer */}
      <LogicActionEditDrawer
        open={actionEditOpen}
        handleClose={() => { setActionEditOpen(false); setActionEditStep(null); }}
        step={actionEditStep}
        allSteps={steps}
        onSave={handleSaveActionEdit}
        attackPatternsMap={attackPatternsMap}
      />
    </div>
  );
};

export default ScenarioLogic;
