import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type InternalAxiosRequestConfig } from 'axios';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalActionPopover from '../../../../admin/components/threat_arsenal/ThreatArsenalActionPopover';
import type * as NetworkModule from '../../../../network';
import { setNotifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';

// The HTTP layer is real (Action.ts helpers and the network.ts interceptor, which rejects with
// `{ status, ...body }`): only the transport is stubbed, so the test sees the error shape the app
// really gets.
const { requests, responses, submitErrors, permissions, versionList } = vi.hoisted(() => ({
  permissions: { canApprove: true },
  versionList: [] as unknown[],
  requests: [] as InternalAxiosRequestConfig[],
  responses: [] as {
    status: number;
    data: unknown;
  }[],
  submitErrors: [] as unknown[],
}));

vi.mock('../../../../network', async (importOriginal) => {
  const original = await importOriginal<typeof NetworkModule>();
  const { AxiosError } = await import('axios');
  return {
    ...original,
    api: (...args: Parameters<typeof original.api>) => {
      const instance = original.api(...args);
      instance.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
        requests.push(config);
        // The version list the popover and its dialogs load: none unless a test sets some.
        if (config.url?.endsWith('/versions')) {
          return {
            status: 200,
            data: versionList,
            statusText: '',
            headers: {},
            config,
          };
        }
        const next = responses.shift() ?? {
          status: 200,
          data: {},
        };
        const response = {
          ...next,
          statusText: '',
          headers: {},
          config,
        };
        if (next.status >= 400) {
          // An axios HTTP error, as the real transport raises it: the interceptor then rejects
          // with `{ status, ...body }`.
          return Promise.reject(new AxiosError(`Request failed with status code ${next.status}`, AxiosError.ERR_BAD_RESPONSE, config, null, response));
        }
        return response;
      };
      return instance;
    },
  };
});

vi.mock('../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({
    t: (value: string, values?: Record<string, unknown>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, String(val)), value),
    tPick: () => 'Whoami',
  }),
}));

vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: (action: string) => action !== 'APPROVE' || permissions.canApprove }) }));

vi.mock('../../../../components/common/Drawer', () => ({
  default: ({ open, children }: {
    open: boolean;
    children: ReactNode;
  }) => (open ? <div data-testid="edit-drawer">{children}</div> : null),
}));

vi.mock('../../../../admin/components/threat_arsenal/utils/SnapshotRemediationProvider', () => ({ default: ({ children }: { children: ReactNode }) => <>{children}</> }));

// Fixture copy bound to a constant: `i18next/no-literal-string` cannot tell it from UI copy.
const SUBMIT_EDIT = 'Submit edit';

// The form itself is not under test: a button submits a command edit like the real form does
// (an exception from onSubmit is what the real form surfaced as an uncaught error).
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalActionForm', () => ({
  default: ({ onSubmit, initialValues }: {
    onSubmit: (data: object) => Promise<void>;
    initialValues: { command_content?: string };
  }) => (
    <>
      <output data-testid="initial-command">{initialValues.command_content}</output>
      <button
        type="button"
        onClick={() => {
          onSubmit({
            action_name: 'Whoami',
            command_content: 'whoami /all',
          }).catch((error: unknown) => submitErrors.push(error));
        }}
      >
        {SUBMIT_EDIT}
      </button>
    </>
  ),
}));

const fetchedAction = {
  status: 200,
  data: {
    action_id: 'action-1',
    action_labels: { en: 'Whoami' },
    action_type: 'Command',
    command_content: 'whoami',
    action_approval_status: 'APPROVED',
  },
};
// A save that added version 1.1.0, waiting for approval (an author without Approve content).
const savedPendingVersion = {
  status: 200,
  data: {
    injector_contract_id: 'action-2',
    action_previous_version_id: 'action-1',
    action_previous_version_usages: 0,
    action_payload: {
      payload_id: 'payload-2',
      payload_version: '1.1.0',
      payload_approval_status: 'PENDING',
    },
  },
};
const updatedAction = {
  status: 200,
  data: {
    action_id: 'action-1',
    action_name: 'Whoami',
  },
};

