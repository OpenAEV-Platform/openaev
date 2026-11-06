import { act, renderHook } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination, isDefaultListState } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type SearchPaginationInput } from '../../../../utils/api-types';

vi.mock('../../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../../utils/url-helper')>();
  return {
    ...original,
    getCurrentTenantId: () => 'tenant-a',
  };
});

const STORAGE_KEY = 'threat-arsenal';
const STORED_KEY = `tenant-a:${STORAGE_KEY}`;
const DEFAULTS = buildSearchPagination({ sorts: initSorting('payload_updated_at', 'DESC') });
const DEPRECATED_FILTER = {
  mode: 'and' as const,
  filters: [{
    id: 'f1',
    key: 'payload_status',
    operator: 'not_eq' as const,
    mode: 'and' as const,
    values: ['DEPRECATED'],
  }],
};

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/admin/threat-arsenal']}>{children}</MemoryRouter>
);

const useListWithLocation = (defaults: Partial<SearchPaginationInput>) => ({
  list: useQueryableWithLocalStorage(STORAGE_KEY, defaults),
  location: useLocation(),
});

// #4181: the default state of a list used to be written to localStorage and to the URL on the
// first visit, which pinned it for the user: a default changed later never reached them.
describe('default list state is never persisted', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('isDefaultListState', () => {
    it('ignores the page, filter ids, an absent filter group and an absent sort direction', () => {
      expect(isDefaultListState({
        ...DEFAULTS,
        page: 3,
        filterGroup: {
          mode: 'and',
          filters: [],
        },
        sorts: [{ property: 'payload_updated_at' }],
      }, DEFAULTS)).toBe(true);
    });

    it('sees a changed sort, size, text search or filter', () => {
      expect(isDefaultListState({
        ...DEFAULTS,
        sorts: initSorting('payload_name', 'ASC'),
      }, DEFAULTS)).toBe(false);
      expect(isDefaultListState({
        ...DEFAULTS,
        size: 100,
      }, DEFAULTS)).toBe(false);
      expect(isDefaultListState({
        ...DEFAULTS,
        textSearch: 'mimikatz',
      }, DEFAULTS)).toBe(false);
      expect(isDefaultListState({
        ...DEFAULTS,
        filterGroup: DEPRECATED_FILTER,
      }, DEFAULTS)).toBe(false);
    });
  });

  it('stores nothing on a first visit', () => {
    renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });
    expect(localStorage.getItem(STORED_KEY)).toBeNull();
  });

  it('stores a user change and clears the entry when the list is back to its default', () => {
    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });

    act(() => {
      result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        filterGroup: DEPRECATED_FILTER,
      }));
    });
    expect(localStorage.getItem(STORED_KEY)).not.toBeNull();

    act(() => {
      result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        filterGroup: {
          mode: 'and',
          filters: [],
        },
      }));
    });
    expect(localStorage.getItem(STORED_KEY)).toBeNull();
  });

  it('lets a new default reach a user who never changed the list', () => {
    const { unmount } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });
    unmount();

    const newDefaults = {
      ...DEFAULTS,
      filterGroup: DEPRECATED_FILTER,
    };
    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, newDefaults), { wrapper });
    expect(result.current.searchPaginationInput.filterGroup?.filters?.[0]?.values).toEqual(['DEPRECATED']);
  });

  it('writes no query parameter for the default state and one for a change', () => {
    const { result } = renderHook(() => useListWithLocation(DEFAULTS), { wrapper });

    act(() => {
      result.current.list.queryableHelpers.uriHelpers?.updateUri();
    });
    expect(new URLSearchParams(result.current.location.search).has('query')).toBe(false);

    act(() => {
      result.current.list.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        textSearch: 'mimikatz',
      }));
    });
    act(() => {
      result.current.list.queryableHelpers.uriHelpers?.updateUri();
    });
    expect(new URLSearchParams(result.current.location.search).has('query')).toBe(true);
  });
});
