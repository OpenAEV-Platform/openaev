import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalApprovalSection from '../../../../../admin/components/threat_arsenal/approval/ThreatArsenalApprovalSection';
import { type ThreatArsenalActionFullOutput } from '../../../../../utils/api-types';

const mockCan = vi.fn();
const mockApprove = vi.fn();
const mockReject = vi.fn();
const mockUsage = vi.fn();

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    nsdt: (value: string) => value,
  }),
}));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: mockCan }) }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  approveThreatArsenalAction: (...args: unknown[]) => mockApprove(...args),
  rejectThreatArsenalAction: (...args: unknown[]) => mockReject(...args),
  fetchThreatArsenalActionApprovals: () => Promise.resolve({ data: [] }),
  fetchThreatArsenalActionUsage: (...args: unknown[]) => mockUsage(...args),
}));

const pendingAction = {
  action_id: 'action-1',
  action_approval_status: 'PENDING',
  action_approval_fingerprint: 'fingerprint-1',
  action_approval_latest: {
    approval_id: 'entry-1',
    approval_status: 'PENDING',
    approval_origin: 'CREATE',
    approval_automatic: true,
    approval_actor_name: 'Author User',
    approval_created_at: '2026-10-07T10:00:00Z',
  },
} as unknown as ThreatArsenalActionFullOutput;

const renderSection = (action: ThreatArsenalActionFullOutput, onDecided = vi.fn()) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <ThreatArsenalApprovalSection action={action} onDecided={onDecided} />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('ThreatArsenalApprovalSection', () => {
  beforeEach(() => {
    mockCan.mockReset();
    mockApprove.mockReset();
    mockReject.mockReset();
    mockUsage.mockReset();
    mockUsage.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    cleanup();
  });

  describe('permissions', () => {
    it('disables approve and reject without Approve content', () => {
      // Arrange
      mockCan.mockReturnValue(false);

      // Act
      renderSection(pendingAction);

      // Assert
      expect((screen.getByRole('button', { name: /Approve/ }) as HTMLButtonElement).disabled).toBe(true);
      expect((screen.getByRole('button', { name: /Reject/ }) as HTMLButtonElement).disabled).toBe(true);
      expect(mockCan).toHaveBeenCalledWith('APPROVE', 'THREAT_ARSENALS');
    });

    it('enables approve and reject for an approver', () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection(pendingAction);

      // Assert
      expect((screen.getByRole('button', { name: /Approve/ }) as HTMLButtonElement).disabled).toBe(false);
      expect((screen.getByRole('button', { name: /Reject/ }) as HTMLButtonElement).disabled).toBe(false);
    });

    it('offers no decision on an approved action', () => {
      // Arrange
      mockCan.mockReturnValue(true);
      const approved = {
        ...pendingAction,
        action_approval_status: 'APPROVED',
      } as ThreatArsenalActionFullOutput;

      // Act
      renderSection(approved);

      // Assert
      expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull();
      expect(screen.getByText('Approved')).toBeTruthy();
    });
  });

  describe('decisions', () => {
    it('approves with the fingerprint of the shown content', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      mockApprove.mockResolvedValue({
        data: {
          ...pendingAction,
          action_approval_status: 'APPROVED',
        },
      });
      const onDecided = vi.fn();
      renderSection(pendingAction, onDecided);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Approve/ }));
      const dialogButtons = await screen.findAllByRole('button', { name: 'Approve' });
      fireEvent.click(dialogButtons[dialogButtons.length - 1]);

      // Assert
      await waitFor(() => expect(mockApprove).toHaveBeenCalledWith('action-1', {
        approval_fingerprint: 'fingerprint-1',
        approval_comment: undefined,
      }));
      await waitFor(() => expect(onDecided).toHaveBeenCalled());
    });

    it('does not reject without a reason', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      renderSection(pendingAction);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Reject/ }));
      const dialogButtons = await screen.findAllByRole('button', { name: 'Reject' });
      fireEvent.click(dialogButtons[dialogButtons.length - 1]);

      // Assert
      expect(await screen.findByText('A reason is required to reject a payload.')).toBeTruthy();
      expect(mockReject).not.toHaveBeenCalled();
    });

    it('warns that rejecting blocks the launch of what uses the payload (US2.4)', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      mockUsage.mockResolvedValue({
        data: {
          usage_atomic_testings_count: 2,
          usage_scenarios_count: 1,
          usage_simulations_count: 0,
        },
      });
      renderSection(pendingAction);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Reject/ }));

      // Assert
      expect(await screen.findByText(/Rejecting it blocks their launch\./)).toBeTruthy();
      expect(mockUsage).toHaveBeenCalledWith('action-1');
    });

    it('shows no usage warning when the payload is not used', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      renderSection(pendingAction);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Reject/ }));
      await screen.findAllByRole('button', { name: 'Reject' });

      // Assert
      await waitFor(() => expect(mockUsage).toHaveBeenCalled());
      expect(screen.queryByText(/Rejecting it blocks their launch\./)).toBeNull();
    });
  });
});
