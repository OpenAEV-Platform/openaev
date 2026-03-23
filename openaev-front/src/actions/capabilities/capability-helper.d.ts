import { type CapabilityOutput } from '../../utils/api-types';

export interface CapabilityHelper {
  getCapabilities: () => CapabilityOutput[];
  getCapabilitiesMap: () => Record<string, CapabilityOutput>;
}
