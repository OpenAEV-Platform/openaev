import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import {
  createCondition,
  createStep,
  deleteStep,
  fetchWorkflow,
  updateStep,
  type StepCreateInput,
  type ConditionCreateInput,
} from '../../../../../actions/workflows/workflow-actions';
import { useFormatter } from '../../../../../components/i18n';
import type { Scenario } from '../../../../../utils/api-types';
import type { Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';
import LogicActionCard from './LogicActionCard';
import LogicActionForm from './LogicActionForm';
import LogicAddComponentButton from './LogicAddComponentButton';
import { type ConditionRule } from './LogicConditionRuleRow';
import LogicEventCard from './LogicEventCard';
import LogicEventForm from './LogicEventForm';
import LogicHealthWarnings from './LogicHealthWarnings';
import { getLinkedSteps, getRootSteps } from './logicUtils';

const ScenarioLogic: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [loading, setLoading] = useState(true);

  // Drawer states
  const [actionFormOpen, setActionFormOpen] = useState(false);
  const [eventFormOpen, setEventFormOpen] = useState(false);
  const [editingStep, setEditingStep] = useState<WorkflowStep | null>(null);

  const loadWorkflow = useCallback(() => {
    setLoading(true);
    fetchWorkflow(scenarioId)
      .then((result: { data: Workflow }) => {
        setWorkflow(result.data);
      })
      .finally(() => setLoading(false));
  }, [scenarioId]);

  useEffect(() => {
    loadWorkflow();
  }, [loadWorkflow]);

  // -- Action handlers --

  const handleCreateAction = (data: {
    label: string;
    step_action_class: 'INJECT_EXECUTION';
    step_data: string;
    step_output_parser: string;
    step_limit_execution: number;
    step_field_scope: 'LOCAL' | 'GLOBAL';
  }) => {
    const input: StepCreateInput = {
      step_action_class: data.step_action_class,
      step_limit_execution: data.step_limit_execution,
      step_data: data.step_data,
      step_output_parser: data.step_output_parser,
      step_field_scope: data.step_field_scope,
    };

    if (editingStep) {
      updateStep(scenarioId, editingStep.step_id, input).then((result: { data: Workflow }) => {
        setWorkflow(result.data);
        setEditingStep(null);
      });
    } else {
      createStep(scenarioId, input).then((result: { data: Workflow }) => {
        setWorkflow(result.data);
      });
    }
  };

  const handleCreateEvent = (data: {
    label: string;
    targetStepId: string;
    conditions: ConditionRule[];
    logicOperator: 'AND' | 'OR';
  }) => {
    // Create a step for the event, then add conditions to it
    const stepInput: StepCreateInput = {
      step_action_class: 'INJECT_EXECUTION',
      step_limit_execution: 1,
      step_data: JSON.stringify({ inject_title: data.label }),
      step_field_scope: 'GLOBAL',
    };

    if (editingStep) {
      updateStep(scenarioId, editingStep.step_id, stepInput).then((result: { data: Workflow }) => {
        setWorkflow(result.data);
        setEditingStep(null);
        // Add conditions after step update
        addConditionsToStep(result.data, editingStep.step_id, data);
      });
    } else {
      createStep(scenarioId, stepInput).then((result: { data: Workflow }) => {
        setWorkflow(result.data);
        // Find the newly created step (last one)
        const newStep = result.data.workflow_steps[result.data.workflow_steps.length - 1];
        if (newStep) {
          addConditionsToStep(result.data, newStep.step_id, data);
        }
      });
    }
  };

  const addConditionsToStep = (
    currentWorkflow: Workflow,
    stepId: string,
    data: { targetStepId: string; conditions: ConditionRule[]; logicOperator: 'AND' | 'OR' },
  ) => {
    // Create a root AND/OR condition, then add child conditions
    if (data.conditions.length === 0) return;

    const rootCondition: ConditionCreateInput = {
      condition_type: data.logicOperator,
      step_from_id: data.targetStepId || undefined,
    };

    createCondition(scenarioId, stepId, rootCondition).then((result: { data: Workflow }) => {
      setWorkflow(result.data);
      // Find the root condition just created
      const updatedStep = result.data.workflow_steps.find(s => s.step_id === stepId);
      const rootCond = updatedStep?.step_conditions[updatedStep.step_conditions.length - 1];
      if (!rootCond) return;

      // Create child conditions sequentially
      let chain = Promise.resolve(result.data);
      for (const rule of data.conditions) {
        chain = chain.then((wf) => {
          const childCondition: ConditionCreateInput = {
            condition_key: rule.key,
            condition_value: rule.value,
            condition_type: rule.operator,
            step_from_id: data.targetStepId || undefined,
            condition_parent_id: rootCond.condition_id,
          };
          return createCondition(scenarioId, stepId, childCondition).then((res: { data: Workflow }) => {
            setWorkflow(res.data);
            return res.data;
          });
        });
      }
    });
  };

  const handleDeleteStep = (stepId: string) => {
    deleteStep(scenarioId, stepId).then((result: { data: Workflow }) => {
      setWorkflow(result.data);
    });
  };

  const handleEditAction = (step: WorkflowStep) => {
    setEditingStep(step);
    if (step.step_conditions.length > 0) {
      setEventFormOpen(true);
    } else {
      setActionFormOpen(true);
    }
  };

  if (loading || !workflow) {
    return <Typography>{t('Loading...')}</Typography>;
  }

  const rootSteps = getRootSteps(workflow);
  const linkedSteps = getLinkedSteps(workflow);

  return (
    <div>
      <LogicHealthWarnings workflow={workflow} />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: theme.spacing(3) }}>
        <Typography variant="h6">
          {t('Chaining Logic')}
        </Typography>
        <LogicAddComponentButton
          onAddAction={() => {
            setEditingStep(null);
            setActionFormOpen(true);
          }}
          onAddEvent={() => {
            setEditingStep(null);
            setEventFormOpen(true);
          }}
        />
      </div>

      {/* Actions section */}
      <Typography variant="subtitle1" fontWeight="bold" sx={{ mb: 1 }}>
        {t('Actions')} ({rootSteps.length})
      </Typography>
      {rootSteps.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('No actions defined yet.')}
        </Typography>
      )}
      {rootSteps.map(step => (
        <LogicActionCard
          key={step.step_id}
          step={step}
          onEdit={handleEditAction}
          onDelete={handleDeleteStep}
        />
      ))}

      {/* Events section */}
      <Typography variant="subtitle1" fontWeight="bold" sx={{ mb: 1, mt: 3 }}>
        {t('Events')} ({linkedSteps.length})
      </Typography>
      {linkedSteps.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('No events defined yet.')}
        </Typography>
      )}
      {linkedSteps.map(step => (
        <LogicEventCard
          key={step.step_id}
          step={step}
          allSteps={workflow.workflow_steps}
          onEdit={handleEditAction}
          onDelete={handleDeleteStep}
        />
      ))}

      {/* Drawers */}
      <LogicActionForm
        open={actionFormOpen}
        handleClose={() => {
          setActionFormOpen(false);
          setEditingStep(null);
        }}
        onSubmit={handleCreateAction}
        editingStep={editingStep}
        isLinkedToEvent={editingStep ? editingStep.step_conditions.length > 0 : false}
      />
      <LogicEventForm
        open={eventFormOpen}
        handleClose={() => {
          setEventFormOpen(false);
          setEditingStep(null);
        }}
        onSubmit={handleCreateEvent}
        editingStep={editingStep}
        allSteps={workflow.workflow_steps}
      />
    </div>
  );
};

export default ScenarioLogic;
