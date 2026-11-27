import { type APIRequestContext } from '@playwright/test';

import {
  type InjectorContractFullOutput,
  type PayloadCreateInput,
  type PayloadOutput,
} from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

export interface CreatedPayload {
  payloadId: string;
  contractId: string;
}

class PayloadApiHelpers {
  readonly payloadUri = tenantApiPath('/api/payloads');
  readonly injectorContractUri = tenantApiPath('/api/injector_contracts');

  constructor(private request: APIRequestContext) {}

  /**
   * Creates a Linux command payload, run by the OpenAEV agent executor, and returns the injector contract the
   * platform built for it: an inject uses the contract, not the payload.
   */
  async createCommandPayload(name: string): Promise<CreatedPayload> {
    const input = {
      payload_type: 'Command',
      payload_name: name,
      payload_source: 'MANUAL',
      payload_status: 'VERIFIED',
      payload_platforms: ['Linux'],
      payload_execution_arch: 'x86_64',
      payload_expectations: ['DETECTION'],
      payload_domains: [],
      command_executor: 'bash',
      command_content: 'echo e2e',
    } as unknown as PayloadCreateInput;
    const response = await this.request.post(this.payloadUri, { data: input });
    if (!response.ok()) {
      throw new Error(`Failed to create payload (POST ${this.payloadUri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    const payload: PayloadOutput = await response.json();
    return {
      payloadId: payload.payload_id,
      contractId: await this.findContractId(name),
    };
  }

  async deletePayload(id: string) {
    await this.request.delete(`${this.payloadUri}/${id}`);
  }

  // The payload output does not carry its contract: the contract is labelled with the payload name.
  private async findContractId(payloadName: string): Promise<string> {
    const uri = `${this.injectorContractUri}/search`;
    const response = await this.request.post(uri, {
      data: {
        page: 0,
        size: 10,
        textSearch: payloadName,
      },
    });
    if (!response.ok()) {
      throw new Error(`Failed to search injector contracts (POST ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    const { content }: { content: InjectorContractFullOutput[] } = await response.json();
    if (content.length !== 1) {
      throw new Error(`Expected one injector contract for payload ${payloadName}, found ${content.length}`);
    }
    return content[0].injector_contract_id;
  }
}

export default PayloadApiHelpers;
