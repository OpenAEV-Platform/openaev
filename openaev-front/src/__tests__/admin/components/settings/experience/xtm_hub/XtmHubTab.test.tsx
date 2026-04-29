import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import XtmHubTab from '../../../../../../admin/components/settings/experience/xtm_hub/XtmHubTab';
import { type PlatformSettings, type TenantOutput, type User } from '../../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../../utils/Environment';
import { isDemoInstance, XTM_HUB_DEFAULT_URL } from '../../../../../../utils/Environment';
import { UserContext, type UserContextType } from '../../../../../../utils/hooks/useAuth';
import type * as UrlHelperModule from '../../../../../../utils/url-helper';

// -- MODULE MOCKS --

const { mockOpenTab, mockCloseTab, mockFocusTab, mockDispatch, mockNotifySuccess, mockGetCurrentTenantId } = vi.hoisted(() => ({
  mockOpenTab: vi.fn(),
  mockCloseTab: vi.fn(),
  mockFocusTab: vi.fn(),
  mockDispatch: vi.fn(),
  mockNotifySuccess: vi.fn(),
  mockGetCurrentTenantId: vi.fn(() => 'tenant-abc'),
}));

vi.mock('../../../../../../utils/hooks/useExternalTab', () => ({
  default: vi.fn(() => ({
    openTab: mockOpenTab,
    closeTab: mockCloseTab,
    focusTab: mockFocusTab,
  })),
}));

vi.mock('../../../../../../store', () => ({ useHelper: vi.fn() }));

vi.mock('../../../../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../../../../actions/xtmhub/xtmhub-actions', () => ({
  registerPlatform: vi.fn(() => ({ type: 'REGISTER_PLATFORM' })),
  unregisterPlatform: vi.fn(() => ({ type: 'UNREGISTER_PLATFORM' })),
}));

vi.mock('../../../../../../utils/Environment', async (importOriginal) => {
  const original = await importOriginal<typeof EnvironmentModule>();
  return {
    ...original,
    isDemoInstance: vi.fn(() => false),
    MESSAGING$: {
      ...original.MESSAGING$,
      notifySuccess: mockNotifySuccess,
    },
  };
});

vi.mock('../../../../../../utils/url-helper', async (importOriginal) => {
  const original = await importOriginal<typeof UrlHelperModule>();
  return {
    ...original,
    getCurrentTenantId: mockGetCurrentTenantId,
  };
});

// -- SUB-COMPONENT MOCKS --
vi.mock('../../../../../../admin/components/settings/experience/xtm_hub/XtmHubProcessInstructions', () => ({
  default: ({ onContinue }: { onContinue: () => void }) => (
    <div data-testid="process-instructions">
      <button type="button" data-testid="continue-btn" onClick={onContinue} />
    </div>
  ),
}));
vi.mock('../../../../../../admin/components/settings/experience/xtm_hub/XtmHubProcessLoader', () => ({
  default: ({ onFocusTab, buttonText }: {
    onFocusTab: () => void;
    buttonText: string;
  }) => (
    <div data-testid="process-loader">
      <button type="button" data-testid="focus-tab-btn" onClick={onFocusTab}>{buttonText}</button>
    </div>
  ),
}));
vi.mock('../../../../../../admin/components/common/GradientButton', () => ({
  default: ({ children, onClick }: {
    children: ReactNode;
    onClick: () => void;
  }) => (
    <button type="button" data-testid="gradient-button" onClick={onClick}>{children}</button>
  ),
}));

vi.mock('../../../../../../admin/components/settings/experience/xtm_hub/XtmHubConfirmationDialog', () => ({
  default: ({ open, title, onConfirm, onCancel, confirmButtonText, cancelButtonText }: {
    open: boolean;
    title: string;
    onConfirm: () => void;
    onCancel: () => void;
    confirmButtonText: string;
    cancelButtonText: string;
  }) => {
    if (!open) return null;
    return (
      <div data-testid="confirmation-dialog">
        <span>{title}</span>
        <button type="button" data-testid="confirm-btn" onClick={onConfirm}>{confirmButtonText}</button>
        <button type="button" data-testid="cancel-btn" onClick={onCancel}>{cancelButtonText}</button>
      </div>
    );
  },
}));

// -- HELPERS --

import { registerPlatform, unregisterPlatform } from '../../../../../../actions/xtmhub/xtmhub-actions';
import { useHelper } from '../../../../../../store';
import useExternalTab from '../../../../../../utils/hooks/useExternalTab';
import { DEFAULT_TENANT_UUID } from '../../../../../../utils/url-helper';

const theme = createTheme();

