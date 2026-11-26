import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import VersionApprovalPanel from '../../../../../admin/components/threat_arsenal/approval/VersionApprovalPanel';
import { type PayloadVersionOutput } from '../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../utils/Environment';

const { mockCan, mockApprove, mockReject, mockDispatch, mockNotifyError, mockErrorHandler } = vi.hoisted(() => ({
  mockCan: vi.fn(),
  mockApprove: vi.fn(),
  mockReject: vi.fn(),
  mockDispatch: vi.fn(),
  mockNotifyError: vi.fn(),
  mockErrorHandler: vi.fn(),
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

vi.mock('../../../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: mockErrorHandler }));

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
}));

vi.mock('../../../../../admin/components/threat_arsenal/approval/useVersionChanges', () => ({ default: () => [] }));

vi.mock('../../../../../admin/components/threat_arsenal/approval/PayloadVersionComparison', () => ({ default: () => null }));

const REMOVED = 'You can no longer approve or reject payloads: Approve content was removed from your role.';

// A version waiting for approval: each version is its own action (#8731).
const pendingVersion = {
  payload_id: 'payload-2',
  payload_action_id: 'action-2',
  payload_version: '1.1.0',
  payload_approval_status: 'PENDING',
} as unknown as PayloadVersionOutput;

const renderPanel = (onDecided = vi.fn(), version = pendingVersion) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <VersionApprovalPanel version={version} onDecided={onDecided} />
    </TooltipProvider>
  </ThemeProvider>,
);

const confirmApprove = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Approve/ }));
  const buttons = await screen.findAllByRole('button', { name: 'Approve' });
  fireEvent.click(buttons[buttons.length - 1]);
};

describe('VersionApprovalPanel: approval rights refresh (#8410)', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given a version waiting for approval should reload the user capabilities', () => {
    // Arrange / Act
    renderPanel();

    // Assert
    expect(mockDispatch).toHaveBeenCalledWith('FETCH_ME');
  });

  it('given Approve content removed meanwhile should explain the 403 and reload the capabilities', async () => {
    // Arrange
    mockApprove.mockRejectedValue({ status: 403 });
    const onDecided = vi.fn();
    renderPanel(onDecided);
    mockDispatch.mockClear();

    // Act
    await confirmApprove();

    // Assert
    await waitFor(() => expect(mockNotifyError).toHaveBeenCalledWith(REMOVED));
    expect(mockDispatch).toHaveBeenCalledWith('FETCH_ME');
    expect(mockErrorHandler).not.toHaveBeenCalled();
    expect(onDecided).not.toHaveBeenCalled();
  });

  it('given another error should show the usual error', async () => {
    // Arrange
    const error = {
      status: 400,
      message: 'Bad request',
    };
    mockApprove.mockRejectedValue(error);
    renderPanel();

    // Act
    await confirmApprove();

    // Assert
    await waitFor(() => expect(mockErrorHandler).toHaveBeenCalledWith(error));
    expect(mockNotifyError).not.toHaveBeenCalledWith(REMOVED);
  });

  it('given no Approve content should offer no decision', () => {
    // Arrange
    mockCan.mockReturnValue(false);

    // Act
    renderPanel();

    // Assert
    expect(screen.queryByRole('button', { name: /Approve/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Reject/ })).toBeNull();
  });
});

describe('VersionApprovalPanel: who last edited the version (US1.2 AC11)', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given a version last saved by someone else than its author should name the last editor', () => {
    // Arrange
    const version = {
      ...pendingVersion,
      payload_last_modified_by_name: 'Fixer User',
      payload_created_at: '2026-11-01T10:00:00Z',
      payload_last_edited_at: '2026-11-20T10:00:00Z',
    } as unknown as PayloadVersionOutput;

    // Act
    renderPanel(vi.fn(), version);

    // Assert
    expect(screen.getByText('Fixer User')).toBeTruthy();
    expect(screen.getByText(/Last edited · 2026-11-20T10:00:00Z/)).toBeTruthy();
    expect(screen.queryByText(/Submitted/)).toBeNull();
  });

  it('given nobody recorded as last editor should say Unknown', () => {
    // Arrange / Act
    renderPanel();

    // Assert
    expect(screen.getByText(/Last edited by \{name\}|Last edited by Unknown/)).toBeTruthy();
  });
});
