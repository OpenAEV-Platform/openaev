import { type CapabilityOutput } from '../../utils/api-types';

export interface CapabilityHelper {
  getPlatformCapabilities: () => CapabilityOutput[];
  getPlatformCapabilitiesMap: () => Record<string, CapabilityOutput>;
  getTenantCapabilities: () => CapabilityOutput[];
  getTenantCapabilitiesMap: () => Record<string, CapabilityOutput>;
}
