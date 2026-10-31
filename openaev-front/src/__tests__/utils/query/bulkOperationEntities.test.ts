import { describe, expect, it } from 'vitest';

import bulkEntityTypes from '../../../utils/query/bulkOperationEntities';

describe('bulkEntityTypes', () => {
  it.each([
    ['scenarios', ['scenarios']],
    ['simulations', ['exercises']],
    ['asset groups', ['asset_groups']],
    ['Players', ['users']],
    [' injects ', ['injects', 'inject_statuses', 'injectexpectations']],
  ])('given_%s_should_mapToSchemaNames', (label, expected) => {
    expect(bulkEntityTypes(label)).toEqual(expected);
  });

  it.each([undefined, 'unknown label'])('given_%s_should_returnUndefined', (label) => {
    expect(bulkEntityTypes(label)).toBeUndefined();
  });
});
