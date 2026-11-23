import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useActionDetails from '../../../../../admin/components/threat_arsenal/hook/useActionDetails';

const { fetchAction } = vi.hoisted(() => ({ fetchAction: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalAction: fetchAction }));

describe('useActionDetails', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given no action should fetch nothing', () => {
    // Act
    const { result } = renderHook(() => useActionDetails(undefined));

    // Assert
    expect(result.current).toEqual({
      details: null,
      loading: false,
    });
    expect(fetchAction).not.toHaveBeenCalled();
  });

  it('given an action should load its details', async () => {
    // Arrange
    fetchAction.mockResolvedValue({ data: { action_id: 'action-v2' } });

    // Act
    const { result } = renderHook(() => useActionDetails('action-v2'));

    // Assert
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.details).toEqual({ action_id: 'action-v2' }));
    expect(result.current.loading).toBe(false);
    expect(fetchAction).toHaveBeenCalledWith('action-v2');
  });

  it('given the call failing should stop loading without details', async () => {
    // Arrange
    fetchAction.mockRejectedValue(new Error('failed'));

    // Act
    const { result } = renderHook(() => useActionDetails('action-v2'));

    // Assert
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.details).toBeNull();
  });
});
