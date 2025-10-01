import { simpleCall, simplePostCall } from '../../utils/Action';

const INJECTOR_URI = '/api/injectors';

export const searchInjectorsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${INJECTOR_URI}/options`, { params });
};

export const searchInjectorByIdAsOptions = (ids: string[]) => {
  return simplePostCall(`${INJECTOR_URI}/options`, ids);
};
