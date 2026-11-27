import { act, renderHook } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination, listDefaultsHash } from '../../../../components/common/queryable/QueryableUtils';
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
const NEW_DEFAULTS = buildSearchPagination({
  ...DEFAULTS,
  filterGroup: DEPRECATED_FILTER,
});
const storedKey = (defaults: Partial<SearchPaginationInput>) => `tenant-a:${STORAGE_KEY}:${listDefaultsHash(defaults)}`;

const wrapper = ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={['/admin/threat-arsenal']}>{children}</MemoryRouter>
);

const useListWithLocation = (defaults: Partial<SearchPaginationInput>) => ({
  list: useQueryableWithLocalStorage(STORAGE_KEY, defaults),
  location: useLocation(),
});

// #4181: a list's state stored in localStorage pinned the defaults of the version the user first
// saw, so a default changed later never reached them. The storage key now carries a hash of the
// list's defaults: a changed default gives a new key.
describe('a changed list default reaches every user', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('listDefaultsHash', () => {
    it('is stable for the same defaults, whatever the page and the generated filter ids', () => {
      expect(listDefaultsHash(DEFAULTS)).toBe(listDefaultsHash({
        ...DEFAULTS,
        page: 3,
      }));
      expect(listDefaultsHash(NEW_DEFAULTS)).toBe(listDefaultsHash({
        ...NEW_DEFAULTS,
        filterGroup: {
          ...DEPRECATED_FILTER,
          filters: DEPRECATED_FILTER.filters.map(filter => ({
            ...filter,
            id: 'another-generated-id',
          })),
        },
      }));
    });

    it('changes with a default sort, size, text search or filter', () => {
      const hash = listDefaultsHash(DEFAULTS);
      expect(listDefaultsHash({
        ...DEFAULTS,
        sorts: initSorting('payload_name', 'ASC'),
      })).not.toBe(hash);
      expect(listDefaultsHash({
        ...DEFAULTS,
        size: 100,
      })).not.toBe(hash);
      expect(listDefaultsHash({
        ...DEFAULTS,
        textSearch: 'mimikatz',
      })).not.toBe(hash);
      expect(listDefaultsHash(NEW_DEFAULTS)).not.toBe(hash);
    });
  });

  it('stores a user change under a key carrying the hash of the list defaults', () => {
    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });

    act(() => {
      result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        textSearch: 'mimikatz',
      }));
    });
    expect(JSON.parse(localStorage.getItem(storedKey(DEFAULTS)) ?? '{}').textSearch).toBe('mimikatz');
    expect(localStorage.getItem(`tenant-a:${STORAGE_KEY}`)).toBeNull();
  });

  it('restores the user change while the defaults are unchanged', () => {
    const first = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });
    act(() => {
      first.result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        sorts: initSorting('payload_name', 'ASC'),
      }));
    });
    first.unmount();

    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });
    expect(result.current.searchPaginationInput.sorts?.[0]?.property).toBe('payload_name');
  });

  it('applies a new default to a user who had changed the list, ignoring the old entry', () => {
    const first = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, DEFAULTS), { wrapper });
    act(() => {
      first.result.current.setSearchPaginationInput((prev: SearchPaginationInput) => ({
        ...prev,
        sorts: initSorting('payload_name', 'ASC'),
      }));
    });
    first.unmount();
    expect(localStorage.getItem(storedKey(DEFAULTS))).not.toBeNull();

    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, NEW_DEFAULTS), { wrapper });
    expect(result.current.searchPaginationInput.filterGroup?.filters?.[0]?.values).toEqual(['DEPRECATED']);
    expect(result.current.searchPaginationInput.sorts?.[0]?.property).toBe('payload_updated_at');
  });

  it('applies a new default to a user whose old entry predates the hashed key', () => {
    localStorage.setItem(`tenant-a:${STORAGE_KEY}`, JSON.stringify(DEFAULTS));

    const { result } = renderHook(() => useQueryableWithLocalStorage(STORAGE_KEY, NEW_DEFAULTS), { wrapper });
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
