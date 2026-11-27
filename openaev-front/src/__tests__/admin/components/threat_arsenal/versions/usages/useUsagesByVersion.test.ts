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

const render = (versions: PayloadVersionOutput[], key = 0) => renderHook(
  ({ asked, reloadKey }: {
    asked: PayloadVersionOutput[];
    reloadKey: number;
  }) => useUsagesByVersion(asked, reloadKey),
  {
    initialProps: {
      asked: versions,
      reloadKey: key,
    },
  },
);

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
    const { result } = render([v1, v2]);

    // Assert
    expect(result.current).toEqual({});
    await waitFor(() => expect(result.current).toEqual({
      'payload-v1': usagesV1,
      'payload-v2': usagesV2,
    }));
  });

  it('given no versions should load nothing', () => {
    // Act
    const { result } = render([]);

    // Assert
    expect(result.current).toEqual({});
    expect(fetchUsages).not.toHaveBeenCalled();
  });

  it('given a failed call should show no usage instead of loading forever', async () => {
    // Arrange
    fetchUsages.mockRejectedValue(new Error('down'));

    // Act
    const { result } = render([v1]);

    // Assert
    await waitFor(() => expect(result.current).toEqual({ 'payload-v1': { payload_usages: [] } }));
  });

  it('given another version asked should load only that one and keep the first', async () => {
    // Arrange
    fetchUsages.mockImplementation((actionId: string) => Promise.resolve({ data: someUsages(actionId === 'action-v1' ? [aUsage()] : []) }));
    const { result, rerender } = render([v1]);
    await waitFor(() => expect(result.current['payload-v1']).toBeDefined());

    // Act
    rerender({
      asked: [{ ...v1 }, v2],
      reloadKey: 0,
    });

    // Assert
    await waitFor(() => expect(result.current['payload-v2']).toBeDefined());
    expect(result.current['payload-v1']).toEqual(someUsages([aUsage()]));
    expect(fetchUsages).toHaveBeenCalledTimes(2);
  });

  it('given a new reload key should load again', async () => {
    // Arrange
    fetchUsages.mockResolvedValue({ data: someUsages([]) });
    const { result, rerender } = render([v1]);
    await waitFor(() => expect(result.current['payload-v1']).toBeDefined());

    // Act
    rerender({
      asked: [v1],
      reloadKey: 1,
    });

    // Assert
    await waitFor(() => expect(fetchUsages).toHaveBeenCalledTimes(2));
  });

  it('given a version no longer asked should drop it and load it again when asked again', async () => {
    // Arrange
    fetchUsages.mockResolvedValue({ data: someUsages([]) });
    const { result, rerender } = render([v1]);
    await waitFor(() => expect(result.current['payload-v1']).toBeDefined());

    // Act
    rerender({
      asked: [],
      reloadKey: 0,
    });
    await waitFor(() => expect(result.current).toEqual({}));
    rerender({
      asked: [v1],
      reloadKey: 0,
    });

    // Assert
    await waitFor(() => expect(result.current['payload-v1']).toBeDefined());
    expect(fetchUsages).toHaveBeenCalledTimes(2);
  });

  it('given a version dropped before its answer should ignore that answer', async () => {
    // Arrange
    let answerFirst: (value: { data: unknown }) => void = () => {};
    fetchUsages
      .mockReturnValueOnce(new Promise((resolve) => {
        answerFirst = resolve;
      }))
      .mockResolvedValueOnce({ data: someUsages([]) });
    const { result, rerender } = render([v1]);

    // Act
    rerender({
      asked: [v2],
      reloadKey: 0,
    });
    await waitFor(() => expect(result.current).toEqual({ 'payload-v2': someUsages([]) }));
    await act(async () => {
      answerFirst({ data: someUsages([aUsage()]) });
    });

    // Assert
    expect(fetchUsages).toHaveBeenCalledTimes(2);
    expect(result.current).toEqual({ 'payload-v2': someUsages([]) });
  });
});
