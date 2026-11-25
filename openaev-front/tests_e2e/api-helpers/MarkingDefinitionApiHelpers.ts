import { type APIRequestContext } from '@playwright/test';

import { type MarkingDefinitionOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class MarkingDefinitionApiHelpers {
  readonly markingDefinitionsUri = tenantApiPath('/api/marking_definitions');

  constructor(private request: APIRequestContext) {
  }

  async listAssignable(): Promise<MarkingDefinitionOutput[]> {
    return (await this.request.get(`${this.markingDefinitionsUri}/assignable`)).json();
  }
}

export default MarkingDefinitionApiHelpers;
