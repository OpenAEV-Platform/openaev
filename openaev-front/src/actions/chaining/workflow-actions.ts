import type { Dispatch } from 'redux';

import { getReferential, putReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { WorkflowConfigurationInput, InjectInput } from '../../utils/api-types';
import type { Workflow, WorkflowStep, WorkflowCondition } from '../../utils/api-types-custom';
import workflowConfigurationSchema from './workflow-schema';

const WORKFLOW_URI = '/api/workflows';
const CHAINING_URI = '/api/chaining';

// -- Backend DTO types (matching Java DTOs) --

/** Matches backend StepInput.java */
export interface StepCreateInput {
  step_workflow_id: string;
  step_action: 'INJECT_EXECUTION';
  step_data_step?: InjectInput;
  step_conditions?: ConditionItemInput[];
  step_condition_ids?: string[];
}

/** Matches backend ConditionCreateInput.java (nested in StepInput or EventInput) */
export interface ConditionItemInput {
  condition_temporary_id?: string;
  condition_temporary_id_condition_parent?: string;
  condition_type: string;
  condition_value?: string;
  condition_key_type?: string;
  condition_key_subtype?: string;
  condition_key?: string;
  condition_mapping_type?: string;
  condition_step_from?: string;
}

/** Matches backend EventInput.java */
export interface EventCreateInput {
  event_name: string;
  event_description?: string;
  event_workflow_id: string;
  event_conditions: ConditionItemInput[];
  event_step_ids?: string[];
}

/** Matches backend StepOutput.java */
interface StepOutputDTO {
  step_id: string;
  step_status: string;
  step_condition_key_types: string[];
  step_data: Record<string, unknown> | null;
  step_created_at: string;
  step_updated_at: string;
}

/** Matches backend EventOutput.java */
interface EventOutputDTO {
  event_id: string;
  event_name: string;
  event_description?: string;
  event_workflow_id: string;
  event_conditions: ConditionOutputDTO[];
  event_created_at: string;
  event_updated_at: string;
}

/** Matches backend ConditionOutput.java */
interface ConditionOutputDTO {
  condition_id: string;
  condition_type: string;
  condition_value?: string;
  condition_parent_id?: string;
  condition_key_type?: string;
  condition_key_subtype?: string;
  condition_mapping_type?: string;
}

// -- Workflow configuration (Redux) --

export const fetchWorkflowConfiguration = (workflowId: string) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return getReferential(workflowConfigurationSchema(workflowId), uri)(dispatch);
};

export const updateWorkflowConfiguration = (workflowId: string, data: WorkflowConfigurationInput) => (dispatch: Dispatch) => {
  const uri = `${WORKFLOW_URI}/${workflowId}/configuration`;
  return putReferential(workflowConfigurationSchema(workflowId), uri, data)(dispatch);
};

// -- Steps (direct API, return unwrapped data) --

export const fetchSteps = async (workflowId: string): Promise<StepOutputDTO[]> => {
  const response = await simpleCall(`${CHAINING_URI}/steps?workflow_id=${workflowId}`);
  return response.data;
};

export const createStep = async (data: StepCreateInput): Promise<StepOutputDTO> => {
  const response = await simplePostCall(`${CHAINING_URI}/steps`, data);
  return response.data;
};

export const updateStep = async (stepId: string, data: StepCreateInput): Promise<StepOutputDTO> => {
  const response = await simplePutCall(`${CHAINING_URI}/steps/${stepId}`, data);
  return response.data;
};

export const deleteStep = async (stepId: string): Promise<void> => {
  await simpleDelCall(`${CHAINING_URI}/steps/${stepId}`);
};

// -- Conditions / Events (direct API, return unwrapped data) --

export const fetchConditions = async (workflowId: string): Promise<EventOutputDTO[]> => {
  const response = await simpleCall(`${CHAINING_URI}/conditions?workflow_id=${workflowId}`);
  return response.data;
};

