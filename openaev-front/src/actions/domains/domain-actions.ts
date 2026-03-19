import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { arrayOfDomains } from './domain-schema';

const DOMAIN_URI = '/api/domains';

const fetchDomains = () => (dispatch: Dispatch) => {
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

export default fetchDomains;
