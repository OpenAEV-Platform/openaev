import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import useCatalogHistory from '../../../../components/common/catalog_history/useCatalogHistory';

const history = (id: string) => [{ catalog_history_id: id }];

describe('useCatalogHistory', () => {
  it('given_anId_should_loadItsHistory', async () => {
    // Arrange
    const fetchHistory = vi.fn((id: string) => Promise.resolve({ data: history(id) }));

    // Act
    const { result } = renderHook(() => useCatalogHistory(fetchHistory, 'action-1'));

    // Assert
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toEqual(history('action-1')));
    expect(fetchHistory).toHaveBeenCalledWith('action-1');
  });

  it('given_aNewReloadValue_should_readTheHistoryAgain', async () => {
    // Arrange
    const fetchHistory = vi.fn((id: string) => Promise.resolve({ data: history(id) }));
    const { result, rerender } = renderHook(({ reload }) => useCatalogHistory(fetchHistory, 'scenario-1', reload), { initialProps: { reload: 0 } });
    await waitFor(() => expect(result.current).toEqual(history('scenario-1')));

    // Act
    rerender({ reload: 1 });

    // Assert
    await waitFor(() => expect(fetchHistory).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current).toEqual(history('scenario-1')));
  });

  it('given_anotherIdBeforeTheFirstAnswer_should_keepOnlyTheLastHistory', async () => {
    // Arrange
    const first: { answer?: (value: { data: unknown }) => void } = {};
    const fetchHistory = vi.fn((id: string) => (id === 'action-1'
      ? new Promise<{ data: unknown }>((resolve) => {
          first.answer = resolve;
        })
      : Promise.resolve({ data: history(id) })));
    const { result, rerender } = renderHook(({ id }) => useCatalogHistory(fetchHistory, id), { initialProps: { id: 'action-1' } });

    // Act
    rerender({ id: 'action-2' });
    await waitFor(() => expect(result.current).toEqual(history('action-2')));
    first.answer?.({ data: history('action-1') });
    await Promise.resolve();

    // Assert
    expect(result.current).toEqual(history('action-2'));
  });
});
