import { QueryClient, QueryClientProvider, queryOptions } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter, useSearchParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type Page } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { retrieveFromUri } from '../../../../components/common/queryable/uri/useUriState';
import usePaginatedQuery from '../../../../components/common/queryable/usePaginatedQuery';
import { type SearchPaginationInput } from '../../../../utils/api-types';

vi.mock('../../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../../utils/url-helper')>();
  return {
    ...original,
    getCurrentTenantId: () => 'tenant-a',
  };
});

const STORAGE_KEY = 'items';

interface Item { id: string }

const SORT = {
  empty: true,
  sorted: false,
  unsorted: true,
};

// The page the server answers for an input, out of `total` items.
const pageOf = (input: SearchPaginationInput, total: number): Page<Item> => {
  const first = input.page * input.size;
  const content = Array.from({ length: Math.max(0, Math.min(input.size, total - first)) }, (_, index) => ({ id: `item-${first + index}` }));
  const totalPages = Math.ceil(total / input.size);
  return {
    content,
    empty: content.length === 0,
    first: input.page === 0,
    last: input.page >= totalPages - 1,
    number: input.page,
    numberOfElements: content.length,
    pageable: {
      offset: first,
      pageNumber: input.page,
      pageSize: input.size,
      paged: true,
      sort: SORT,
      unpaged: false,
    },
    size: input.size,
    sort: SORT,
    totalElements: total,
    totalPages,
  };
};

const deferred = <T, >() => {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return {
    promise,
    resolve,
  };
};

