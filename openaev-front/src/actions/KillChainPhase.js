import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchKillChainPhases = () => (dispatch) => {
  const uri = '/api/kill_chain_phases';
  return getReferential(schema.arrayOfKillChainPhases, uri)(dispatch);
};

export const searchKillChainPhases = (searchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = '/api/kill_chain_phases/search';
  return simplePostCall(uri, data);
};

export const updateKillChainPhase = (killChainPhaseId, data) => (dispatch) => {
  const uri = `/api/kill_chain_phases/${killChainPhaseId}`;
  return putReferential(schema.killChainPhase, uri, data)(dispatch);
};

export const addKillChainPhase = data => (dispatch) => {
  const uri = '/api/kill_chain_phases';
  return postReferential(schema.killChainPhase, uri, data)(dispatch);
};

export const deleteKillChainPhase = killChainPhaseId => (dispatch) => {
  const uri = `/api/kill_chain_phases/${killChainPhaseId}`;
  return delReferential(uri, 'killchainphases', killChainPhaseId)(dispatch);
};
