import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { type SearchPaginationInput } from '../../utils/api-types';
import { arrayOfDomains } from './domain-schema';

const DOMAIN_URI = '/api/domains';

export const fetchDomains = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfDomains, DOMAIN_URI)(dispatch);
};
// -- OPTION --

export const searchDomainsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${DOMAIN_URI}/options`, { params });
};

export const searchDomainsByIdsAsOption = (ids: string[]) => {
  return simplePostCall(`${DOMAIN_URI}/options`, ids);
};

// This action must use InjectorContractSearchPaginationInput to stay
// synchronized with the search route filters
export const fetchDomainCounts = (apiPrefix: string, data: SearchPaginationInput) => {
  const uri = `/api/${apiPrefix}/domain-counts`;
  return simplePostCall(uri, data);
};
