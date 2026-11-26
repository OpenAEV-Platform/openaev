import { type APIRequestContext } from '@playwright/test';

import { type MarkingDefinitionOutput } from '../../src/utils/api-types';
import { tenantApiPath } from '../utils/url';

class MarkingDefinitionApiHelpers {
  readonly markingDefinitionUri = tenantApiPath('/api/marking_definitions');

  constructor(private request: APIRequestContext) {}

  /** The id of a seeded definition (`TLP:RED`…), among those the caller is cleared for. */
  async findIdByDefinition(definition: string): Promise<string> {
    const response = await this.request.get(`${this.markingDefinitionUri}/assignable`);
    if (!response.ok()) {
      throw new Error(`Failed to list the marking definitions: ${response.status()} - ${await response.text()}`);
    }
    const found = ((await response.json()) as MarkingDefinitionOutput[])
      .find(marking => marking.marking_definition_definition === definition);
    if (!found) {
      throw new Error(`Marking definition ${definition} not found`);
    }
    return found.marking_definition_id;
  }
}

export default MarkingDefinitionApiHelpers;
