import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import { arrayOfLogs, log } from './schemas';

export const fetchLogs = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs`;
  return getReferential(arrayOfLogs, uri)(dispatch);
};

export const fetchLog = (exerciseId, logId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return getReferential(log, uri)(dispatch);
};

export const updateLog = (exerciseId, logId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return putReferential(log, uri, data)(dispatch);
};

export const addLog = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs`;
  return postReferential(log, uri, data)(dispatch);
};

export const deleteLog = (exerciseId, logId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return delReferential(uri, 'logs', logId)(dispatch);
};
