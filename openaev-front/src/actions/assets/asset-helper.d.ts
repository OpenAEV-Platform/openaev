import { type Endpoint, type EndpointOverviewOutput, type SecurityPlatform } from '../../utils/api-types';

export interface EndpointHelper {
  getEndpoint: (endpointId: EndpointOverviewOutput['asset_id']) => EndpointOverviewOutput;
  getEndpoints: () => Endpoint[];
  getEndpointsMap: () => Record<string, Endpoint>;
}

export interface SecurityPlatformHelper {
  getSecurityPlatforms: () => SecurityPlatform[];
  getSecurityPlatformsMap: () => Record<string, SecurityPlatform>;
}
