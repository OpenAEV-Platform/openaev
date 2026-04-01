import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfPlatformCapabilities, arrayOfTenantCapabilities } from './capability-schema';

const CAPABILITIES_URI = '/api/capabilities';

// eslint-disable-next-line import/prefer-default-export
export const fetchCapabilities = (scope: 'PLATFORM' | 'TENANT') => (dispatch: Dispatch) => {
  const capabilitySchema = scope === 'PLATFORM' ? arrayOfPlatformCapabilities : arrayOfTenantCapabilities;
  return getReferential(capabilitySchema, `${CAPABILITIES_URI}?scope=${scope}`)(dispatch);
};
