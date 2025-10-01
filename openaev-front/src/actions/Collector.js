import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchCollectors = () => (dispatch) => {
  const uri = '/api/collectors';
  return getReferential(schema.arrayOfCollectors, uri)(dispatch);
};

export const fetchCollector = collectorId => (dispatch) => {
  const uri = `/api/collectors/${collectorId}`;
  return getReferential(schema.collector, uri)(dispatch);
};

export const searchCollectors = (paginationInput) => {
  const data = paginationInput;
  const uri = '/api/collectors/search';
  return simplePostCall(uri, data);
};

export const updateCollector = (collectorId, data) => (dispatch) => {
  const uri = `/api/collectors/${collectorId}`;
  return putReferential(schema.collector, uri, data)(dispatch);
};

export const addCollector = data => (dispatch) => {
  const uri = '/api/collectors';
  return postReferential(schema.collector, uri, data)(dispatch);
};

export const deleteCollector = collectorId => (dispatch) => {
  const uri = `/api/collectors/${collectorId}`;
  return delReferential(uri, 'collectors', collectorId)(dispatch);
};