const TENANT: TenantOutput = {
  tenant_id: 'tenant-abc',
  tenant_name: 'Test Tenant',
};

const DEFAULT_SETTINGS: Partial<PlatformSettings> = {
  platform_id: 'platform-123',
  platform_name: 'Test Platform',
  platform_version: '1.0.0',
  xtm_hub_url: 'https://hub.example.com',
  platform_license: { license_is_validated: true } as PlatformSettings['platform_license'],
};

const UNREGISTER_BUTTON_TEXT = 'Unregister from XTM Hub';

// -- FACTORIZED HELPERS --

interface RenderOptions {
  registrationStatus?: 'REGISTERED' | null;
  settingsOverrides?: Partial<PlatformSettings>;
  currentUserTenant?: TenantOutput | null;
}

const renderXtmHubTab = ({ registrationStatus = null, settingsOverrides = {}, currentUserTenant = TENANT }: RenderOptions = {}) => {
  vi.mocked(useHelper).mockReturnValue(
    registrationStatus
      ? {
          tenant_xtmhub_registration_status: registrationStatus,
          tenant_xtmhub_registration_id: 'reg-1',
        }
      : null,
  );

  const userContext: UserContextType = {
    me: { user_id: 'user-1' } as User,
    settings: {
      ...DEFAULT_SETTINGS,
      ...settingsOverrides,
    } as PlatformSettings,
    isXTMHubAccessible: true,
    userTenants: currentUserTenant ? [currentUserTenant] : [],
    currentUserTenant,
    switchUserTenant: vi.fn(),
    reloadUserTenants: vi.fn(),
  };

  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <UserContext.Provider value={userContext}>{children}</UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );

  return render(<XtmHubTab />, { wrapper });
};

/** Args passed to useExternalTab on the first render. */
const getExternalTabArgs = () => vi.mocked(useExternalTab).mock.calls[0][0];

/** Builds the expected registration URLSearchParams, allowing per-test overrides. */
const buildRegistrationParams = (overrides: Record<string, string> = {}) =>
  new URLSearchParams({
    tenant_id: TENANT.tenant_id,
    platform_id: DEFAULT_SETTINGS.platform_id!,
    platform_url: `${window.location.origin}/${TENANT.tenant_id}`,
    platform_title: DEFAULT_SETTINGS.platform_name!,
    platform_contract: 'EE',
    platform_version: DEFAULT_SETTINGS.platform_version!,
    tenant_name: TENANT.tenant_name,
    ...overrides,
  });

/**
 * Opens the dialog, advances to the WAITING_HUB step, and returns the onMessage
 * handler captured at that point — avoiding reliance on render call count.
 */
const openProcessToWaitingHub = (registrationStatus: 'REGISTERED' | null = null) => {
  if (registrationStatus === 'REGISTERED') {
    fireEvent.click(screen.getByText(UNREGISTER_BUTTON_TEXT));
  } else {
    fireEvent.click(screen.getByTestId('gradient-button'));
  }
  fireEvent.click(screen.getByTestId('continue-btn'));
  return vi.mocked(useExternalTab).mock.calls.at(-1)![0].onMessage;
};

// -- TESTS --

