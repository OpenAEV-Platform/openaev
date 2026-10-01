import { type APIRequestContext } from '@playwright/test';

import { type AtomicTestingInput, type InjectResultOverviewOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class AtomicTestingApiHelpers {
  readonly atomicTestingUri = tenantApiPath('/api/atomic-testings');

  constructor(private request: APIRequestContext) {}

  async createAtomicTesting(input: AtomicTestingInput): Promise<InjectResultOverviewOutput> {
    const response = await this.request.post(this.atomicTestingUri, { data: input });
    if (!response.ok()) {
      throw new Error(
        `Failed to create atomic testing (POST ${this.atomicTestingUri}): `
        + `${response.status()} ${response.statusText()} - ${await response.text()}`,
      );
    }
    return response.json();
  }

  async getAtomicTesting(id: string): Promise<InjectResultOverviewOutput> {
    const response = await this.request.get(`${this.atomicTestingUri}/${id}`);
    return response.json();
  }

  async deleteAtomicTesting(id: string) {
    await this.request.delete(`${this.atomicTestingUri}/${id}`);
  }
}

export default AtomicTestingApiHelpers;
