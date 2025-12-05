import type { Dispatch } from 'redux';

import { getReferential, postReferential, simplePostCall } from '../../utils/Action';
import type { Exercise, InjectInput, SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';

export const createInjectsForSimulation = (simulationId: Exercise['exercise_id'], inputs: InjectInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/injects/bulk`;
  return postReferential(schema.arrayOfInjects, uri, inputs)(dispatch);
};

export const fetchExerciseInjectsSimple = (exerciseId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/simple`;
  return getReferential(schema.arrayOfInjects, uri)(dispatch);
};

export const searchExerciseInjectsSimple = (exerciseId: Exercise['exercise_id'], input: SearchPaginationInput) => {
  const uri = `/api/exercises/${exerciseId}/injects/simple`;
  return simplePostCall(uri, input);
};

export const importInjectsForSimulation = (simulationId: Exercise['exercise_id'], file: File) => {
  const uri = `/api/exercises/${simulationId}/injects/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import injects');
    throw error;
  });
};
