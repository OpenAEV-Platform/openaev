import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadRevocationDialog from '../../../../../admin/components/threat_arsenal/revocation/PayloadRevocationDialog';
import { expectLibraryButton } from '../../../../utils/designSystemAssertions';
import { aUsage, aVersion, renderInApp, someUsages } from '../threatArsenalTestUtils';

const mocks = vi.hoisted(() => ({
  fetchUsages: vi.fn(),
  revoke: vi.fn(),
  lift: vi.fn(),
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ fetchThreatArsenalUsages: mocks.fetchUsages }));
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

const renderDialog = (lift = false) => {
  const onClose = vi.fn();
  const onDone = vi.fn();
  renderInApp(
    <PayloadRevocationDialog
      open
      name="Mimikatz"
      lift={lift}
      versions={[current]}
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

describe('PayloadRevocationDialog', () => {
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
    expect(await screen.findByText('Used by 3 injects')).toBeTruthy();
    expect(screen.getByRole('dialog', { name: 'Revoke Mimikatz' })).toBeTruthy();
    expect(screen.getByText('What will fail:')).toBeTruthy();
    expect(screen.getByText('1 scenario · 1 scenario inject · 1 atomic test')).toBeTruthy();
    const link = screen.getByRole('link', { name: /See where it is used/ });
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
    expect(screen.getByText('Used by 0 injects')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('given a reason should revoke the version', async () => {
    // Arrange
    const { onDone } = renderDialog();
    await screen.findByText('Used by 3 injects');
    const revoke = screen.getByRole('button', { name: 'Revoke' }) as HTMLButtonElement;
    expectLibraryButton(revoke, 'revoke button', { variant: 'destructive' });
    expect(revoke.disabled).toBe(true);

    // Act
    fireEvent.change(screen.getByRole('textbox', { name: 'Reason' }), { target: { value: 'Flagged' } });
    fireEvent.click(revoke);

    // Assert
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(current));
    expect(mocks.revoke).toHaveBeenCalledWith('payload-v2', { payload_revocation_reason: 'Flagged' });
  });

  it('given a lift should lift the version without a reason', async () => {
    // Arrange
    const { onDone } = renderDialog(true);
    await screen.findByText('Used by 3 injects');
    expect(screen.getByText('Runs again in:')).toBeTruthy();
    expect(screen.queryByRole('textbox', { name: 'Reason' })).toBeNull();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Lift revocation' }));

    // Assert
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(current));
    expect(mocks.lift).toHaveBeenCalledWith('payload-v2');
  });

  it('given cancel should close the dialog', async () => {
    // Arrange
    const { onClose } = renderDialog();
    await screen.findByText('Used by 3 injects');

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // Assert
    expect(onClose).toHaveBeenCalled();
  });
});
