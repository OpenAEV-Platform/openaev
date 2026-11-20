import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useOlderVersionsInUse from '../../../../../admin/components/threat_arsenal/hook/useOlderVersionsInUse';
import { anAction } from '../threatArsenalTestUtils';

const { fetchOlderVersionsInUse } = vi.hoisted(() => ({ fetchOlderVersionsInUse: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalOlderVersionsInUse: fetchOlderVersionsInUse }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

const withPayload = anAction();
const builtIn = anAction({
  injector_contract_id: 'built-in',
  action_payload: undefined,
});

describe('useOlderVersionsInUse', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given a page listed should ask once for its payload actions only', async () => {
    // Arrange
    fetchOlderVersionsInUse.mockResolvedValue({ data: ['action-v2'] });
    const { result } = renderHook(() => useOlderVersionsInUse());

    // Act
    act(() => result.current.load([withPayload, builtIn]));

    // Assert
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(true));
    expect(fetchOlderVersionsInUse).toHaveBeenCalledTimes(1);
    expect(fetchOlderVersionsInUse).toHaveBeenCalledWith({ action_ids: ['action-v2'] });
  });

  it('given an action loaded again without injects left behind should drop its flag', async () => {
    // Arrange
    fetchOlderVersionsInUse.mockResolvedValueOnce({ data: ['action-v2'] }).mockResolvedValueOnce({ data: [] });
    const { result } = renderHook(() => useOlderVersionsInUse());
    act(() => result.current.load([withPayload]));
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(true));

    // Act
    act(() => result.current.load([withPayload]));

    // Assert
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(false));
  });

  it('given only actions without a payload should not ask', () => {
    // Arrange
    const { result } = renderHook(() => useOlderVersionsInUse());

    // Act
    act(() => result.current.load([builtIn]));

    // Assert
    expect(fetchOlderVersionsInUse).not.toHaveBeenCalled();
  });
});
