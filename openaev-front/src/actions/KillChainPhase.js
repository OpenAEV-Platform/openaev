import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import { arrayOfKillChainPhases, killChainPhase } from './schemas';

export const fetchKillChainPhases = (dispatch) => {
  const uri = '/api/kill_chain_phases';
  return getReferential(arrayOfKillChainPhases, uri)(dispatch);
};

export const searchKillChainPhases = (searchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = '/api/kill_chain_phases/search';
  return simplePostCall(uri, data);
};

export const updateKillChainPhase = (killChainPhaseId, data) => (dispatch) => {
  const uri = `/api/kill_chain_phases/${killChainPhaseId}`;
  return putReferential(killChainPhase, uri, data)(dispatch);
};

export const addKillChainPhase = data => (dispatch) => {
  const uri = '/api/kill_chain_phases';
  return postReferential(killChainPhase, uri, data)(dispatch);
};

export const deleteKillChainPhase = killChainPhaseId => (dispatch) => {
  const uri = `/api/kill_chain_phases/${killChainPhaseId}`;
  return delReferential(uri, 'killchainphases', killChainPhaseId)(dispatch);
};
