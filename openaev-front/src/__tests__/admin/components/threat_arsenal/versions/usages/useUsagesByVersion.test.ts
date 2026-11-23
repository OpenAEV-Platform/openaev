import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useUsagesByVersion from '../../../../../../admin/components/threat_arsenal/versions/usages/useUsagesByVersion';
import { type PayloadVersionOutput } from '../../../../../../utils/api-types';
import { aUsage, aVersion, someUsages } from '../../threatArsenalTestUtils';

const { fetchUsages } = vi.hoisted(() => ({ fetchUsages: vi.fn() }));

vi.mock('../../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalUsages: fetchUsages }));

const v1 = aVersion();
const v2 = aVersion({
  payload_action_id: 'action-v2',
  payload_id: 'payload-v2',
  payload_version: '2',
});

describe('useUsagesByVersion', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given versions should load the usages of each one by payload id', async () => {
    // Arrange
    const usagesV1 = someUsages([aUsage()], 2);
    const usagesV2 = someUsages([]);
    fetchUsages.mockImplementation((actionId: string) => Promise.resolve({ data: actionId === 'action-v1' ? usagesV1 : usagesV2 }));

    // Act
    const { result } = renderHook(() => useUsagesByVersion([v1, v2]));

    // Assert
    expect(result.current).toBeNull();
    await waitFor(() => expect(result.current).toEqual({
      'payload-v1': usagesV1,
      'payload-v2': usagesV2,
    }));
  });

  it('given no versions should load nothing', () => {
    // Act
    const { result } = renderHook(() => useUsagesByVersion(null));

    // Assert
    expect(result.current).toBeNull();
    expect(fetchUsages).not.toHaveBeenCalled();
  });

  it('given a failed call should show no usage instead of loading forever', async () => {
    // Arrange
    fetchUsages.mockRejectedValue(new Error('down'));

    // Act
    const { result } = renderHook(() => useUsagesByVersion([v1]));

    // Assert
    await waitFor(() => expect(result.current).toEqual({ 'payload-v1': { payload_usages: [] } }));
  });

  it('given the same versions in a new list should not load again', async () => {
    // Arrange
    fetchUsages.mockResolvedValue({ data: someUsages([]) });
    const { result, rerender } = renderHook(({ versions }) => useUsagesByVersion(versions), { initialProps: { versions: [v1] } });
    await waitFor(() => expect(result.current).not.toBeNull());

    // Act
    rerender({ versions: [{ ...v1 }] });

    // Assert
    expect(fetchUsages).toHaveBeenCalledTimes(1);
  });

  it('given a new reload key should load again', async () => {
    // Arrange
    fetchUsages.mockResolvedValue({ data: someUsages([]) });
    const { result, rerender } = renderHook(({ key }) => useUsagesByVersion([v1], key), { initialProps: { key: 0 } });
    await waitFor(() => expect(result.current).not.toBeNull());

    // Act
    rerender({ key: 1 });

    // Assert
    await waitFor(() => expect(fetchUsages).toHaveBeenCalledTimes(2));
  });

  it('given other versions chosen should drop the answer for the previous ones', async () => {
    // Arrange
    let answerFirst: (value: { data: unknown }) => void = () => {};
    fetchUsages
      .mockReturnValueOnce(new Promise((resolve) => {
        answerFirst = resolve;
      }))
      .mockResolvedValueOnce({ data: someUsages([]) });
    const { result, rerender } = renderHook(
      ({ versions }: { versions: PayloadVersionOutput[] }) => useUsagesByVersion(versions),
      { initialProps: { versions: [v1] } },
    );

    // Act
    rerender({ versions: [v2] });
    await waitFor(() => expect(result.current).toEqual({ 'payload-v2': someUsages([]) }));
    await act(async () => {
      answerFirst({ data: someUsages([aUsage()]) });
    });

    // Assert
    expect(fetchUsages).toHaveBeenCalledTimes(2);
    expect(result.current).toEqual({ 'payload-v2': someUsages([]) });
  });
});
