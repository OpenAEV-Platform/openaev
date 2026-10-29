import { simpleCall } from '../../utils/Action';
import { type CapabilityScope } from '../../utils/permissions/types';

const CAPABILITIES_URI = '/api/capabilities';

export const fetchCapabilities = (scope: CapabilityScope) => simpleCall(`${CAPABILITIES_URI}?scope=${scope}`);
