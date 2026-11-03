import type { APIRequestContext } from '@playwright/test';

import { tenantApiPath } from '../utils/url';

export interface E2eEndpoint {
  asset_id: string;
  asset_name: string;
}

export interface EndpointFields {
  name: string;
  hostname: string;
  macAddress: string;
}

const endpointInput = ({ name, hostname, macAddress }: EndpointFields) => ({
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

  async createAgentlessEndpoint(fields: EndpointFields): Promise<E2eEndpoint> {
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
    await this.request.delete(`${this.assetUri}/${id}`);
  }
}

export default EndpointApiHelpers;
