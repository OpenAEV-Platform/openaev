import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import useContentHistory from '../../../../components/common/content_history/useContentHistory';
import { type Page } from '../../../../components/common/queryable/Page';
import { type ContentChangeOutput, type SearchPaginationInput } from '../../../../utils/api-types';

const entries = (id: string) => [{ content_change_id: id } as ContentChangeOutput];
const page = (id: string, totalElements = 1) => ({
  data: {
    content: entries(id),
    totalElements,
  } as Page<ContentChangeOutput>,
});
const search = () => vi.fn((id: string, _input: SearchPaginationInput) => Promise.resolve(page(id)));

describe('useContentHistory', () => {
  it('given_anId_should_loadTheFirstPageOfItsHistory', async () => {
    // Arrange
    const searchHistory = search();

    // Act
    const { result } = renderHook(() => useContentHistory(searchHistory, 'action-1'));

    // Assert
    expect(result.current.entries).toBeNull();
    await waitFor(() => expect(result.current.entries).toEqual(entries('action-1')));
    expect(searchHistory).toHaveBeenCalledWith('action-1', {
      page: 0,
      size: 20,
    });
    expect(result.current.pagination.getTotalElements()).toBe(1);
  });

  it('given_anotherPage_should_loadIt', async () => {
    // Arrange
    const searchHistory = vi.fn((id: string, _input: SearchPaginationInput) => Promise.resolve(page(id, 45)));
    const { result } = renderHook(() => useContentHistory(searchHistory, 'action-1'));
    await waitFor(() => expect(result.current.entries).not.toBeNull());

    // Act
    act(() => result.current.pagination.handleChangePage(2));

    // Assert
    await waitFor(() => expect(searchHistory).toHaveBeenLastCalledWith('action-1', {
      page: 2,
      size: 20,
    }));
  });

  it('given_aNewReloadValue_should_readTheHistoryAgain', async () => {
    // Arrange
    const searchHistory = search();
    const { result, rerender } = renderHook(({ reload }) => useContentHistory(searchHistory, 'scenario-1', reload), { initialProps: { reload: 0 } });
    await waitFor(() => expect(result.current.entries).toEqual(entries('scenario-1')));

    // Act
    rerender({ reload: 1 });

    // Assert
    await waitFor(() => expect(searchHistory).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current.entries).toEqual(entries('scenario-1')));
  });

  it('given_anotherIdBeforeTheFirstAnswer_should_keepOnlyTheLastHistory', async () => {
    // Arrange
    const first: { answer?: (value: ReturnType<typeof page>) => void } = {};
    const searchHistory = vi.fn((id: string, _input: SearchPaginationInput) => (id === 'action-1'
      ? new Promise<ReturnType<typeof page>>((resolve) => {
          first.answer = resolve;
        })
      : Promise.resolve(page(id))));
    const { result, rerender } = renderHook(({ id }) => useContentHistory(searchHistory, id), { initialProps: { id: 'action-1' } });

    // Act
    rerender({ id: 'action-2' });
    await waitFor(() => expect(result.current.entries).toEqual(entries('action-2')));
    first.answer?.(page('action-1'));
    await Promise.resolve();

    // Assert
    expect(result.current.entries).toEqual(entries('action-2'));
  });
});
