import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useCustomization from '../../../../../admin/components/threat_arsenal/customization/useCustomization';
import { anAction } from '../threatArsenalTestUtils';

const { fetchCustomization } = vi.hoisted(() => ({ fetchCustomization: vi.fn() }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalCustomization: fetchCustomization }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

describe('useCustomization', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('given_aCustomizedCopy_should_loadItsCustomization', async () => {
    // Arrange
    const customization = { customization_base_version: '1.0.0' };
    fetchCustomization.mockResolvedValue({ data: customization });

    // Act
    const { result } = renderHook(() => useCustomization(anAction({ action_payload_customized: true })));

    // Assert
    await waitFor(() => expect(result.current).toEqual(customization));
    expect(fetchCustomization).toHaveBeenCalledWith('action-v2');
  });

  it.each([
    ['an action that is not a copy', anAction()],
    ['no action', null],
  ])('given_%s_should_loadNothing', (_, action) => {
    // Act
    const { result } = renderHook(() => useCustomization(action));

    // Assert
    expect(result.current).toBeNull();
    expect(fetchCustomization).not.toHaveBeenCalled();
  });
});
