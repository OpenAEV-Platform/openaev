import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalApprovalSection from '../../../../../admin/components/threat_arsenal/approval/ThreatArsenalApprovalSection';
import { type ThreatArsenalActionFullOutput } from '../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const mockCan = vi.fn();
const mockApprove = vi.fn();
const mockReject = vi.fn();
const mockUsage = vi.fn();
const mockApprovals = vi.fn();
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
  fetchThreatArsenalActionApprovals: (...args: unknown[]) => mockApprovals(...args),
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

// An approved action whose author submitted a new version (Task 5).
const actionWithPendingVersion = {
  ...pendingAction,
  action_approval_status: 'APPROVED',
  action_approval_fingerprint: 'fingerprint-active',
  action_active_version: 1,
  action_active_content: {
    content: 'whoami',
    executor: 'sh',
  },
  action_pending_version: {
    version_id: 'version-2',
    version_number: 2,
    version_status: 'PENDING',
    version_origin: 'UPDATE',
    version_fingerprint: 'fingerprint-2',
    version_author_name: 'Author User',
    version_created_at: '2026-10-09T10:00:00Z',
    version_content: {
      content: 'whoami\nid',
      executor: 'sh',
    },
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
    mockApprovals.mockReset();
    mockApprovals.mockResolvedValue({ data: [] });
    mockDispatch.mockReset();
    mockNotifyError.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  describe('permissions', () => {
    it('hides approve and reject without Approve content and says what happens next', () => {
      // Arrange
      mockCan.mockReturnValue(false);

      // Act
      renderSection(pendingAction);

      // Assert
      expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull();
      expect(screen.queryByRole('button', { name: /Reject/ })).toBeNull();
      expect(screen.getByText('Waiting for an approver. It can\'t be used until it is approved.')).toBeTruthy();
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
      }, false));
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

    it('shows where the payload is used as information, with no usage warning when rejecting (Task 5)', async () => {
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
      expect(await screen.findByText('Reject this payload? It stays blocked until it is edited and approved.')).toBeTruthy();
      expect(mockUsage).toHaveBeenCalledWith('action-1');
    });
  });

  describe('pending version (Task 5)', () => {
    it('shows the pending version and the version in use as two cards, the decision on the pending one', async () => {
      // Arrange
      mockCan.mockReturnValue(true);

      // Act
      renderSection(actionWithPendingVersion);

      // Assert
      // Each version is its own card with its own status: v2 pending, v1 approved and in use.
      const cards = screen.getAllByRole('article');
      expect(cards).toHaveLength(2);
      expect(cards[0].textContent).toContain('v2');
      expect(cards[0].textContent).toContain('Pending approval');
      expect(cards[1].textContent).toContain('v1');
      expect(cards[1].textContent).toContain('Approved');
      expect(cards[1].textContent).toContain('In use');
      // Approve / Reject sit on the pending version's card only.
      expect(within(cards[0]).getByRole('button', { name: /Approve/ })).toBeTruthy();
      expect(within(cards[0]).getByRole('button', { name: /Reject/ })).toBeTruthy();
      expect(within(cards[1]).queryByRole('button', { name: /Approve/ })).toBeNull();
      expect(within(cards[1]).queryByRole('button', { name: /Reject/ })).toBeNull();
      // The changed field as a labelled Before / After, the added line highlighted on the After side.
      const diff = screen.getByLabelText('Command');
      expect(diff.textContent).toContain('Before · v{version}whoami');
      expect(diff.textContent).toContain('After · v{version}whoami\nid');
      expect(within(diff).getByText('id', { selector: 'mark' })).toBeTruthy();
      expect(screen.queryByText('Executor')).toBeNull();
    });

    it('approves the pending version with the fingerprint of that version', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      mockApprove.mockResolvedValue({ data: actionWithPendingVersion });
      renderSection(actionWithPendingVersion);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Approve/ }));
      expect(await screen.findByText('Approve version {number}? It replaces the active version: the next runs use it.')).toBeTruthy();
      const dialogButtons = await screen.findAllByRole('button', { name: 'Approve' });
      fireEvent.click(dialogButtons[dialogButtons.length - 1]);

      // Assert
      await waitFor(() => expect(mockApprove).toHaveBeenCalledWith('action-1', {
        approval_fingerprint: 'fingerprint-2',
        approval_comment: undefined,
      }, false));
    });

    it('rejects the pending version, keeping the active one', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      renderSection(actionWithPendingVersion);

      // Act
      fireEvent.click(screen.getByRole('button', { name: /Reject/ }));

      // Assert
      expect(await screen.findByText('Reject version {number}? The active version stays in use.')).toBeTruthy();
    });

    it('shows the decision of the version in use on its own card, and the approval history', async () => {
      // Arrange
      mockCan.mockReturnValue(true);
      mockApprovals.mockResolvedValue({
        data: [{
          approval_id: 'entry-approved',
          approval_status: 'APPROVED',
          approval_origin: 'CREATE',
          approval_automatic: true,
          approval_actor_name: 'Admin User',
          approval_created_at: '2026-10-07T10:00:00Z',
        }],
      });

      // Act
      renderSection(actionWithPendingVersion);

      // Assert
      expect(await screen.findByText('Auto-approved: {name} holds Approve content')).toBeTruthy();
      const cards = screen.getAllByRole('article');
      expect(cards[0].textContent).not.toContain('Auto-approved');
      expect(screen.getByText('Approval history')).toBeTruthy();
      expect(mockApprovals).toHaveBeenCalledWith('action-1');
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