const puts = () => requests.filter(request => request.method === 'put');
const notifyError = vi.fn();

const renderPopover = (onUpdate = vi.fn()) => {
  render(
    <MemoryRouter>
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <ThreatArsenalActionPopover actionId="action-1" payloadId="payload-1" name="Whoami" onUpdate={onUpdate} />
        </TooltipProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
  return onUpdate;
};

const openEdit = async () => {
  fireEvent.click(screen.getByRole('button', { name: 'More actions' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Update' }));
  await screen.findByRole('button', { name: SUBMIT_EDIT });
};

const submitEdit = async () => {
  await openEdit();
  fireEvent.click(screen.getByRole('button', { name: SUBMIT_EDIT }));
};

const NOTE_FOR_AUTHOR = 'Changes to what this action runs create a new version that waits for approval. Injects keep running their current version until an approver approves the new one.';

describe('ThreatArsenalActionPopover: saving an action (payload versions and approval)', () => {
  beforeEach(() => {
    requests.length = 0;
    responses.length = 0;
    submitErrors.length = 0;
    permissions.canApprove = true;
    notifyError.mockReset();
    setNotifyErrorHandler(notifyError);
    document.cookie = 'XSRF-TOKEN=test-token';
  });

  afterEach(() => {
    cleanup();
  });

  it('saves with a single update, without any approval impact check, and closes', async () => {
    // Arrange
    responses.push(fetchedAction, updatedAction);
    const onUpdate = renderPopover();

    // Act
    await submitEdit();

    // Assert
    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(updatedAction.data));
    expect(puts()).toHaveLength(1);
    expect(puts()[0].url).toMatch(/\/threat_arsenals\/action-1$/);
    expect(puts()[0].params).toBeUndefined();
    await waitFor(() => expect(screen.queryByTestId('edit-drawer')).toBeNull());
    expect(notifyError).not.toHaveBeenCalled();
  });

  it('tells an author that a saved version waits for approval before injects use it', async () => {
    // Arrange
    permissions.canApprove = false;
    responses.push(fetchedAction);
    renderPopover();

    // Act
    await openEdit();

    // Assert
    expect(screen.getByTestId('initial-command').textContent).toBe('whoami');
    expect(screen.getByText(NOTE_FOR_AUTHOR)).toBeTruthy();
  });

  it('shows no note to an approver: their version is approved at once', async () => {
    // Arrange
    responses.push(fetchedAction);
    renderPopover();

    // Act
    await openEdit();

    // Assert
    expect(screen.queryByText(NOTE_FOR_AUTHOR)).toBeNull();
  });

  it('does not ask the author to move the injects of a version waiting for approval', async () => {
    // Arrange
    permissions.canApprove = false;
    responses.push(fetchedAction, savedPendingVersion);
    const onUpdate = renderPopover();

    // Act
    await submitEdit();

    // Assert
    await waitFor(() => expect(onUpdate).toHaveBeenCalled());
    expect(screen.queryByText('New version v{version}')).toBeNull();
    expect(requests.some(request => request.url?.endsWith('/change-version'))).toBe(false);
  });
});

// v1.1.0 is the version shown (approved, used by 2 injects); v1.0.0 is an older approved version.
const approvedVersions = [
  {
    payload_id: 'payload-1',
    payload_action_id: 'action-1',
    payload_version: '1.1.0',
    payload_is_latest: true,
    payload_installed: true,
    payload_revoked: false,
    payload_usage_count: 2,
    payload_approval_status: 'APPROVED',
  },
  {
    payload_id: 'payload-0',
    payload_action_id: 'action-0',
    payload_version: '1.0.0',
    payload_is_latest: false,
    payload_installed: true,
    payload_revoked: false,
    payload_usage_count: 0,
    payload_approval_status: 'APPROVED',
  },
];

const posts = () => requests.filter(request => request.method === 'post');

const openMenu = () => fireEvent.click(screen.getByRole('button', { name: 'More actions' }));

describe('ThreatArsenalActionPopover: taking an approval back', () => {
  beforeEach(() => {
    requests.length = 0;
    responses.length = 0;
    versionList.length = 0;
    versionList.push(...approvedVersions);
    permissions.canApprove = true;
    notifyError.mockReset();
    setNotifyErrorHandler(notifyError);
    document.cookie = 'XSRF-TOKEN=test-token';
  });

  afterEach(() => {
    cleanup();
  });

  it('offers Reject and Move to pending approval to an approver, next to Revoke', async () => {
    // Arrange
    renderPopover();

    // Act
    openMenu();

    // Assert
    expect(await screen.findByRole('menuitem', { name: 'Reject' })).toBeTruthy();
    expect(screen.getByRole('menuitem', { name: 'Move to pending approval' })).toBeTruthy();
    expect(screen.getByRole('menuitem', { name: 'Revoke' })).toBeTruthy();
  });

  it('shows neither to a user without Approve content', async () => {
    // Arrange
    permissions.canApprove = false;
    renderPopover();

    // Act
    openMenu();

    // Assert
    expect(await screen.findByRole('menuitem', { name: 'Revoke' })).toBeTruthy();
    expect(screen.queryByRole('menuitem', { name: 'Reject' })).toBeNull();
    expect(screen.queryByRole('menuitem', { name: 'Move to pending approval' })).toBeNull();
  });

  it('rejects with a required reason, then moves the injects to the chosen approved version', async () => {
    // Arrange
    responses.push(
      {
        status: 200,
        data: { action_approval_status: 'REJECTED' },
      },
      {
        status: 200,
        data: [{
          usage_id: 'inject-1',
          usage_type: 'inject',
        }, {
          usage_id: 'step-1',
          usage_type: 'step',
        }],
      },
      {
        status: 200,
        data: {
          moved_ids: ['inject-1', 'step-1'],
          refused: [],
        },
      },
    );
    renderPopover();
    openMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Reject' }));
    const submit = await screen.findByRole('button', { name: 'Reject' });
    await screen.findByText('Used by 2 injects');

    // Act
    expect((submit as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByPlaceholderText('Reason'), { target: { value: 'Approved by mistake' } });
    expect((screen.getByRole('checkbox', { name: 'Move the injects using this version to another version' }) as HTMLButtonElement).getAttribute('aria-checked')).toBe('true');
    fireEvent.click(submit);

    // Assert
    await waitFor(() => expect(posts().some(request => request.url?.endsWith('/change-version'))).toBe(true));
    const reject = posts().find(request => request.url?.endsWith('/action-1/reject'));
    expect(JSON.parse(reject?.data as string)).toEqual({ approval_reason: 'Approved by mistake' });
    const move = posts().find(request => request.url?.endsWith('/action-1/change-version'));
    expect(JSON.parse(move?.data as string)).toEqual({
      target_payload_id: 'payload-0',
      inject_ids: ['inject-1'],
      step_ids: ['step-1'],
    });
  });

  it('moves a version back to pending with an optional note, without moving the injects when unticked', async () => {
    // Arrange
    responses.push({
      status: 200,
      data: { action_approval_status: 'PENDING' },
    });
    renderPopover();
    openMenu();
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Move to pending approval' }));
    await screen.findByText('Used by 2 injects');

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Move the injects using this version to another version' }));
    fireEvent.click(screen.getByRole('button', { name: 'Move to pending approval' }));

    // Assert
    await waitFor(() => expect(posts().some(request => request.url?.endsWith('/action-1/reopen'))).toBe(true));
    const reopen = posts().find(request => request.url?.endsWith('/action-1/reopen'));
    expect(JSON.parse(reopen?.data as string)).toEqual({});
    expect(requests.some(request => request.url?.endsWith('/change-version'))).toBe(false);
  });
});
