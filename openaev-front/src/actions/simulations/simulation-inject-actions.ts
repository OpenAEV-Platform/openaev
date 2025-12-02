import type { Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { getReferential, postReferential, simplePostCall } from '../../utils/Action';
import { type Exercise, type Inject, type InjectInput, type InjectOutput, type SearchPaginationInput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { arrayOfInjects } from '../schemas';

export const createInjectsForSimulation = (simulationId: Exercise['exercise_id'], inputs: InjectInput[]) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/injects/bulk`;
  return postReferential<Inject[]>(arrayOfInjects, uri, inputs)(dispatch);
};

export const fetchExerciseInjectsSimple = (exerciseId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/simple`;
  return getReferential<InjectOutput[]>(arrayOfInjects, uri)(dispatch);
};

export const searchExerciseInjectsSimple = (exerciseId: Exercise['exercise_id'], input: SearchPaginationInput) => {
  const uri = `/api/exercises/${exerciseId}/injects/simple`;
  return simplePostCall<Page<InjectOutput>>(uri, input);
};

export const importInjectsForSimulation = (simulationId: Exercise['exercise_id'], file: File) => {
  const uri = `/api/exercises/${simulationId}/injects/import`;
  const formData = new FormData();
  formData.append('file', file);
  return simplePostCall<void>(uri, formData).catch((error) => {
    MESSAGING$.notifyError('Could not import injects');
    throw error;
  });
};
