import { type APIRequestContext } from '@playwright/test';

import { type Endpoint, type MarkedEndpointInput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class EndpointsApiHelpers {
  readonly endpointsUri = tenantApiPath('/api/endpoints');
  constructor(private request: APIRequestContext) {}

  async createEndpoint(name?: string, markings?: string[]): Promise<Endpoint> {
    const input: MarkedEndpointInput = {
      asset_name: name || `Asset Agentless test e2e ${Date.now()}`,
      asset_description: '',
      asset_tags: [],
      asset_category: 'WEB_APPLICATION',
      asset_subcategory: 'WEBSITE',
      asset_criticality: 'UNKNOWN',
      asset_internet_facing: true,
      asset_hostname: '',
      asset_url: 'https://test.com',
      asset_ips: [],
      endpoint_is_eol: false,
      asset_linked_person: null,
      asset_markings: markings ?? [],
    };
    const response = await this.request.post(`${this.endpointsUri}/agentless`, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to create endpoint (POST ${this.endpointsUri}/agentless): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }

  async deleteEndpoint(id: string) {
    await this.request.delete(`${this.endpointsUri}/${id}`);
  }
}

export default EndpointsApiHelpers;
