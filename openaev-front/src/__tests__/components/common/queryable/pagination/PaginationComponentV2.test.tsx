import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type Page } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { type QueryableHelpers } from '../../../../../components/common/queryable/QueryableHelpers';
import { type SearchPaginationInput } from '../../../../../utils/api-types';

const mocks = vi.hoisted(() => ({ toolbarProps: undefined as Record<string, unknown> | undefined }));

vi.mock('../../../../../components/common/queryable/QueryableToolbar', () => ({
  default: (props: Record<string, unknown>) => {
    mocks.toolbarProps = props;
    return null;
  },
}));

const SORT = {
  empty: true,
  sorted: false,
  unsorted: true,
};

const pageOf = (pageNumber: number, totalPages: number, ids: string[]) => ({
  data: {
    content: ids.map(id => ({ id })),
    totalElements: ids.length,
    totalPages,
    pageable: {
      pageNumber,
      pageSize: 20,
      offset: 0,
      paged: true,
      unpaged: false,
      sort: SORT,
    },
  } as unknown as Page<{ id: string }>,
});

describe('PaginationComponentV2', () => {
  const paginationHelpers = {
    handleChangeTotalElements: vi.fn(),
    handleChangePage: vi.fn(),
  };
  const updateUri = vi.fn();
  const queryableHelpers = {
    paginationHelpers,
    uriHelpers: { updateUri },
  } as unknown as QueryableHelpers;
  const input: SearchPaginationInput = {
    page: 0,
    size: 20,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.toolbarProps = undefined;
  });

  afterEach(() => {
    cleanup();
  });

  it('given_anInput_should_fetchItAndHandTheContentAndTotalToTheCaller', async () => {
    // Arrange
    const fetch = vi.fn(() => Promise.resolve(pageOf(0, 1, ['a', 'b'])));
    const setContent = vi.fn();
    const setLoading = vi.fn();

    // Act
    render(<PaginationComponentV2 fetch={fetch} setContent={setContent} setLoading={setLoading} searchPaginationInput={input} queryableHelpers={queryableHelpers} />);

    // Assert
    await waitFor(() => expect(setContent).toHaveBeenCalledWith([{ id: 'a' }, { id: 'b' }]));
    expect(fetch).toHaveBeenCalledWith(input);
    expect(paginationHelpers.handleChangeTotalElements).toHaveBeenCalledWith(2);
    expect(updateUri).toHaveBeenCalledTimes(1);
    expect(setLoading).toHaveBeenLastCalledWith(false);
  });

  it('given_aPagePastTheEnd_should_restartFromTheFirstPage', async () => {
    // Arrange
    const fetch = vi.fn(() => Promise.resolve(pageOf(3, 2, [])));

    // Act
    render(
      <PaginationComponentV2
        fetch={fetch}
        setContent={vi.fn()}
        searchPaginationInput={{
          ...input,
          page: 3,
        }}
        queryableHelpers={queryableHelpers}
      />,
    );

    // Assert
    await waitFor(() => expect(paginationHelpers.handleChangePage).toHaveBeenCalledWith(0));
  });

  it('given_aReloadCount_should_fetchAgain', async () => {
    // Arrange
    const fetch = vi.fn(() => Promise.resolve(pageOf(0, 1, ['a'])));
    const setContent = vi.fn();
    const list = (reloadContentCount: number) => (
      <PaginationComponentV2 fetch={fetch} setContent={setContent} searchPaginationInput={input} queryableHelpers={queryableHelpers} reloadContentCount={reloadContentCount} />
    );
    const { rerender } = render(list(0));
    await waitFor(() => expect(setContent).toHaveBeenCalledTimes(1));

    // Act
    rerender(list(1));

    // Assert
    await waitFor(() => expect(setContent).toHaveBeenCalledTimes(2));
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('given_anotherScope_should_fetchAgainWithTheSameInput', async () => {
    // Arrange
    const fetch = vi.fn(() => Promise.resolve(pageOf(0, 1, ['a'])));
    const setContent = vi.fn();
    const list = (contextId: string) => (
      <PaginationComponentV2 fetch={fetch} setContent={setContent} searchPaginationInput={input} queryableHelpers={queryableHelpers} contextId={contextId} />
    );
    const { rerender } = render(list('simulation-1'));
    await waitFor(() => expect(setContent).toHaveBeenCalledTimes(1));

    // Act
    rerender(list('simulation-2'));

    // Assert
    await waitFor(() => expect(setContent).toHaveBeenCalledTimes(2));
  });

  it('given_aResponseOvertakenByANewerInput_should_dropIt', async () => {
    // Arrange: the first input answers after the second one
    let answerFirst: (page: { data: Page<{ id: string }> }) => void = () => {};
    const fetch = vi.fn()
      .mockReturnValueOnce(new Promise((resolve) => {
        answerFirst = resolve;
      }))
      .mockReturnValueOnce(Promise.resolve(pageOf(1, 2, ['second'])));
    const setContent = vi.fn();
    const list = (page: number) => (
      <PaginationComponentV2
        fetch={fetch}
        setContent={setContent}
        searchPaginationInput={{
          ...input,
          page,
        }}
        queryableHelpers={queryableHelpers}
      />
    );
    const { rerender } = render(list(0));

    // Act
    rerender(list(1));
    await waitFor(() => expect(setContent).toHaveBeenCalledTimes(1));
    answerFirst(pageOf(0, 2, ['first']));
    await new Promise(resolve => setTimeout(resolve, 0));

    // Assert
    expect(setContent).toHaveBeenCalledTimes(1);
    expect(setContent).toHaveBeenCalledWith([{ id: 'second' }]);
  });

  it('given_itsProps_should_handTheToolbarEverythingButTheFetching', () => {
    // Act
    render(<PaginationComponentV2 fetch={vi.fn(() => new Promise<{ data: Page<{ id: string }> }>(() => {}))} setContent={vi.fn()} searchPaginationInput={input} queryableHelpers={queryableHelpers} entityPrefix="asset" availableFilterNames={['asset_tags']} contextId="ctx" />);

    // Assert
    expect(mocks.toolbarProps).toEqual({
      searchPaginationInput: input,
      queryableHelpers,
      entityPrefix: 'asset',
      availableFilterNames: ['asset_tags'],
      contextId: 'ctx',
    });
  });
});
