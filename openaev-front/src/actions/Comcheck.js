import { delReferential, getReferential, postReferential, simpleCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchComchecks = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks`;
  return getReferential(schema.arrayOfComchecks, uri)(dispatch);
};

export const fetchComcheck = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}`;
  return getReferential(schema.comcheck, uri)(dispatch);
};

export const addComcheck = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks`;
  return postReferential(schema.comcheck, uri, data)(dispatch);
};

export const deleteComcheck = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}`;
  return delReferential(uri, 'comchecks', comcheckId)(dispatch);
};

export const fetchComcheckStatuses = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}/statuses`;
  return getReferential(schema.arrayOfComcheckStatuses, uri)(dispatch);
};

// The public page shows a refusal (409) itself: no error toast, and no store: the page keeps the answer.
export const fetchComcheckStatus = statusId => simpleCall(`/api/comcheck/${statusId}`, undefined, false).then(response => response.data);
