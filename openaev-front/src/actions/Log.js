import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchLogs = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs`;
  return getReferential(schema.arrayOfLogs, uri)(dispatch);
};

export const fetchLog = (exerciseId, logId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return getReferential(schema.log, uri)(dispatch);
};

export const updateLog = (exerciseId, logId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return putReferential(schema.log, uri, data)(dispatch);
};

export const addLog = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs`;
  return postReferential(schema.log, uri, data)(dispatch);
};

export const deleteLog = (exerciseId, logId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/logs/${logId}`;
  return delReferential(uri, 'logs', logId)(dispatch);
};
