import type { APIRequestContext } from '@playwright/test';

import { type Endpoint, type EndpointInput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

export interface EndpointFields {
  name: string;
  hostname: string;
  macAddress: string;
}

const endpointInput = ({ name, hostname, macAddress }: EndpointFields): EndpointInput => ({
  asset_name: name,
  asset_hostname: hostname,
  asset_tags: [],
  asset_ips: ['10.10.10.10'],
  asset_mac_addresses: [macAddress],
  endpoint_platform: 'Linux',
  endpoint_arch: 'x86_64',
});

/** Locally administered MAC address, unique enough to identify one test endpoint. */
export const randomMacAddress = () => `02:${Array.from({ length: 5 }, () => Math.floor(Math.random() * 256)
  .toString(16)
  .padStart(2, '0')).join(':')}`;

class EndpointApiHelpers {
  readonly endpointUri = tenantApiPath('/api/endpoints');
  readonly assetUri = tenantApiPath('/api/assets');
  constructor(private request: APIRequestContext) {}

  async createAgentlessEndpoint(fields: EndpointFields): Promise<Endpoint> {
    const response = await this.request.post(`${this.endpointUri}/agentless`, { data: endpointInput(fields) });
    if (!response.ok()) {
      throw new Error(`Endpoint creation failed: ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  async updateEndpoint(id: string, fields: EndpointFields) {
    const response = await this.request.put(`${this.endpointUri}/${id}`, { data: endpointInput(fields) });
    if (!response.ok()) {
      throw new Error(`Endpoint update failed: ${response.status()} ${await response.text()}`);
    }
  }

  async deleteAsset(id: string) {
    const response = await this.request.delete(`${this.assetUri}/${id}`);
    if (!response.ok()) {
      throw new Error(`Asset deletion failed: ${response.status()} ${await response.text()}`);
    }
  }

  /** Teardown: an asset the test already deleted answers 404. */
  async deleteAssetIfPresent(id: string) {
    const response = await this.request.delete(`${this.assetUri}/${id}`);
    if (!response.ok() && response.status() !== 404) {
      throw new Error(`Asset cleanup failed: ${response.status()} ${await response.text()}`);
    }
  }
}

export default EndpointApiHelpers;
