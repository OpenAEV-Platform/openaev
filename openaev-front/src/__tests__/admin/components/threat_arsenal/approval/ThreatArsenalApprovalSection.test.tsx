import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalApprovalSection from '../../../../../admin/components/threat_arsenal/approval/ThreatArsenalApprovalSection';
import { type ThreatArsenalActionFullOutput } from '../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const mockCan = vi.fn();
const mockApprove = vi.fn();
const mockReject = vi.fn();
const mockUsage = vi.fn();
const { mockDispatch, mockNotifyError } = vi.hoisted(() => ({
  mockDispatch: vi.fn(),
  mockNotifyError: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    nsdt: (value: string) => value,
  }),
}));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: mockCan }) }));

vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../../../actions/Application', () => ({ fetchMe: () => 'FETCH_ME' }));

vi.mock('../../../../../utils/Environment', async (importOriginal) => {
  const original = await importOriginal<typeof EnvironmentModule>();
  return {
    ...original,
    MESSAGING$: {
      ...original.MESSAGING$,
      notifyError: mockNotifyError,
      notifySuccess: vi.fn(),
    },
  };
});

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  approveThreatArsenalAction: (...args: unknown[]) => mockApprove(...args),
  rejectThreatArsenalAction: (...args: unknown[]) => mockReject(...args),
  fetchThreatArsenalActionUsage: (...args: unknown[]) => mockUsage(...args),
}));

// A pending payload version: each version is its own action (#8731).
const pendingAction = {
  action_id: 'action-1',
  action_payload_id: 'payload-1',
  action_payload_version: '1.1.0',
  action_approval_status: 'PENDING',
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
    mockDispatch.mockReset();
    mockNotifyError.mockReset();
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
    it('approves the version shown', async () => {
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
      await waitFor(() => expect(mockApprove).toHaveBeenCalledWith('action-1', { approval_comment: undefined }, false));
      await waitFor(() => expect(onDecided).toHaveBeenCalled());
    });

    it('tells the approver that the injects stay on their version until moved', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      renderSection(pendingAction);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Approve/ }));

      // Assert
      expect(await screen.findByText('Approve version v{version}? It can then be used. Injects keep running their current version: you will be asked whether to move them to this one.')).toBeTruthy();
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
      expect(await screen.findByText('A reason is required to reject a version.')).toBeTruthy();
      expect(mockReject).not.toHaveBeenCalled();
    });

    it('shows where the payload is used as information, with no usage warning when rejecting', async () => {
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
      expect(await screen.findByText('Used in')).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: /Reject/ }));

      // Assert
      expect(await screen.findByText('Reject version v{version}? Injects keep running their current version; the author can save a new version.')).toBeTruthy();
      expect(mockUsage).toHaveBeenCalledWith('action-1');
    });
  });

  describe('superseded version', () => {
    it('offers no decision on a version replaced before any decision', () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection({
        ...pendingAction,
        action_approval_status: 'SUPERSEDED',
      } as ThreatArsenalActionFullOutput);

      // Assert
      expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull();
      expect(screen.getByText('Superseded')).toBeTruthy();
    });

    it('shows the latest decision but no history list', () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection(pendingAction);

      // Assert
      expect(screen.getByText('Last decision')).toBeTruthy();
      expect(screen.queryByText('Version history')).toBeNull();
      expect(screen.queryByText('Approval history')).toBeNull();
    });
  });

  describe('approval rights refresh (#8410)', () => {
    it('refreshes the user capabilities when a pending payload is shown', () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection(pendingAction);

      // Assert
      expect(mockDispatch).toHaveBeenCalledWith('FETCH_ME');
    });

    it('does not refresh them for an approved payload (no decision offered)', () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection({
        ...pendingAction,
        action_approval_status: 'APPROVED',
      } as ThreatArsenalActionFullOutput);

      // Assert
      expect(mockDispatch).not.toHaveBeenCalled();
    });

    it('explains a 403 on approve and refreshes the capabilities', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      mockApprove.mockRejectedValue({ status: 403 });
      const onDecided = vi.fn();
      renderSection(pendingAction, onDecided);
      mockDispatch.mockClear();

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Approve/ }));
      const dialogButtons = await screen.findAllByRole('button', { name: 'Approve' });
      fireEvent.click(dialogButtons[dialogButtons.length - 1]);

      // Assert
      await waitFor(() => expect(mockNotifyError).toHaveBeenCalledWith(
        'You can no longer approve or reject payloads: Approve content was removed from your role.',
      ));
      expect(mockDispatch).toHaveBeenCalledWith('FETCH_ME');
      expect(onDecided).not.toHaveBeenCalled();
    });
  });
});