describe('XtmHubTab', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    // Restore default mock implementations after each test
    mockGetCurrentTenantId.mockImplementation(() => 'tenant-abc');
    // Ensure the demo mode mock is reset between tests
    vi.mocked(isDemoInstance).mockReturnValue(false);
  });

  // ── 1. Rendering ──────────────────────────────────────────────────────────

  describe('Rendering', () => {
    it('renders a GradientButton "Register in XTM Hub" when not registered', () => {
      renderXtmHubTab({ registrationStatus: null });
      expect(screen.getByTestId('gradient-button')).toBeDefined();
      expect(screen.getByText('Register in XTM Hub')).toBeDefined();
    });

    it('renders an outlined error Button "Unregister from XTM Hub" when registered', () => {
      renderXtmHubTab({ registrationStatus: 'REGISTERED' });
      expect(screen.queryByTestId('gradient-button')).toBeNull();
      expect(screen.getByText(UNREGISTER_BUTTON_TEXT)).toBeDefined();
    });

    it('renders nothing when in demo mode', () => {
      vi.mocked(isDemoInstance).mockReturnValue(true);
      const { container } = renderXtmHubTab();
      expect(container.firstChild).toBeNull();
    });
  });

  // ── 2. Dialog open / step transitions ────────────────────────────────────

  describe('Dialog lifecycle', () => {
    it('opens the dialog and shows instructions step when clicking Register', () => {
      renderXtmHubTab({ registrationStatus: null });
      fireEvent.click(screen.getByTestId('gradient-button'));
      expect(screen.getByTestId('process-instructions')).toBeDefined();
    });

    it('opens the dialog and shows instructions step when clicking Unregister', () => {
      renderXtmHubTab({ registrationStatus: 'REGISTERED' });
      fireEvent.click(screen.getByText(UNREGISTER_BUTTON_TEXT));
      expect(screen.getByTestId('process-instructions')).toBeDefined();
    });

    it('transitions to WAITING_HUB step (shows loader) when Continue is clicked', () => {
      renderXtmHubTab({ registrationStatus: null });
      openProcessToWaitingHub();
      expect(screen.getByTestId('process-loader')).toBeDefined();
      expect(screen.queryByTestId('process-instructions')).toBeNull();
    });

    it('calls openTab when transitioning to WAITING_HUB', () => {
      renderXtmHubTab({ registrationStatus: null });
      openProcessToWaitingHub();
      expect(mockOpenTab).toHaveBeenCalledOnce();
    });

    it('closes immediately (no confirmation) when dialog is closed from INSTRUCTIONS step', () => {
      renderXtmHubTab({ registrationStatus: null });
      fireEvent.click(screen.getByTestId('gradient-button'));
      expect(screen.getByTestId('process-instructions')).toBeDefined();
      fireEvent.click(screen.getByLabelText('close'));
      expect(screen.queryByTestId('process-instructions')).toBeNull();
    });

    it('shows the confirmation dialog when closing from WAITING_HUB step', () => {
      renderXtmHubTab({ registrationStatus: null });
      openProcessToWaitingHub();
      fireEvent.click(screen.getByLabelText('close'));
      expect(screen.getByTestId('confirmation-dialog')).toBeDefined();
      expect(screen.getByText('Close registration process?')).toBeDefined();
    });

    it('fully closes when confirming the confirmation dialog', () => {
      renderXtmHubTab({ registrationStatus: null });
      openProcessToWaitingHub();
      fireEvent.click(screen.getByLabelText('close'));
      fireEvent.click(screen.getByTestId('confirm-btn'));
      expect(screen.queryByTestId('process-loader')).toBeNull();
      expect(mockCloseTab).toHaveBeenCalledOnce();
    });

    it('returns to the process dialog when cancelling the confirmation dialog', () => {
      renderXtmHubTab({ registrationStatus: null });
      openProcessToWaitingHub();
      fireEvent.click(screen.getByLabelText('close'));
      fireEvent.click(screen.getByTestId('cancel-btn'));
      expect(screen.queryByTestId('confirmation-dialog')).toBeNull();
      expect(screen.getByTestId('process-loader')).toBeDefined();
    });
  });

  // ── 3. Registration flow ──────────────────────────────────────────────────

  describe('Registration flow', () => {
    it('dispatches registerPlatform and shows success notification on success', async () => {
      mockDispatch.mockResolvedValue(undefined);
      renderXtmHubTab({ registrationStatus: null });
      const onMessage = openProcessToWaitingHub();

      onMessage(new MessageEvent('message', {
        data: {
          action: 'register',
          token: 'tok-123',
        },
      }));

      await waitFor(() => expect(registerPlatform).toHaveBeenCalledWith('tok-123'));
      await waitFor(() => expect(mockNotifySuccess).toHaveBeenCalledWith(expect.stringContaining('successfully registered')));
    });

    it('transitions to ERROR step when registerPlatform fails', async () => {
      mockDispatch.mockRejectedValue(new Error('Network error'));
      renderXtmHubTab({ registrationStatus: null });
      const onMessage = openProcessToWaitingHub();

      onMessage(new MessageEvent('message', {
        data: {
          action: 'register',
          token: 'tok-bad',
        },
      }));

      await waitFor(() => expect(screen.getByText('Sorry, we have an issue, please retry')).toBeDefined());
    });
  });

  // ── 4. Unregistration flow ────────────────────────────────────────────────

  describe('Unregistration flow', () => {
    it('dispatches unregisterPlatform and shows success notification on success', async () => {
      mockDispatch.mockResolvedValue(undefined);
      renderXtmHubTab({ registrationStatus: 'REGISTERED' });
      const onMessage = openProcessToWaitingHub('REGISTERED');

      onMessage(new MessageEvent('message', { data: { action: 'unregister' } }));

      await waitFor(() => expect(unregisterPlatform).toHaveBeenCalledWith('reg-1'));
      await waitFor(() => expect(mockNotifySuccess).toHaveBeenCalledWith(expect.stringContaining('successfully unregistered')));
    });

    it('transitions to ERROR step when unregisterPlatform fails', async () => {
      mockDispatch.mockRejectedValue(new Error('Network error'));
      renderXtmHubTab({ registrationStatus: 'REGISTERED' });
      const onMessage = openProcessToWaitingHub('REGISTERED');

      onMessage(new MessageEvent('message', { data: { action: 'unregister' } }));

      await waitFor(() => expect(screen.getByText('Sorry, we have an issue, please retry')).toBeDefined());
    });
  });

  // ── 5. handleTabMessage ───────────────────────────────────────────────────

  describe('handleTabMessage', () => {
    it('transitions to CANCELED step on action=cancel', async () => {
      renderXtmHubTab({ registrationStatus: null });
      const onMessage = openProcessToWaitingHub();

      onMessage(new MessageEvent('message', { data: { action: 'cancel' } }));

      await waitFor(() => expect(screen.getByText('You have canceled the registration process')).toBeDefined());
    });

    it('transitions to ERROR step on unknown action', async () => {
      renderXtmHubTab({ registrationStatus: null });
      const onMessage = openProcessToWaitingHub();

      onMessage(new MessageEvent('message', { data: { action: 'unknown_action' } }));

      await waitFor(() => expect(screen.getByText('Sorry, we have an issue, please retry')).toBeDefined());
    });
  });

  // ── 6. URL building / useExternalTab params ──────────────────────────────

  describe('useExternalTab params', () => {
    describe('when not registered', () => {
      it('passes the exact registration URL', () => {
        renderXtmHubTab({ registrationStatus: null });
        expect(getExternalTabArgs().url).toBe(`https://hub.example.com/redirect/register-openaev?${buildRegistrationParams()}`);
      });

      it('platform_url is the full origin + tenant path when a tenant is set', () => {
        renderXtmHubTab({ registrationStatus: null });
        const platformUrl = new URL(getExternalTabArgs().url).searchParams.get('platform_url');
        expect(platformUrl).toBe(`${window.location.origin}/${TENANT.tenant_id}`);
      });

      it('platform_url uses the default tenant uuid when no tenant is set', () => {
        mockGetCurrentTenantId.mockReturnValueOnce(DEFAULT_TENANT_UUID);
        renderXtmHubTab({
          registrationStatus: null,
          currentUserTenant: null,
        });
        const platformUrl = new URL(getExternalTabArgs().url).searchParams.get('platform_url');
        expect(platformUrl).toBe(`${window.location.origin}/${DEFAULT_TENANT_UUID}`);
      });

      it('uses CE contract when license is not validated', () => {
        renderXtmHubTab({
          registrationStatus: null,
          settingsOverrides: { platform_license: { license_is_validated: false } as PlatformSettings['platform_license'] },
        });
        expect(new URL(getExternalTabArgs().url).searchParams.get('platform_contract')).toBe('CE');
      });

      it('uses XTM_HUB_DEFAULT_URL as base when xtm_hub_url is not set', () => {
        renderXtmHubTab({
          registrationStatus: null,
          settingsOverrides: { xtm_hub_url: undefined },
        });
        expect(getExternalTabArgs().url).toBe(`${XTM_HUB_DEFAULT_URL}/redirect/register-openaev?${buildRegistrationParams()}`);
      });

      it('passes tabName "xtmhub-registration"', () => {
        renderXtmHubTab({ registrationStatus: null });
        expect(getExternalTabArgs().tabName).toBe('xtmhub-registration');
      });
    });

    describe('when registered', () => {
      it('passes the exact unregistration URL', () => {
        renderXtmHubTab({ registrationStatus: 'REGISTERED' });
        const expectedParams = new URLSearchParams({
          tenant_id: 'tenant-abc',
          platform_id: 'platform-123',
        });
        expect(getExternalTabArgs().url).toBe(`https://hub.example.com/redirect/unregister-openaev?${expectedParams}`);
      });

      it('unregistration URL contains only platform identifier query params (no platform info)', () => {
        renderXtmHubTab({ registrationStatus: 'REGISTERED' });
        const query = new URL(getExternalTabArgs().url).searchParams;
        expect(query.get('platform_id')).toBe('platform-123');
        expect(query.get('tenant_id')).toBe('tenant-abc');
        expect(query.get('platform_title')).toBeNull();
        expect(query.get('platform_version')).toBeNull();
        expect(query.get('platform_contract')).toBeNull();
        expect(query.get('platform_url')).toBeNull();
      });

      it('passes tabName "xtmhub-unregistration"', () => {
        renderXtmHubTab({ registrationStatus: 'REGISTERED' });
        expect(getExternalTabArgs().tabName).toBe('xtmhub-unregistration');
      });
    });
  });
});