export const createConditionTree = async (data: EventCreateInput): Promise<EventOutputDTO> => {
  const response = await simplePostCall(`${CHAINING_URI}/conditions`, data);
  return response.data;
};

export const updateConditionTree = async (conditionId: string, data: EventCreateInput): Promise<EventOutputDTO> => {
  const response = await simplePutCall(`${CHAINING_URI}/conditions/${conditionId}`, data);
  return response.data;
};

export const deleteConditionTree = async (conditionId: string): Promise<void> => {
  await simpleDelCall(`${CHAINING_URI}/conditions/${conditionId}`);
};

// -- Composite workflow fetch (3 parallel GETs → unified Workflow view model) --

/** Convert events into WorkflowCondition items for attachment to steps */
const mapConditionsForSteps = (events: EventOutputDTO[]): WorkflowCondition[] => {
  const conditions: WorkflowCondition[] = [];
  for (const event of events) {
    if (event.event_conditions) {
      for (const c of event.event_conditions) {
        conditions.push({
          condition_id: c.condition_id,
          condition_type: c.condition_type as WorkflowCondition['condition_type'],
          condition_value: c.condition_value,
          condition_parent_id: c.condition_parent_id,
        });
      }
    }
  }
  return conditions;
};

/** Convert StepOutputDTOs into WorkflowStep view models (action nodes) */
const mapActionSteps = (steps: StepOutputDTO[], allConditions: WorkflowCondition[]): WorkflowStep[] => {
  return steps.map((s) => ({
    step_id: s.step_id,
    step_action_class: 'INJECT_EXECUTION' as const,
    step_data: s.step_data ? JSON.stringify(s.step_data) : undefined,
    step_limit_execution: 1,
    step_status: s.step_status as WorkflowStep['step_status'],
    step_created_at: s.step_created_at,
    step_updated_at: s.step_updated_at,
    step_conditions: allConditions.filter((c) => c.step_from_id === s.step_id),
  }));
};

/** Convert EventOutputDTOs into WorkflowStep view models (event nodes) */
const mapEventSteps = (events: EventOutputDTO[]): WorkflowStep[] => {
  return events.map((e) => ({
    step_id: e.event_id,
    step_action_class: 'EVENT' as const,
    step_data: JSON.stringify({
      event_name: e.event_name,
      event_description: e.event_description,
    }),
    step_limit_execution: 0,
    step_status: 'TEMPLATE' as const,
    step_created_at: e.event_created_at,
    step_updated_at: e.event_updated_at,
    step_conditions: (e.event_conditions ?? []).map((c) => ({
      condition_id: c.condition_id,
      condition_type: c.condition_type as WorkflowCondition['condition_type'],
      condition_value: c.condition_value,
      condition_parent_id: c.condition_parent_id,
    })),
  }));
};

export const fetchWorkflow = async (workflowId: string): Promise<Workflow> => {
  const configResponse = await simpleCall(`${WORKFLOW_URI}/${workflowId}/workflow-configuration`);
  const [stepsResult, conditionsResult] = await Promise.all([
    fetchSteps(workflowId),
    fetchConditions(workflowId),
  ]);

  const configData = configResponse.data;
  const allConditions = mapConditionsForSteps(conditionsResult);

  // Merge action steps and event steps into a single array for the UI
  const actionSteps = mapActionSteps(stepsResult, allConditions);
  const eventSteps = mapEventSteps(conditionsResult);

  return {
    workflow_id: workflowId,
    workflow_status: 'TEMPLATE',
    workflow_version: 1,
    workflow_is_edited: false,
    workflow_scope: configData.workflow_scope_rules ? JSON.stringify(configData.workflow_scope_rules) : undefined,
    workflow_timeout: configData.workflow_configuration_timeout_seconds,
    workflow_steps: [...actionSteps, ...eventSteps],
  };
};
