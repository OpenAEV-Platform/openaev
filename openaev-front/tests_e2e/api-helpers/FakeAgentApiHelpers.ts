import { randomInt } from 'node:crypto';

import { type APIRequestContext } from '@playwright/test';

import { type Endpoint, type EndpointRegisterInput, type InjectExecutionInput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

// Locally administered unicast prefix (02:...), so it never matches a real network card.
const randomMacAddress = () => ['02', ...Array.from({ length: 5 }, () => randomInt(256).toString(16).padStart(2, '0'))].join(':');

export interface FakeAgent {
  assetId: string;
  agentId: string;
}

/**
 * Plays an OpenAEV agent through its API, so an inject is dispatched to it and ends with the result the test
 * chooses, without installing a real agent.
 */
class FakeAgentApiHelpers {
  readonly endpointsUri = tenantApiPath('/api/endpoints');
  readonly injectUri = tenantApiPath('/api/injects');

  constructor(private request: APIRequestContext) {}

  async register(name: string): Promise<FakeAgent> {
    const uri = `${this.endpointsUri}/register`;
    const input: EndpointRegisterInput = {
      asset_name: name,
      asset_external_reference: `ext-${name}`,
      asset_hostname: name,
      asset_ips: [`10.66.${randomInt(256)}.${randomInt(1, 255)}`],
      // A new agent joins the endpoint of any matching MAC: each fake agent needs its own.
      asset_mac_addresses: [randomMacAddress()],
      endpoint_platform: 'Linux',
      endpoint_arch: 'x86_64',
      // An agent of another version gets an upgrade job queued at registration.
      endpoint_agent_version: await this.platformVersion(),
      agent_executed_by_user: 'root',
      agent_is_service: true,
      agent_is_elevated: true,
    };
    const response = await this.request.post(uri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to register fake agent (POST ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    const endpoint: Endpoint = await response.json();
    const agentId = endpoint.asset_agents?.[0]?.agent_id;
    if (!agentId) {
      throw new Error(`Fake agent ${name} registered without an agent`);
    }
    return {
      assetId: endpoint.asset_id,
      agentId,
    };
  }

  /** Sends the agent's final trace. The inject must be PENDING, otherwise the platform answers 409. */
  async complete(agentId: string, injectId: string, status: string) {
    const uri = `${this.injectUri}/execution/${agentId}/callback/${injectId}`;
    const input: InjectExecutionInput = {
      execution_message: `fake agent: ${status}`,
      execution_status: status,
      execution_action: 'complete',
      execution_duration: 1000,
    };
    const response = await this.request.post(uri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to complete inject (POST ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
  }

  private async platformVersion(): Promise<string> {
    const response = await this.request.get('/api/settings');
    return (await response.json()).platform_version;
  }
}

export default FakeAgentApiHelpers;
