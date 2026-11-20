import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useVersionUsages from '../../../../../../admin/components/threat_arsenal/versions/usages/useVersionUsages';
import { aUsage, someUsages } from '../../threatArsenalTestUtils';

const { fetchUsages } = vi.hoisted(() => ({ fetchUsages: vi.fn() }));

vi.mock('../../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalUsages: fetchUsages }));

describe('useVersionUsages', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given a version action should load its usages', async () => {
    // Arrange
    const usages = someUsages([aUsage()], 2);
    fetchUsages.mockResolvedValue({ data: usages });

    // Act
    const { result } = renderHook(() => useVersionUsages('action-v1'));

    // Assert
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toEqual(usages));
    expect(fetchUsages).toHaveBeenCalledWith('action-v1');
  });

  it('given no action should load nothing', () => {
    // Act
    const { result } = renderHook(() => useVersionUsages(undefined));

    // Assert
    expect(result.current).toBeNull();
    expect(fetchUsages).not.toHaveBeenCalled();
  });

  it('given another version chosen should drop the answer of the previous one', async () => {
    // Arrange
    let answerFirst: (value: { data: unknown }) => void = () => {};
    fetchUsages
      .mockReturnValueOnce(new Promise((resolve) => {
        answerFirst = resolve;
      }))
      .mockResolvedValueOnce({ data: someUsages([]) });
    const { result, rerender } = renderHook(({ id }) => useVersionUsages(id), { initialProps: { id: 'action-v1' } });

    // Act
    rerender({ id: 'action-v2' });
    await waitFor(() => expect(result.current).toEqual(someUsages([])));
    await act(async () => {
      answerFirst({ data: someUsages([aUsage()]) });
    });

    // Assert
    expect(fetchUsages).toHaveBeenCalledTimes(2);
    expect(result.current).toEqual(someUsages([]));
  });
});
