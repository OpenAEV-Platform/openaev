import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { type CapabilityScope } from '../../utils/permissions/types';
import { arrayOfPlatformCapabilities, arrayOfTenantCapabilities } from './capability-schema';

const CAPABILITIES_URI = '/api/capabilities';

// eslint-disable-next-line import/prefer-default-export
export const fetchCapabilities = (scope: CapabilityScope) => (dispatch: Dispatch) => {
  const capabilitySchema = scope === 'PLATFORM' ? arrayOfPlatformCapabilities : arrayOfTenantCapabilities;
  return getReferential(capabilitySchema, `${CAPABILITIES_URI}?scope=${scope}`)(dispatch);
};
