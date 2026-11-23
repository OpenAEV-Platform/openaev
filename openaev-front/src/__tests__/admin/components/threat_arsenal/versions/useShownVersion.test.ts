import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import useShownVersion from '../../../../../admin/components/threat_arsenal/versions/useShownVersion';
import { anAction, aVersion } from '../threatArsenalTestUtils';

const latest = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
  payload_is_latest: true,
});
const older = aVersion({ payload_revoked: true });
const action = anAction({ action_payload_version: '2' });

describe('useShownVersion', () => {
  it('given the drawer opened should show the opened action', () => {
    // Act
    const { result } = renderHook(() => useShownVersion(action, [latest, older]));

    // Assert
    expect(result.current.shownId).toBe('payload-v2');
    expect(result.current.installed).toBe(latest);
    expect(result.current.shownAction).toBe(action);
    expect(result.current.notInstalledVersion).toBeUndefined();
  });

  it('given another installed version picked should show its action', () => {
    // Arrange
    const { result } = renderHook(() => useShownVersion(action, [latest, older]));

    // Act
    act(() => result.current.show('payload-v1'));

    // Assert
    expect(result.current.shownAction).toMatchObject({
      injector_contract_id: 'action-v1',
      action_payload: {
        payload_id: 'payload-v1',
        payload_status: undefined,
      },
      action_payload_version: '1',
      action_payload_revoked: true,
    });
  });

  it('given a catalogue version not installed picked should show no action', () => {
    // Arrange
    const { result } = renderHook(() => useShownVersion(action, [latest, older]));

    // Act
    act(() => result.current.show('3'));

    // Assert
    expect(result.current.shownAction).toBeNull();
    expect(result.current.notInstalledVersion).toBe('3');
  });
});
