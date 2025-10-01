import { simpleCall, simplePostCall } from '../../utils/Action';

export const SIMULATION_URI = '/api/simulations';

export const searchSimulationAsOptions = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${SIMULATION_URI}/options`, { params });
};

export const searchSimulationByIdAsOptions = (ids: string[]) => {
  return simplePostCall(`${SIMULATION_URI}/options`, ids);
};
