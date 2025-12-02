import { delReferential, getReferential, postReferential } from '../utils/Action';
import { arrayOfComchecks, arrayOfComcheckStatuses, comcheck, comcheckStatus } from './schemas';

export const fetchComchecks = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks`;
  return getReferential(arrayOfComchecks, uri)(dispatch);
};

export const fetchComcheck = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}`;
  return getReferential(comcheck, uri)(dispatch);
};

export const addComcheck = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks`;
  return postReferential(comcheck, uri, data)(dispatch);
};

export const deleteComcheck = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}`;
  return delReferential(uri, 'comchecks', comcheckId)(dispatch);
};

export const fetchComcheckStatuses = (exerciseId, comcheckId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/comchecks/${comcheckId}/statuses`;
  return getReferential(arrayOfComcheckStatuses, uri)(dispatch);
};

export const fetchComcheckStatus = statusId => (dispatch) => {
  const uri = `/api/comcheck/${statusId}`;
  return getReferential(comcheckStatus, uri)(dispatch);
};
