import { getReferential } from '../utils/Action';
import { arrayOfCommunications } from './schemas';

export const fetchExerciseCommunications = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/communications`;
  return getReferential(arrayOfCommunications, uri)(dispatch);
};

export const fetchInjectCommunications = (exerciseId, injectId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/injects/${injectId}/communications`;
  return getReferential(arrayOfCommunications, uri)(dispatch);
};
