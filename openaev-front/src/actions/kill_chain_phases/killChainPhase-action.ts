import { simpleCall, simplePostCall } from '../../utils/Action';

const KILL_CHAIN_PHASE_URI = '/api/kill_chain_phases';

export const searchKillChainPhasesByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${KILL_CHAIN_PHASE_URI}/options`, { params });
};

export const searchKillChainPhasesByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${KILL_CHAIN_PHASE_URI}/options`, ids);
};
