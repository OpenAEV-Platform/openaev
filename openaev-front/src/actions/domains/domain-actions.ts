import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfDomains } from './domain-schema';

const DOMAIN_URI = '/api/domains';

const fetchDomains = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfDomains, DOMAIN_URI)(dispatch);
};

export default fetchDomains;
