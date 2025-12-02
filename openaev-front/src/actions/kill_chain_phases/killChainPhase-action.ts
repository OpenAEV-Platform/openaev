import { simpleCall, simplePostCall } from '../../utils/Action';
import { type Option } from '../../utils/api-types';

const KILL_CHAIN_PHASE_URI = '/api/kill_chain_phases';

export const searchKillChainPhasesByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${KILL_CHAIN_PHASE_URI}/options`, { params });
};

export const searchKillChainPhasesByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${KILL_CHAIN_PHASE_URI}/options`, ids);
};
