import { type APIRequestContext } from '@playwright/test';

import { type MarkingDefinitionOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class MarkingDefinitionApiHelpers {
  readonly markingDefinitionsUri = tenantApiPath('/api/marking_definitions');

  constructor(private request: APIRequestContext) {}

  async listAssignable(): Promise<MarkingDefinitionOutput[]> {
    const uri = `${this.markingDefinitionsUri}/assignable`;
    const response = await this.request.get(uri);
    if (!response.ok()) {
      throw new Error(`Failed to list assignable marking definitions (GET ${uri}): ${response.status()} ${response.statusText()} - ${await response.text()}`);
    }
    return response.json();
  }
}

export default MarkingDefinitionApiHelpers;
