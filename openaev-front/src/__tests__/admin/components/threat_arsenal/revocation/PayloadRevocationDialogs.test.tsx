import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadLiftDialog from '../../../../../admin/components/threat_arsenal/revocation/PayloadLiftDialog';
import PayloadRevokeDialog from '../../../../../admin/components/threat_arsenal/revocation/PayloadRevokeDialog';
import { expectLibraryButton } from '../../../../utils/designSystemAssertions';
import { aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  fetchUsages: vi.fn(),
  move: vi.fn(),
  revoke: vi.fn(),
  lift: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  fetchThreatArsenalUsages: mocks.fetchUsages,
  moveThreatArsenalUsages: mocks.move,
}));
vi.mock('../../../../../actions/payloads/payload-revocation-actions', () => ({
  revokePayload: mocks.revoke,
  liftPayloadRevocation: mocks.lift,
}));

const current = aVersion({
  payload_id: 'payload-v2',
  payload_action_id: 'action-v2',
  payload_version: '2',
  payload_is_latest: true,
});

const older = aVersion({
  payload_id: 'payload-v1',
  payload_action_id: 'action-v1',
  payload_version: '1',
});

const renderDialog = (lift = false, versions = [current]) => {
  const onClose = vi.fn();
  const onDone = vi.fn();
  const Dialog = lift ? PayloadLiftDialog : PayloadRevokeDialog;
  renderInApp(
    <Dialog
      open
      name="Mimikatz"
      versions={versions}
      currentPayloadId="payload-v2"
      actionId="card-action"
      onClose={onClose}
      onDone={onDone}
    />,
  );
  return {
    onClose,
    onDone,
  };
};

describe('PayloadRevokeDialog and PayloadLiftDialog', () => {
  beforeEach(() => {
    mocks.fetchUsages.mockResolvedValue({
      data: someUsages([
        aUsage(),
        aUsage({
          usage_id: 'usage-2',
          usage_type: 'atomic_testing',
          usage_scenario_id: undefined,
        }),
      ], 1),
    });
    mocks.move.mockResolvedValue({
      data: {
        moved_ids: ['usage-1', 'usage-2'],
        refused: [],
      },
    });
    mocks.revoke.mockResolvedValue({});
    mocks.lift.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given a version in use should say what will fail and link to where it is used', async () => {
    // Act
    renderDialog();

    // Assert
    expect(await screen.findByText('Revoked versions don\'t run. What uses v2 will fail:')).toBeTruthy();
    expect(screen.getByRole('dialog', { name: 'Revoke a version' })).toBeTruthy();
    expect(screen.getByText('Mimikatz')).toBeTruthy();
    expect(screen.getByText('1 scenario · 1 scenario inject · 1 atomic test')).toBeTruthy();
    const link = screen.getByRole('link', { name: /See where/ });
    expect(link.getAttribute('href')).toBe('/admin/threat-arsenal?action=card-action&tab=versions');
    expect(mocks.fetchUsages).toHaveBeenCalledWith('action-v2');
  });

  it('given a version nothing uses should say nothing fails', async () => {
    // Arrange
    mocks.fetchUsages.mockResolvedValue({ data: someUsages([]) });

    // Act
    renderDialog();

    // Assert
    expect(await screen.findByText('Nothing fails: v2 isn\'t used.')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('given a version nothing uses should say what keeps running on the others', async () => {
    // Arrange
    mocks.fetchUsages.mockImplementation((actionId: string) => Promise.resolve({ data: actionId === 'action-v2' ? someUsages([]) : someUsages([aUsage(), aUsage({ usage_id: 'usage-2' })]) }));

    // Act
    renderDialog(false, [current, older]);

    // Assert
    expect(await screen.findByText('Nothing fails: v2 isn\'t used.')).toBeTruthy();
    expect(await screen.findByText('2 injects keep running on other versions.')).toBeTruthy();
  });

  it('given a reason should revoke the version', async () => {
    // Arrange
    const { onDone } = renderDialog();
    await screen.findByText('1 scenario · 1 scenario inject · 1 atomic test');
    const revoke = screen.getByRole('button', { name: 'Revoke' }) as HTMLButtonElement;
    expectLibraryButton(revoke, 'revoke button', { variant: 'destructive' });
    expect(revoke.disabled).toBe(true);

    // Act
    fireEvent.change(screen.getByRole('textbox', { name: /Reason/ }), { target: { value: 'Flagged' } });
    fireEvent.click(revoke);

    // Assert
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(current));
    expect(mocks.revoke).toHaveBeenCalledWith('payload-v2', { payload_revocation_reason: 'Flagged' });
  });

  it('given another version should move the injects first and say so on the button', async () => {
    // Arrange
    const { onDone } = renderDialog(false, [current, older]);
    await screen.findByText('1 scenario · 1 scenario inject · 1 atomic test');
    fireEvent.change(screen.getByRole('textbox', { name: /Reason/ }), { target: { value: 'Flagged' } });

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Move 2 injects and revoke' }));

    // Assert
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(current));
    expect(mocks.move).toHaveBeenCalledWith('action-v2', expect.objectContaining({ target_payload_id: 'payload-v1' }));
    expect(mocks.move.mock.invocationCallOrder[0]).toBeLessThan(mocks.revoke.mock.invocationCallOrder[0]);
  });

  it('given the injects still loading should not let the user revoke', async () => {
    // Arrange
    mocks.fetchUsages.mockReturnValue(new Promise(() => {}));

    // Act
    renderDialog(false, [current, older]);
    fireEvent.change(screen.getByRole('textbox', { name: /Reason/ }), { target: { value: 'Flagged' } });

    // Assert
    expect((screen.getByRole('button', { name: 'Revoke' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('given a lift should lift the version without a reason', async () => {
    // Arrange
    const { onDone } = renderDialog(true);
    expect(await screen.findByText('v2 runs again in this tenant.')).toBeTruthy();
    expect(screen.getByRole('dialog', { name: 'Lift a revocation' })).toBeTruthy();
    expect(screen.queryByRole('textbox', { name: /Reason/ })).toBeNull();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Lift revocation' }));

    // Assert
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(current));
    expect(mocks.lift).toHaveBeenCalledWith('payload-v2');
  });

  it('given cancel should close the dialog', async () => {
    // Arrange
    const { onClose } = renderDialog();
    await screen.findByText('1 scenario · 1 scenario inject · 1 atomic test');

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // Assert
    expect(onClose).toHaveBeenCalled();
  });
});
