import { type APIRequestContext } from '@playwright/test';

import { type Inject } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class InjectApiHelpers {
  readonly injectUri = tenantApiPath('/api/injects');

  constructor(private request: APIRequestContext) {}

  async getInject(id: string): Promise<Inject> {
    const response = await this.request.get(`${this.injectUri}/${id}`);
    return response.json();
  }
}

export default InjectApiHelpers;
