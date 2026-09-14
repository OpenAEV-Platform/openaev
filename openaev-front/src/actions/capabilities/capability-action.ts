import { simpleCall } from '../../utils/Action';
import { type CapabilityScope } from '../../utils/permissions/types';

const CAPABILITIES_URI = '/api/capabilities';

// eslint-disable-next-line import/prefer-default-export
export const fetchCapabilities = (scope: CapabilityScope) => simpleCall(`${CAPABILITIES_URI}?scope=${scope}`);
