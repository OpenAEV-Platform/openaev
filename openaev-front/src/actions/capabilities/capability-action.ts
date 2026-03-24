import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfCapabilities } from './capability-schema';

const CAPABILITIES_URI = '/api/capabilities';

// eslint-disable-next-line import/prefer-default-export
export const fetchCapabilities = (scope?: 'PLATFORM' | 'TENANT') => (dispatch: Dispatch) => {
  const params = scope ? `?scope=${scope}` : '';
  return getReferential(arrayOfCapabilities, `${CAPABILITIES_URI}${params}`)(dispatch);
};
