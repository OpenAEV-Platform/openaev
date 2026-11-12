import { describe, expect, it } from 'vitest';

import { keepStreamedChanges, patchRowsFromStore, withoutNullValues } from '../../../../admin/components/assets/asset-stream-values';
import { type Page } from '../../../../components/common/queryable/Page';
import { type SearchPaginationInput } from '../../../../utils/api-types';

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

describe('keepStreamedChanges', () => {
  type Row = {
    asset_id: string;
    asset_name: string;
  };
  const pageOf = (content: Row[]) => ({ data: { content } as Page<Row> });

  it('given_aRenameStreamedBeforeTheSearchResponse_should_keepTheNewName', async () => {
    // Arrange: the search reads the old name, the stream renames the row before the response
    let store: Record<string, Record<string, unknown>> = { e1: { asset_name: 'old' } };
    let answer: (page: { data: Page<Row> }) => void = () => {};
    const search = () => new Promise<{ data: Page<Row> }>((resolve) => {
      answer = resolve;
    });
    const response = keepStreamedChanges(search, () => store)({} as SearchPaginationInput);

    // Act
    store = { e1: { asset_name: 'new' } };
    answer(pageOf([{
      asset_id: 'e1',
      asset_name: 'old',
    }]));

    // Assert
    expect((await response).data.content[0].asset_name).toBe('new');
  });

  it('given_noStreamedChangeDuringTheSearch_should_keepTheSearchedRows', async () => {
    // Arrange: the store copy is older than the search, and did not change while it ran
    const store = { e1: { asset_name: 'old' } };
    const searched = [{
      asset_id: 'e1',
      asset_name: 'searched',
    }];

    // Act
    const response = await keepStreamedChanges(() => Promise.resolve(pageOf(searched)), () => store)({} as SearchPaginationInput);

    // Assert
    expect(response.data.content).toBe(searched);
  });
});
