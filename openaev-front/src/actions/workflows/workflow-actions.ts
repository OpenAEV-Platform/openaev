import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { ConditionType, StepActionClass, StepFieldScope } from '../../utils/api-types-custom';

const SCENARIO_URI = '/api/scenarios';

// -- Workflow --

export const fetchWorkflow = (scenarioId: string) => {
  return simpleCall(`${SCENARIO_URI}/${scenarioId}/workflow`);
};

// -- Steps --

export interface StepCreateInput {
  step_action_class: StepActionClass;
  step_limit_execution: number;
  step_data?: string;
  step_output_parser?: string;
  step_field_scope?: StepFieldScope;
}

export const createStep = (scenarioId: string, data: StepCreateInput) => {
  return simplePostCall(`${SCENARIO_URI}/${scenarioId}/workflow/steps`, data);
};

export const updateStep = (scenarioId: string, stepId: string, data: StepCreateInput) => {
  return simplePutCall(`${SCENARIO_URI}/${scenarioId}/workflow/steps/${stepId}`, data);
};

export const deleteStep = (scenarioId: string, stepId: string) => {
  return simpleDelCall(`${SCENARIO_URI}/${scenarioId}/workflow/steps/${stepId}`);
};

// -- Conditions --

export interface ConditionCreateInput {
  condition_key?: string;
  condition_value?: string;
  condition_type: ConditionType;
  step_from_id?: string;
  condition_parent_id?: string;
}

export const createCondition = (scenarioId: string, stepId: string, data: ConditionCreateInput) => {
  return simplePostCall(`${SCENARIO_URI}/${scenarioId}/workflow/steps/${stepId}/conditions`, data);
};

export const updateCondition = (scenarioId: string, conditionId: string, data: ConditionCreateInput) => {
  return simplePutCall(`${SCENARIO_URI}/${scenarioId}/workflow/conditions/${conditionId}`, data);
};

export const deleteCondition = (scenarioId: string, conditionId: string) => {
  return simpleDelCall(`${SCENARIO_URI}/${scenarioId}/workflow/conditions/${conditionId}`);
};
