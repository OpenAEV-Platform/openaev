import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import usePayloadRevocation from '../../../../../admin/components/threat_arsenal/revocation/usePayloadRevocation';
import { anAction, aVersion } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  fetchVersions: vi.fn(),
  canRevoke: true,
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalVersions: mocks.fetchVersions }));
vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => mocks.canRevoke }) }));
vi.mock('../../../../../utils/utils', () => ({ isFeatureEnabled: () => true }));

const current = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
  payload_is_latest: true,
});
const revokedInTenant = aVersion({
  payload_revoked: true,
  payload_revoked_by: 'Jane',
});
const revokedByFiligran = aVersion({
  payload_id: 'payload-v0',
  payload_version: '0',
  payload_revoked: true,
  payload_revoked_by_catalogue: true,
});

const renderRevocation = (menuOpen: boolean) => {
  const onDone = vi.fn();
  const action = anAction();
  const hook = renderHook(({ open }) => usePayloadRevocation(action, open, onDone), { initialProps: { open: menuOpen } });
  return {
    ...hook,
    action,
    onDone,
  };
};

describe('usePayloadRevocation', () => {
  beforeEach(() => {
    mocks.fetchVersions.mockResolvedValue({ data: [current, revokedInTenant, revokedByFiligran] });
  });

  afterEach(() => {
    vi.clearAllMocks();
    mocks.canRevoke = true;
  });

  it('given the menu closed should not load the versions', () => {
    // Act
    const { result } = renderRevocation(false);

    // Assert
    expect(mocks.fetchVersions).not.toHaveBeenCalled();
    expect(result.current.canRevoke).toBe(true);
    expect(result.current.revocable).toEqual([]);
  });

  it('given the menu open should offer the versions to revoke and the ones the tenant can lift', async () => {
    // Act
    const { result } = renderRevocation(true);

    // Assert
    await waitFor(() => expect(result.current.revocable).toEqual([current]));
    expect(result.current.liftable).toEqual([revokedInTenant]);
    expect(mocks.fetchVersions).toHaveBeenCalledWith('action-v2');
  });

  it('given no revoke right should offer nothing', () => {
    // Arrange
    mocks.canRevoke = false;

    // Act
    const { result } = renderRevocation(true);

    // Assert
    expect(result.current.canRevoke).toBe(false);
    expect(result.current.revocable).toEqual([]);
    expect(result.current.liftable).toEqual([]);
    expect(mocks.fetchVersions).not.toHaveBeenCalled();
  });

  it('given the shown version revoked should mark the action revoked and close the dialog', async () => {
    // Arrange
    const { result, action, onDone } = renderRevocation(true);
    await waitFor(() => expect(result.current.revocable).toEqual([current]));
    act(() => result.current.openRevocation('revoke'));
    expect(result.current.revocation).toBe('revoke');

    // Act
    act(() => result.current.revocationDone(current));

    // Assert
    expect(onDone).toHaveBeenCalledWith({
      ...action,
      action_payload_revoked: true,
    });
    expect(result.current.revocation).toBeNull();
  });

  it('given the shown version lifted should mark the action as running again', () => {
    // Arrange
    const { result, action, onDone } = renderRevocation(false);
    act(() => result.current.openRevocation('lift'));

    // Act
    act(() => result.current.revocationDone(current));

    // Assert
    expect(onDone).toHaveBeenCalledWith({
      ...action,
      action_payload_revoked: false,
    });
  });

  it('given an older version revoked should leave the action as it is', () => {
    // Arrange
    const { result, action, onDone } = renderRevocation(false);
    act(() => result.current.openRevocation('revoke'));

    // Act
    act(() => result.current.revocationDone(revokedInTenant));

    // Assert
    expect(onDone).toHaveBeenCalledWith(action);
  });

  it('given the dialog closed should forget the revocation', () => {
    // Arrange
    const { result } = renderRevocation(false);
    act(() => result.current.openRevocation('lift'));

    // Act
    act(() => result.current.closeRevocation());

    // Assert
    expect(result.current.revocation).toBeNull();
  });
});
