import { RocketLaunchOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import {
  createCondition,
  createStep,
  deleteCondition,
  deleteStep,
  updateStep,
  fetchWorkflow,
  type StepCreateInput,
  type ConditionCreateInput,
} from '../../../../../actions/workflows/workflow-actions';
import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Scenario, InjectInput, AtomicTestingInput, InjectorContract } from '../../../../../utils/api-types';
import type { Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';
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

  const { attackPatternsMap } = useHelper((helper: AttackPatternHelper) => ({
    attackPatternsMap: helper.getAttackPatternsMap(),
  }));

  const loadWorkflow = useCallback(() => {
    setLoading(true);
    fetchWorkflow(scenarioId)
      .then((result: { data: Workflow }) => setWorkflow(result.data))
      .finally(() => setLoading(false));
  }, [scenarioId]);

  useEffect(() => { loadWorkflow(); }, [loadWorkflow]);

  // -- Action handlers --

  // Find newly created step by comparing IDs before and after
  const findNewStep = (previousSteps: WorkflowStep[], updatedWorkflow: Workflow) => {
    const previousIds = new Set(previousSteps.map(s => s.step_id));
    return updatedWorkflow.workflow_steps.find(s => !previousIds.has(s.step_id));
  };

  const handleCreateAction = async (data: InjectInput | AtomicTestingInput) => {
    const injectData = data as InjectInput;
    const contractId = injectData.inject_injector_contract;
    const baseStepData = {
      inject_title: injectData.inject_title,
      inject_injector_contract: contractId,
      inject_content: injectData.inject_content,
    };
    const input: StepCreateInput = {
      step_action_class: 'INJECT_EXECUTION',
      step_limit_execution: 1,
      step_data: JSON.stringify(baseStepData),
      step_field_scope: 'GLOBAL',
    };

    const previousSteps = workflow?.workflow_steps ?? [];
    const [result, contractResult] = await Promise.all([
      createStep(scenarioId, input) as Promise<{ data: Workflow }>,
      contractId
        ? directFetchInjectorContract(contractId).catch(() => null)
        : Promise.resolve(null),
    ]);

    const newStep = findNewStep(previousSteps, result.data);

    if (newStep && contractResult) {
      const contract = contractResult.data as InjectorContract;
      const enrichedStepData = {
        ...baseStepData,
        injector_type: contract.injector_contract_injector_type ?? null,
        injector_contract_attack_patterns: contract.injector_contract_attack_patterns ?? [],
      };
      const payloadOutputParsers = contract.injector_contract_payload?.payload_output_parsers ?? [];
      let outputParserJson: string | undefined = newStep.step_output_parser;
      if (payloadOutputParsers.length > 0) {
        outputParserJson = JSON.stringify(payloadOutputParsers);
      } else if (contract.injector_contract_content) {
        try {
          const content = JSON.parse(contract.injector_contract_content);
          if (Array.isArray(content.outputs) && content.outputs.length > 0) {
            outputParserJson = JSON.stringify(content.outputs);
          }
        } catch { /* ignore */ }
      }
      const updateInput: StepCreateInput = {
        step_action_class: newStep.step_action_class,
        step_limit_execution: newStep.step_limit_execution,
        step_data: JSON.stringify(enrichedStepData),
        step_output_parser: outputParserJson,
        step_field_scope: newStep.step_field_scope,
      };
      await updateStep(scenarioId, newStep.step_id, updateInput);
    }

    const eventStepId = linkToEventStepIdRef.current;
    if (eventStepId && newStep) {
      const dependCondition: ConditionCreateInput = {
        condition_type: 'DEPEND_ON',
        step_from_id: eventStepId,
      };
      await createCondition(scenarioId, newStep.step_id, dependCondition);
    }

    setActionFormOpen(false);
    linkToEventStepIdRef.current = null;
    loadWorkflow();
  };

  const handleEditAction = (step: WorkflowStep) => {
    setActionEditStep(step);
    setActionEditOpen(true);
  };

  const handleSaveActionEdit = async (step: WorkflowStep, title: string, fieldScopes: Record<string, string>) => {
    try {
      const currentData = JSON.parse(step.step_data ?? '{}');
      const updatedData = { ...currentData, inject_title: title, field_scopes: fieldScopes };
      const input: StepCreateInput = {
        step_action_class: step.step_action_class,
        step_limit_execution: step.step_limit_execution,
        step_data: JSON.stringify(updatedData),
        step_output_parser: step.step_output_parser,
        step_field_scope: step.step_field_scope,
      };
      await updateStep(scenarioId, step.step_id, input);
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
    const savedParentActionStepId = parentActionStepId;

    if (editingStep) {
      const updateInput: StepCreateInput = {
        step_action_class: editingStep.step_action_class,
        step_limit_execution: editingStep.step_limit_execution,
        step_data: JSON.stringify({
          event_name: data.label,
          event_description: data.description,
        }),
        step_output_parser: editingStep.step_output_parser,
        step_field_scope: editingStep.step_field_scope,
      };
      await updateStep(scenarioId, editingStep.step_id, updateInput);

      const oldFieldConditions = editingStep.step_conditions.filter(
        c => c.condition_type !== 'DEPEND_ON',
      );
      for (const cond of oldFieldConditions) {
        await deleteCondition(scenarioId, cond.condition_id);
      }

      if (data.conditions.length > 0) {
        const latestWorkflow = await fetchWorkflow(scenarioId) as { data: Workflow };
        await addConditionsToStep(editingStep.step_id, data, latestWorkflow.data);
      }

      setEditingStep(null);
      loadWorkflow();
      return;
    }

    const stepInput: StepCreateInput = {
      step_action_class: 'INJECT_EXECUTION',
      step_limit_execution: 1,
      step_data: JSON.stringify({
        event_name: data.label,
        event_description: data.description,
      }),
      step_field_scope: 'GLOBAL',
    };

    const previousStepsForEvent = workflow?.workflow_steps ?? [];
    const result = await createStep(scenarioId, stepInput) as { data: Workflow };
    const newStep = findNewStep(previousStepsForEvent, result.data);
    if (!newStep) { loadWorkflow(); return; }

    if (savedParentActionStepId) {
      const dependCondition: ConditionCreateInput = {
        condition_type: 'DEPEND_ON',
        step_from_id: savedParentActionStepId,
      };
      await createCondition(scenarioId, newStep.step_id, dependCondition);
    }

    if (data.conditions.length > 0) {
      await addConditionsToStep(newStep.step_id, data, result.data);
    }

    setParentActionStepId(null);
    loadWorkflow();
  };

  const addConditionsToStep = (
    stepId: string,
    data: { conditions: ConditionRule[]; logicOperator: 'AND' | 'OR' },
    _currentWorkflow: Workflow,
  ): Promise<Workflow> => {
    if (data.conditions.length === 0) return Promise.resolve(_currentWorkflow);

    const rootCondition: ConditionCreateInput = {
      condition_type: data.logicOperator,
    };

    return createCondition(scenarioId, stepId, rootCondition).then((result: { data: Workflow }) => {
      setWorkflow(result.data);
      const updatedStep = result.data.workflow_steps.find(s => s.step_id === stepId);
      const rootCond = updatedStep?.step_conditions[updatedStep.step_conditions.length - 1];
      if (!rootCond) return result.data;

      let chain = Promise.resolve(result.data);
      for (const rule of data.conditions) {
        chain = chain.then(() => {
          const childCondition: ConditionCreateInput = {
            condition_key: rule.key,
            condition_field: rule.field || undefined,
            condition_value: rule.value || undefined,
            condition_type: rule.operator,
            condition_parent_id: rootCond.condition_id,
          };
          return createCondition(scenarioId, stepId, childCondition).then((res: { data: Workflow }) => {
            setWorkflow(res.data);
            return res.data;
          });
        });
      }
      return chain;
    });
  };

  const handleDeleteStep = (stepId: string) => {
    deleteStep(scenarioId, stepId).then((result: { data: Workflow }) => {
      setWorkflow(result.data);
    });
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
    const baseStepData = {
      inject_title: contractLabel,
      inject_injector_contract: contractId,
    };
    const input: StepCreateInput = {
      step_action_class: 'INJECT_EXECUTION',
      step_limit_execution: 1,
      step_data: JSON.stringify(baseStepData),
      step_field_scope: 'GLOBAL',
    };

    const previousSteps = workflow?.workflow_steps ?? [];
    const [result, contractResult] = await Promise.all([
      createStep(scenarioId, input) as Promise<{ data: Workflow }>,
      directFetchInjectorContract(contractId).catch(() => null),
    ]);

    const newStep = findNewStep(previousSteps, result.data);

    if (newStep && contractResult) {
      const contract = contractResult.data as InjectorContract;
      const enrichedStepData = {
        ...baseStepData,
        injector_type: contract.injector_contract_injector_type ?? null,
        injector_contract_attack_patterns: contract.injector_contract_attack_patterns ?? [],
      };
      const payloadOutputParsers = contract.injector_contract_payload?.payload_output_parsers ?? [];
      let outputParserJson: string | undefined = newStep.step_output_parser;
      if (payloadOutputParsers.length > 0) {
        outputParserJson = JSON.stringify(payloadOutputParsers);
      } else if (contract.injector_contract_content) {
        try {
          const content = JSON.parse(contract.injector_contract_content);
          if (Array.isArray(content.outputs) && content.outputs.length > 0) {
            outputParserJson = JSON.stringify(content.outputs);
          }
        } catch { /* ignore */ }
      }
      const updateInput: StepCreateInput = {
        step_action_class: newStep.step_action_class,
        step_limit_execution: newStep.step_limit_execution,
        step_data: JSON.stringify(enrichedStepData),
        step_output_parser: outputParserJson,
        step_field_scope: newStep.step_field_scope,
      };
      await updateStep(scenarioId, newStep.step_id, updateInput);
    }

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
        onSave={handleSaveActionEdit}
        attackPatternsMap={attackPatternsMap}
      />
    </div>
  );
};

export default ScenarioLogic;
