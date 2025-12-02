import { simpleCall, simplePostCall } from '../../utils/Action';
import { type Option } from '../../utils/api-types';

export const SIMULATION_URI = '/api/simulations';

export const searchSimulationAsOptions = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${SIMULATION_URI}/options`, { params });
};

export const searchSimulationByIdAsOptions = (ids: string[]) => {
  return simplePostCall<Option[]>(`${SIMULATION_URI}/options`, ids);
};