describe('usePaginatedQuery', () => {
  let queryClient: QueryClient;
  const search = vi.fn<(input: SearchPaginationInput) => Promise<Page<Item>>>();

  const itemQueries = (input: SearchPaginationInput) => queryOptions({
    queryKey: ['items', input],
    queryFn: () => search(input),
  });

  const renderList = (init: Partial<SearchPaginationInput> = { size: 20 }) => renderHook(() => ({
    list: usePaginatedQuery(STORAGE_KEY, buildSearchPagination(init), itemQueries),
    searchParams: useSearchParams()[0],
  }), {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin/items']}>{children}</MemoryRouter>
      </QueryClientProvider>
    ),
  });

  beforeEach(() => {
    localStorage.clear();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    search.mockReset();
    search.mockImplementation(input => Promise.resolve(pageOf(input, 45)));
  });

  afterEach(() => {
    // Unmounted, so that a later test's storage writes cannot reach these lists.
    cleanup();
    queryClient.clear();
  });

  it('given_aFirstLoad_should_exposeThePageAndTheTotalOfItsData', async () => {
    // Act
    const { result } = renderList();

    // Assert
    expect(result.current.list.query.isPending).toBe(true);
    expect(result.current.list.items).toEqual([]);
    await waitFor(() => expect(result.current.list.items).toHaveLength(20));
    expect(result.current.list.queryableHelpers.paginationHelpers.getTotalElements()).toBe(45);
    expect(search).toHaveBeenCalledWith(expect.objectContaining({
      page: 0,
      size: 20,
    }));
  });

  it('given_anotherPage_should_keepThePreviousRowsAndTotalUntilItsRowsArrive', async () => {
    // Arrange
    const { result } = renderList();
    await waitFor(() => expect(result.current.list.items).toHaveLength(20));
    const secondPage = deferred<Page<Item>>();
    search.mockReturnValueOnce(secondPage.promise);

    // Act
    act(() => result.current.list.queryableHelpers.paginationHelpers.handleChangePage(1));

    // Assert
    await waitFor(() => expect(search).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
    expect(result.current.list.query.isPlaceholderData).toBe(true);
    expect(result.current.list.items[0]).toEqual({ id: 'item-0' });
    expect(result.current.list.queryableHelpers.paginationHelpers.getTotalElements()).toBe(45);
    act(() => secondPage.resolve(pageOf(result.current.list.searchPaginationInput, 45)));
    await waitFor(() => expect(result.current.list.items[0]).toEqual({ id: 'item-20' }));
    expect(result.current.list.query.isPlaceholderData).toBe(false);
  });

  // Page 2 (the last one, 5 rows) shown.
  const renderLastPage = async () => {
    const rendered = renderList();
    await waitFor(() => expect(rendered.result.current.list.items).toHaveLength(20));
    act(() => rendered.result.current.list.queryableHelpers.paginationHelpers.handleChangePage(2));
    await waitFor(() => expect(rendered.result.current.list.items[0]).toEqual({ id: 'item-40' }));
    return rendered;
  };

  it('given_thePageLosingItsLastRows_should_moveToTheNewLastPage', async () => {
    // Arrange
    const { result } = await renderLastPage();
    search.mockImplementation(input => Promise.resolve(pageOf(input, 25)));

    // Act
    await act(() => queryClient.invalidateQueries());

    // Assert
    await waitFor(() => expect(result.current.list.searchPaginationInput.page).toBe(1));
    await waitFor(() => expect(result.current.list.items[0]).toEqual({ id: 'item-20' }));
  });

  it('given_aCachedPagePastTheEndThatRefetches_should_waitForItsNewRows', async () => {
    // Arrange: page 2 emptied (back to page 1), then refilled while it sits in the cache
    const { result } = await renderLastPage();
    search.mockImplementation(input => Promise.resolve(pageOf(input, 40)));
    await act(() => queryClient.invalidateQueries());
    await waitFor(() => expect(result.current.list.searchPaginationInput.page).toBe(1));
    search.mockImplementation(input => Promise.resolve(pageOf(input, 45)));
    await act(() => queryClient.invalidateQueries());

    // Act: the cached page 2 still says it is past the end until its refetch lands
    act(() => result.current.list.queryableHelpers.paginationHelpers.handleChangePage(2));

    // Assert
    await waitFor(() => expect(result.current.list.items[0]).toEqual({ id: 'item-40' }));
    expect(result.current.list.searchPaginationInput.page).toBe(2);
  });

  it('given_anEmptyDataset_should_stayOnTheFirstPage', async () => {
    // Arrange
    search.mockImplementation(input => Promise.resolve(pageOf(input, 0)));

    // Act
    const { result } = renderList();
    await waitFor(() => expect(result.current.list.query.isSuccess).toBe(true));

    // Assert: no reset loop
    expect(search).toHaveBeenCalledTimes(1);
    expect(result.current.list.searchPaginationInput.page).toBe(0);
  });

  it('given_aTextSearch_should_queryItFromTheFirstPageAndWriteItToTheUri', async () => {
    // Arrange
    const { result } = renderList();
    await waitFor(() => expect(result.current.list.items).toHaveLength(20));
    act(() => result.current.list.queryableHelpers.paginationHelpers.handleChangePage(1));
    await waitFor(() => expect(result.current.list.items[0]).toEqual({ id: 'item-20' }));

    // Act
    act(() => result.current.list.queryableHelpers.textSearchHelpers.handleTextSearch('web'));

    // Assert
    await waitFor(() => expect(search).toHaveBeenLastCalledWith(expect.objectContaining({
      textSearch: 'web',
      page: 0,
    })));
    await waitFor(() => expect(retrieveFromUri(STORAGE_KEY, result.current.searchParams)).toEqual(expect.objectContaining({
      textSearch: 'web',
      page: 0,
    })));
  });

  it('given_aRowsPerPageChange_should_persistItForTheNextVisit', async () => {
    // Arrange
    const first = renderList();
    await waitFor(() => expect(first.result.current.list.items).toHaveLength(20));
    act(() => first.result.current.list.queryableHelpers.paginationHelpers.handleChangeRowsPerPage(50));
    await waitFor(() => expect(first.result.current.list.items).toHaveLength(45));
    first.unmount();

    // Act
    const next = renderList();

    // Assert
    await waitFor(() => expect(next.result.current.list.items).toHaveLength(45));
    expect(next.result.current.list.searchPaginationInput.size).toBe(50);
  });

  it('given_handWrittenTotals_should_keepTheTotalAndThePageOfTheData', async () => {
    // Arrange: on the last page, where a lower total would step back
    const { result } = await renderLastPage();
    const { paginationHelpers } = result.current.list.queryableHelpers;

    // Act
    act(() => {
      paginationHelpers.handleChangeTotalElements(3);
      paginationHelpers.incrementTotalElements(-5);
    });

    // Assert
    expect(result.current.list.queryableHelpers.paginationHelpers.getTotalElements()).toBe(45);
    expect(result.current.list.searchPaginationInput.page).toBe(2);
  });
});
