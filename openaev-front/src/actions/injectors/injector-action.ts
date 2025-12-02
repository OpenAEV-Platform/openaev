import { simpleCall, simplePostCall } from '../../utils/Action';
import { type Option } from '../../utils/api-types';

const INJECTOR_URI = '/api/injectors';

export const searchInjectorsByNameAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall<Option[]>(`${INJECTOR_URI}/options`, { params });
};

export const searchInjectorByIdAsOptions = (ids: string[], sourceId: string = '') => {
  const url = sourceId
    ? `${INJECTOR_URI}/options?sourceId=${sourceId}`
    : `${INJECTOR_URI}/options`;
  return simplePostCall<Option[]>(url, ids);
};
