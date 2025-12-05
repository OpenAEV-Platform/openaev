import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simplePostCall } from '../../utils/Action';
import * as schema from '../Schema';

const INJECTOR_URI = '/api/injectors';

export const fetchInjectors = (isNextIncluded = false) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_URI}?include_next=${isNextIncluded}`;
  return getReferential(schema.arrayOfInjectors, uri)(dispatch);
};

export const fetchInjector = (injectorId: string) => (dispatch: Dispatch) => {
  const uri = `${INJECTOR_URI}/${injectorId}`;
  return getReferential(schema.injector, uri)(dispatch);
};

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

export const fetchInjectorRelatedIds = (injectorId: string) => {
  return simpleCall(`${INJECTOR_URI}/${injectorId}/related-ids`);
};
