import { getReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchExerciseCommunications = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/communications`;
  return getReferential(schema.arrayOfCommunications, uri)(dispatch);
};

export const fetchInjectCommunications = (exerciseId, injectId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/communications`;
  return getReferential(schema.arrayOfCommunications, uri)(dispatch);
};
