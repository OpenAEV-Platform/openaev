import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchThreatArsenalAuthorCounts } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import useActiveFacets from '../../../../../admin/components/threat_arsenal/hook/useActiveFacets';
import { type AuthorOption, useAuthorOptions } from '../../../../../components/common/facets/ContractFacets';
import { type QueryableHelpers } from '../../../../../components/common/queryable/QueryableHelpers';
import { type SearchPaginationInput } from '../../../../../utils/api-types';

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalAuthorCounts: vi.fn() }));
vi.mock('../../../../../actions/AttackPattern', () => ({ fetchAttackPatterns: vi.fn() }));
vi.mock('../../../../../store', () => ({ useHelper: () => [] }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../../admin/components/common/domains/useDomainIconFilter', () => ({ default: () => ({ iconBarOrderedDomains: [] }) }));
vi.mock('../../../../../admin/components/threat_arsenal/useThreatArsenalFacetCounts', () => ({ default: () => null }));
vi.mock('../../../../../components/common/facets/ContractFacets', () => ({ useAuthorOptions: vi.fn(() => []) }));

const SEARCH: SearchPaginationInput = {
  page: 0,
  size: 20,
};

describe('useActiveFacets', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_authorCounts_should_offerEachAuthorByItsName', async () => {
    // Arrange
    vi.mocked(fetchThreatArsenalAuthorCounts).mockResolvedValue({
      data: [
        {
          author: 'u1',
          author_name: 'Alice',
          author_type: 'USER',
        },
        {
          author: 'c1',
          author_type: 'COLLECTOR',
        },
        { author_type: 'NONE' },
      ],
    } as Awaited<ReturnType<typeof fetchThreatArsenalAuthorCounts>>);
    renderHook(() => useActiveFacets(SEARCH, {} as QueryableHelpers));
    const loadAuthors = vi.mocked(useAuthorOptions).mock.calls[0][0];

    // Act
    const authors: AuthorOption[] = await loadAuthors();

    // Assert
    expect(authors).toEqual([
      {
        value: 'u1',
        label: 'Alice',
        type: 'USER',
      },
      {
        value: 'c1',
        label: 'c1',
        type: 'COLLECTOR',
      },
      {
        value: '',
        label: '',
        type: 'NONE',
      },
    ]);
  });
});
