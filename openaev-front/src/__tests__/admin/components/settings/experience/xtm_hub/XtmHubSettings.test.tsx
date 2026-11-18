import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { LoggedHelper } from '../../../../../../actions/helper';
import XtmHubSettings from '../../../../../../admin/components/settings/experience/xtm_hub/XtmHubSettings';
import ThemeDark from '../../../../../../components/ThemeDark';
import { useHelper } from '../../../../../../store';
import { type PlatformSettings, type User, type XtmHubRegistrationOutput } from '../../../../../../utils/api-types';
import type * as EnvironmentModule from '../../../../../../utils/Environment';
import { isDemoInstance } from '../../../../../../utils/Environment';
import { UserContext, type UserContextType } from '../../../../../../utils/hooks/useAuth';
import { type AppAbility, defineAbility } from '../../../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../../../utils/permissions/permissionsContext';
import { DEFAULT_TENANT_UUID } from '../../../../../../utils/url-helper';

const { mockOpenDialog } = vi.hoisted(() => ({ mockOpenDialog: vi.fn() }));

vi.mock('../../../../../../store', () => ({ useHelper: vi.fn() }));
vi.mock('../../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../../actions/xtmhub/xtmhub-actions', () => ({
  fetchXtmHubRegistration: vi.fn(),
  refreshConnectivity: vi.fn(),
}));
vi.mock('../../../../../../utils/Environment', async (importOriginal) => {
  const original = await importOriginal<typeof EnvironmentModule>();
  return {
    ...original,
    isDemoInstance: vi.fn(() => false),
  };
});
vi.mock('../../../../../../admin/components/settings/experience/xtm_hub/XtmHubTab', () => ({
  default: ({ renderTrigger }: { renderTrigger?: (handleOpen: () => void) => ReactNode }) => (
    <div data-testid="xtm-hub-tab">{renderTrigger?.(mockOpenDialog)}</div>
  ),
}));

const theme = createTheme(ThemeDark());
const PLATFORM_ID = '2295d0ae-6438-4012-9791-6cc66afa354d';
const ADMIN_ABILITY: AppAbility = defineAbility([], {}, true);
const READ_ONLY_ABILITY: AppAbility = defineAbility(['ACCESS_TENANT_SETTINGS'], {}, false);

interface RenderOptions {
  registered?: boolean;
  demo?: boolean;
  ability?: AppAbility;
  reachable?: boolean;
  commercialModel?: XtmHubRegistrationOutput['tenant_xtmhub_registration_commercial_model'];
}

const renderXtmHubSettings = ({
  registered = false,
  demo = false,
  ability = ADMIN_ABILITY,
  reachable = true,
  commercialModel,
}: RenderOptions = {}) => {
  vi.mocked(isDemoInstance).mockReturnValue(demo);
  const settings = {
    platform_id: PLATFORM_ID,
    xtm_hub_url: 'https://hub.example.com',
    xtm_hub_reachable: reachable,
  } as PlatformSettings;
  const registration = registered
    ? {
        tenant_xtmhub_registration_status: 'REGISTERED',
        tenant_xtmhub_registration_token: 'token',
        tenant_xtmhub_registration_commercial_model: commercialModel,
      } as XtmHubRegistrationOutput
    : null;
  const helper = {
    getPlatformSettings: () => settings,
    getXtmHubRegistration: () => registration,
  } as unknown as LoggedHelper;
  vi.mocked(useHelper).mockImplementation((selector: (helper: LoggedHelper) => unknown) => selector(helper));
  const userContext = {
    me: { user_id: 'user-1' } as User,
    settings,
    isXTMHubAccessible: true,
  } as UserContextType;
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <UserContext.Provider value={userContext}>
          <AbilityProvider value={ability}>
            {children}
          </AbilityProvider>
        </UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );
  return render(<XtmHubSettings />, { wrapper });
};

describe('XtmHubSettings', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('in demo mode', () => {
    it('shows the card with the explore link but without the connect button', () => {
      renderXtmHubSettings({ demo: true });

      expect(screen.getByTestId('experience-xtm-hub-card')).toBeDefined();
      expect(screen.getByRole('link', { name: 'Explore XTM Hub' })).toBeDefined();
      expect(screen.queryByRole('button', { name: 'Connect to XTM Hub' })).toBeNull();
      expect(screen.queryByTestId('xtm-hub-tab')).toBeNull();
    });

    it('shows the card with the hub link but without the disconnect button when registered', () => {
      renderXtmHubSettings({
        demo: true,
        registered: true,
      });

      expect(screen.getByTestId('experience-xtm-hub-card')).toBeDefined();
      expect(screen.getByRole('link', { name: 'Go to the Hub' })).toBeDefined();
      expect(screen.queryByRole('button', { name: 'Disconnect XTM Hub' })).toBeNull();
    });
  });

  describe('outside demo mode', () => {
    it('opens the connection process from the connect button when the user can manage and the hub is reachable', () => {
      renderXtmHubSettings();

      const connectButton = screen.getByRole('button', { name: 'Connect to XTM Hub' });
      expect(screen.getByTestId('xtm-hub-tab').contains(connectButton)).toBe(true);
      fireEvent.click(connectButton);
      expect(mockOpenDialog).toHaveBeenCalledOnce();
    });

    it('opens the disconnection process from the disconnect button when registered', () => {
      renderXtmHubSettings({ registered: true });

      fireEvent.click(screen.getByRole('button', { name: 'Disconnect XTM Hub' }));
      expect(mockOpenDialog).toHaveBeenCalledOnce();
    });

    it('disables the connect button when the user cannot manage tenant settings', () => {
      renderXtmHubSettings({ ability: READ_ONLY_ABILITY });

      expect(screen.getByRole('button', { name: 'Connect to XTM Hub' }).hasAttribute('disabled')).toBe(true);
      expect(screen.queryByTestId('xtm-hub-tab')).toBeNull();
    });

    it('disables the connect button when the hub is unreachable', () => {
      renderXtmHubSettings({ reachable: false });

      expect(screen.getByRole('button', { name: 'Connect to XTM Hub' }).hasAttribute('disabled')).toBe(true);
      expect(screen.queryByTestId('xtm-hub-tab')).toBeNull();
    });
  });

  describe('SaaS metrics', () => {
    it('links to the hub redirect to the tenant registration when the commercial model is SaaS', () => {
      // Arrange
      renderXtmHubSettings({
        registered: true,
        commercialModel: 'SAAS',
      });

      // Act
      const metricsLink = screen.getByRole('link', { name: 'See my OpenAEV metrics' });

      // Assert
      expect(screen.getByTestId('xtm-hub-saas-metrics').contains(metricsLink)).toBe(true);
      expect(metricsLink.getAttribute('href')).toBe(
        `https://hub.example.com/redirect/openaev-registration?platform_id=${PLATFORM_ID}&tenant_id=${DEFAULT_TENANT_UUID}`,
      );
    });

    it.each([['OTHER' as const], [undefined]])('is hidden when the commercial model is %s', (commercialModel) => {
      // Arrange
      renderXtmHubSettings({
        registered: true,
        commercialModel,
      });

      // Assert
      expect(screen.queryByTestId('xtm-hub-saas-metrics')).toBeNull();
    });
  });
});
