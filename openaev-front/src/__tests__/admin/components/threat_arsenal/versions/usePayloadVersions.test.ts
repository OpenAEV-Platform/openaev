import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import usePayloadVersions from '../../../../../admin/components/threat_arsenal/versions/usePayloadVersions';
import { aVersion } from '../threatArsenalTestUtils';

const { fetchVersions } = vi.hoisted(() => ({ fetchVersions: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalVersions: fetchVersions }));

describe('usePayloadVersions', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given load off should fetch nothing', () => {
    // Act
    const { result } = renderHook(() => usePayloadVersions('action-v2', false));

    // Assert
    expect(result.current).toBeNull();
    expect(fetchVersions).not.toHaveBeenCalled();
  });

  it('given load on should fetch the versions of the action', async () => {
    // Arrange
    const versions = [aVersion({ payload_id: 'payload-v2' }), aVersion()];
    fetchVersions.mockResolvedValue({ data: versions });

    // Act
    const { result } = renderHook(() => usePayloadVersions('action-v2', true));

    // Assert
    await waitFor(() => expect(result.current).toEqual(versions));
    expect(fetchVersions).toHaveBeenCalledWith('action-v2');
  });

  it('given load turned off then on again should keep the versions, then fetch them again', async () => {
    // Arrange
    const versions = [aVersion()];
    fetchVersions.mockResolvedValue({ data: versions });
    const { result, rerender } = renderHook(({ load }) => usePayloadVersions('action-v2', load), { initialProps: { load: true } });
    await waitFor(() => expect(result.current).toEqual(versions));

    // Act
    rerender({ load: false });

    // Assert
    expect(result.current).toEqual(versions);

    // Act
    rerender({ load: true });

    // Assert
    await waitFor(() => expect(fetchVersions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current).toEqual(versions));
  });
});
