import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { ChainingOutput, EventInput, EventOutput, StepInput, StepOutput } from '../../utils/api-types';

const CHAINING_URI = '/api/chaining';
const STEPS_URI = `${CHAINING_URI}/steps`;
const CONDITIONS_URI = `${CHAINING_URI}/conditions`;
const INJECTOR_CONTRACTS_URI = '/api/injector_contracts';

// -- Injector contracts --
export const searchInjectorContracts = (searchPaginationInput: object) => simplePostCall(`${INJECTOR_CONTRACTS_URI}/search`, searchPaginationInput);

// -- Full chaining --
export const fetchChaining = (workflowId: string): Promise<{ data: ChainingOutput }> => simpleCall(`${CHAINING_URI}?workflow_id=${workflowId}`);

// -- Steps (Actions in UI) --
export const fetchSteps = (workflowId: string): Promise<{ data: StepOutput[] }> => simpleCall(`${STEPS_URI}?workflow_id=${workflowId}`);

export const createStep = (data: StepInput): Promise<{ data: StepOutput }> => simplePostCall(STEPS_URI, data);

export const updateStep = (stepId: string, data: StepInput): Promise<{ data: StepOutput }> => simplePutCall(`${STEPS_URI}/${stepId}`, data);

export const deleteStep = (stepId: string) => simpleDelCall(`${STEPS_URI}/${stepId}`);

// -- Conditions / Events (Events in UI) --
export const fetchConditions = (workflowId: string): Promise<{ data: EventOutput[] }> => simpleCall(`${CONDITIONS_URI}?workflow_id=${workflowId}`);

export const createCondition = (data: EventInput): Promise<{ data: EventOutput }> => simplePostCall(CONDITIONS_URI, data);

export const updateCondition = (conditionId: string, data: EventInput): Promise<{ data: EventOutput }> => simplePutCall(`${CONDITIONS_URI}/${conditionId}`, data);

export const deleteCondition = (conditionId: string) => simpleDelCall(`${CONDITIONS_URI}/${conditionId}`);
