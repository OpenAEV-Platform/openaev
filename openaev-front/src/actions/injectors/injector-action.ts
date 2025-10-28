import { simpleCall, simplePostCall } from '../../utils/Action';

const INJECTOR_URI = '/api/injectors';

export const searchInjectorsByNameAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall(`${INJECTOR_URI}/options`, { params });
};

export const searchInjectorByIdAsOptions = (ids: string[], sourceId: string = '') => {
  const url = sourceId
    ? `${INJECTOR_URI}/options?sourceId=${sourceId}`
    : `${INJECTOR_URI}/options`;
  return simplePostCall(url, ids);
};
