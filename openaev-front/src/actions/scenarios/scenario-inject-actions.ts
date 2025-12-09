import type { Dispatch } from 'redux';

import { getReferential, postReferential, simplePostCall } from '../../utils/Action';
import type { InjectInput, Scenario, SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';

export const createInjectsForScenario = (scenarioId: Scenario['scenario_id'], inputs: InjectInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/bulk`;
  return postReferential(schema.arrayOfInjects, uri, inputs)(dispatch);
};

export const fetchScenarioInjectsSimple = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/injects/simple`;
  return getReferential(schema.arrayOfInjects, uri)(dispatch);
};

export const searchScenarioInjectsSimple = (scenarioId: Scenario['scenario_id'], input: SearchPaginationInput) => {
  const uri = `/api/scenarios/${scenarioId}/injects/simple`;
  return simplePostCall(uri, input);
};

export const importInjectsForScenario = (scenarioId: Scenario['scenario_id'], file: File) => {
  const uri = `/api/scenarios/${scenarioId}/injects/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import injects');
    throw error;
  });
};
