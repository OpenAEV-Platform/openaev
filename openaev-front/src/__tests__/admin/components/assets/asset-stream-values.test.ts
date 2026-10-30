import { describe, expect, it } from 'vitest';

import { patchRowsFromStore, withoutNullValues } from '../../../../admin/components/assets/asset-stream-values';

describe('withoutNullValues', () => {
  it('given_nullFields_should_dropThemSoTheFormDefaultsApply', () => {
    // Arrange
    const streamed = {
      asset_name: 'host',
      asset_url: null,
      asset_cloud_region: null,
      asset_tags: [],
      endpoint_is_eol: false,
    };

    // Act
    const values = withoutNullValues(streamed);

    // Assert
    expect(values).toEqual({
      asset_name: 'host',
      asset_tags: [],
      endpoint_is_eol: false,
    });
  });
});

describe('patchRowsFromStore', () => {
  const row = {
    asset_id: 'e1',
    asset_name: 'toto',
    asset_tags: ['t1'],
    asset_agents: [{ agent_id: 'searched-agent' }],
  };

  it('given_anEntityRenamedInTheStore_should_patchItsRow', () => {
    // Arrange
    const previous = { e1: { asset_name: 'toto' } };
    const current = {
      e1: {
        asset_name: 'toto3',
        asset_url: null,
        asset_agents: [{ agent_id: 'streamed-agent' }],
      },
    };

    // Act
    const rows = patchRowsFromStore([row], previous, current);

    // Assert
    expect(rows).toEqual([{
      ...row,
      asset_name: 'toto3',
    }]);
  });

  it('given_anEntityNewInTheStore_should_patchItsRow', () => {
    // Act
    const rows = patchRowsFromStore([row], {}, { e1: { asset_name: 'toto3' } });

    // Assert
    expect(rows[0].asset_name).toBe('toto3');
  });

  it('given_anEntityUnchangedInTheStore_should_keepTheSearchedRow', () => {
    // Arrange: the store copy is older than the row, but did not change since the last render
    const stale = { e1: { asset_name: 'old name' } };

    // Act
    const rows = patchRowsFromStore([row], stale, stale);

    // Assert
    expect(rows[0].asset_name).toBe('toto');
  });

  it('given_noDisplayedRowChanged_should_returnTheSameArray', () => {
    // Arrange
    const rows = [row];

    // Act
    const patched = patchRowsFromStore(rows, {}, { other: { asset_name: 'x' } });

    // Assert
    expect(patched).toBe(rows);
  });
});
