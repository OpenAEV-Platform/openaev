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
const { requests, responses, submitErrors } = vi.hoisted(() => ({
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

vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));

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
  default: ({ onSubmit }: { onSubmit: (data: object) => Promise<void> }) => (
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
  ),
}));

const IMPACT_MESSAGE = 'Saving will send this payload back to Pending approval and block the launch of 1 atomic testing, 1 scenario until it is approved again.';
const approvalImpact = {
  status: 409,
  data: {
    message: IMPACT_MESSAGE,
    usage: {
      usage_atomic_testings_count: 1,
      usage_scenarios_count: 1,
      usage_simulations_count: 0,
    },
  },
};
const fetchedAction = {
  status: 200,
  data: {
    action_id: 'action-1',
    action_labels: { en: 'Whoami' },
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

const submitEdit = async () => {
  fireEvent.click(screen.getByRole('button', { name: 'More actions' }));
  fireEvent.click(await screen.findByRole('menuitem', { name: 'Update' }));
  fireEvent.click(await screen.findByRole('button', { name: SUBMIT_EDIT }));
};

describe('ThreatArsenalActionPopover: edit that would send an approved payload in use back to pending (US2.4)', () => {
  beforeEach(() => {
    requests.length = 0;
    responses.length = 0;
    submitErrors.length = 0;
    notifyError.mockReset();
    setNotifyErrorHandler(notifyError);
    document.cookie = 'XSRF-TOKEN=test-token';
  });

  afterEach(() => {
    cleanup();
  });

  it('asks for confirmation on the 409, then saves the same edit without the check', async () => {
    // Arrange
    responses.push(fetchedAction, approvalImpact, updatedAction);
    const onUpdate = renderPopover();

    // Act
    await submitEdit();

    // Assert: a confirmation instead of an error toast, nothing thrown to the form
    expect(await screen.findByText(/Saving sends this payload back to pending approval\. It will block the launch of the items below until it is approved again\./)).toBeTruthy();
    expect(notifyError).not.toHaveBeenCalled();
    expect(submitErrors).toHaveLength(0);
    expect(puts()).toHaveLength(1);
    expect(puts()[0].url).toMatch(/\/threat_arsenals\/action-1$/);
    expect(puts()[0].params).toEqual({ check_approval_impact: true });

    // Act: confirm
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    // Assert: same edit sent again without the check, saved, drawer closed
    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(updatedAction.data));
    expect(puts()).toHaveLength(2);
    expect(puts()[1].params?.check_approval_impact).toBeUndefined();
    expect(JSON.parse(puts()[1].data as string)).toEqual(JSON.parse(puts()[0].data as string));
    await waitFor(() => expect(screen.queryByTestId('edit-drawer')).toBeNull());
    expect(notifyError).not.toHaveBeenCalled();
  });

  it('keeps the form open and sends nothing more when the user cancels', async () => {
    // Arrange
    responses.push(fetchedAction, approvalImpact);
    const onUpdate = renderPopover();
    await submitEdit();
    await screen.findByText(/Saving sends this payload back to pending approval/);

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // Assert
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.getByTestId('edit-drawer')).toBeTruthy();
    expect(screen.getByRole('button', { name: SUBMIT_EDIT })).toBeTruthy();
    expect(puts()).toHaveLength(1);
    expect(onUpdate).not.toHaveBeenCalled();
    expect(notifyError).not.toHaveBeenCalled();
  });

  it('saves directly, with no dialog, when the edit has no approval impact', async () => {
    // Arrange
    responses.push(fetchedAction, updatedAction);
    const onUpdate = renderPopover();

    // Act
    await submitEdit();

    // Assert
    await waitFor(() => expect(onUpdate).toHaveBeenCalledWith(updatedAction.data));
    expect(puts()).toHaveLength(1);
    expect(screen.queryByText(/Saving sends this payload back to pending approval/)).toBeNull();
    await waitFor(() => expect(screen.queryByTestId('edit-drawer')).toBeNull());
  });

  it('keeps the usual error handling for another 409', async () => {
    // Arrange
    responses.push(fetchedAction, {
      status: 409,
      data: { message: 'Conflict' },
    });
    const onUpdate = renderPopover();

    // Act
    await submitEdit();

    // Assert
    await waitFor(() => expect(notifyError).toHaveBeenCalledWith(expect.objectContaining({
      status: 409,
      message: 'Conflict',
    })));
    await waitFor(() => expect(submitErrors).toHaveLength(1));
    expect(screen.queryByText(/Saving sends this payload back to pending approval/)).toBeNull();
    expect(puts()).toHaveLength(1);
    expect(onUpdate).not.toHaveBeenCalled();
  });
});
