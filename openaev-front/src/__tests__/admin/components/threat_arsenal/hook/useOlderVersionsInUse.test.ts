import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useOlderVersionsInUse from '../../../../../admin/components/threat_arsenal/hook/useOlderVersionsInUse';
import { type ThreatArsenalAction } from '../../../../../utils/api-types';
import { anAction } from '../threatArsenalTestUtils';

const { fetchOlderVersionsInUse } = vi.hoisted(() => ({ fetchOlderVersionsInUse: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalOlderVersionsInUse: fetchOlderVersionsInUse }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

const withPayload = anAction();
const nextPage = anAction({ injector_contract_id: 'action-v3' });
const builtIn = anAction({
  injector_contract_id: 'built-in',
  action_payload: undefined,
});

const renderListing = (actions: ThreatArsenalAction[]) => renderHook(
  ({ listed }) => useOlderVersionsInUse(listed),
  { initialProps: { listed: actions } },
);

describe('useOlderVersionsInUse', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given a page listed should ask once for its payload actions only', async () => {
    // Arrange
    fetchOlderVersionsInUse.mockResolvedValue({ data: ['action-v2'] });

    // Act
    const { result } = renderListing([withPayload, builtIn]);

    // Assert
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(true));
    expect(fetchOlderVersionsInUse).toHaveBeenCalledTimes(1);
    expect(fetchOlderVersionsInUse).toHaveBeenCalledWith({ action_ids: ['action-v2'] });
  });

  it('given the next page listed should ask for its actions only', async () => {
    // Arrange
    fetchOlderVersionsInUse.mockResolvedValueOnce({ data: ['action-v2'] }).mockResolvedValueOnce({ data: [] });
    const { result, rerender } = renderListing([withPayload]);
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(true));

    // Act
    rerender({ listed: [withPayload, nextPage] });

    // Assert
    await waitFor(() => expect(fetchOlderVersionsInUse).toHaveBeenCalledTimes(2));
    expect(fetchOlderVersionsInUse).toHaveBeenLastCalledWith({ action_ids: ['action-v3'] });
    expect(result.current.isInUse('action-v2')).toBe(true);
  });

  it('given an action loaded again without injects left behind should drop its flag', async () => {
    // Arrange
    fetchOlderVersionsInUse.mockResolvedValueOnce({ data: ['action-v2'] }).mockResolvedValueOnce({ data: [] });
    const { result } = renderListing([withPayload]);
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(true));

    // Act
    act(() => result.current.reload(withPayload));

    // Assert
    await waitFor(() => expect(result.current.isInUse('action-v2')).toBe(false));
  });

  it('given only actions without a payload should not ask', () => {
    // Act
    const { result } = renderListing([builtIn]);
    act(() => result.current.reload(builtIn));

    // Assert
    expect(fetchOlderVersionsInUse).not.toHaveBeenCalled();
  });
});
