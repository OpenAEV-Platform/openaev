import type { Dispatch } from 'redux';

import { getReferential, simpleCall, simplePostCall } from '../../utils/Action';
import { type Option } from '../../utils/api-types';
import { arrayOfDomains } from '../schemas';

const DOMAIN_URI = '/api/domains';

const fetchDomains = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfDomains, DOMAIN_URI)(dispatch);
};

export default fetchDomains;

// -- OPTION --

export const searchDomainsByNameAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${DOMAIN_URI}/options`, { params });
};

export const searchDomainsByIdsAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${DOMAIN_URI}/options`, ids);
};
