import { type APIRequestContext } from '@playwright/test';

import { type ExecutionTraceOutput, type Inject } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class InjectApiHelpers {
  readonly injectUri = tenantApiPath('/api/injects');

  constructor(private request: APIRequestContext) {}

  async getInject(id: string): Promise<Inject> {
    const response = await this.request.get(`${this.injectUri}/${id}`);
    return response.json();
  }

  /**
   * The execution traces of an inject on one asset, with the response status. A refusal (403 or 404) returns an
   * empty list: either way, no trace of that asset reaches the caller. Any other error throws.
   */
  async getAssetExecutionTraces(injectId: string, assetId: string): Promise<{
    status: number;
    traces: ExecutionTraceOutput[];
  }> {
    const uri = `${this.injectUri}/execution-traces`;
    const response = await this.request.get(uri, {
      params: {
        injectId,
        targetId: assetId,
        targetType: 'ASSETS',
      },
    });
    const status = response.status();
    if ([403, 404].includes(status)) {
      return {
        status,
        traces: [],
      };
    }
    if (!response.ok()) {
      throw new Error(`Failed to get execution traces (GET ${uri}): ${status} ${response.statusText()} - ${await response.text()}`);
    }
    return {
      status,
      traces: await response.json(),
    };
  }
}

export default InjectApiHelpers;
