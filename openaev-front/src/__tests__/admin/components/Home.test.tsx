import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY } from '../../../admin/components/xtm_hub/XtmHubRedirect';
import { type AppAbility, defineAbility } from '../../../utils/permissions/ability';

const ALL_HOME_CAPABILITIES = ['ACCESS_DASHBOARDS', 'ACCESS_TENANT_SETTINGS'];

const { mockDispatch, mockAbility, mockHelperState } = vi.hoisted(() => ({
  mockDispatch: vi.fn(),
  mockAbility: { current: undefined as AppAbility | undefined },
  mockHelperState: {
    tenantSettings: { platform_home_dashboard: 'dashboard-id' as string | undefined },
    me: undefined as { user_home_dashboard?: string } | undefined,
  },
}));

vi.mock('../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../store', () => ({ useHelper: () => mockHelperState }));

vi.mock('../../../admin/components/default_dashboard/DefaultHomeDashboard', () => ({ default: () => <div data-testid="default-home-dashboard" /> }));

vi.mock('../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../utils/hooks/useDataLoader', () => ({
  default: (loader: () => void) => {
    loader();
  },
}));

vi.mock('../../../actions/Application', () => ({ fetchPlatformParameters: () => ({ type: 'fetchPlatformParameters' }) }));

vi.mock('../../../actions/settings/tenant-settings-action', () => ({
  fetchTenantHomeDashboard: vi.fn(),
  fetchTenantSettings: () => ({ type: 'fetchTenantSettings' }),
  tenantHomeDashboardAttackPaths: vi.fn(),
  tenantHomeDashboardAverage: vi.fn(),
  tenantHomeDashboardCount: vi.fn(),
  tenantHomeDashboardEntities: vi.fn(),
  tenantHomeDashboardSeries: vi.fn(),
  tenantHomeWidgetToEntitiesRuntime: vi.fn(),
  updateTenantSettings: vi.fn(),
}));

vi.mock('../../../admin/components/workspaces/custom_dashboards/CustomDashboardWrapper', () => ({ default: ({ noDashboardSlot }: { noDashboardSlot: ReactNode }) => <div data-testid="custom-home-dashboard">{noDashboardSlot}</div> }));

vi.mock('../../../admin/components/workspaces/custom_dashboards/NoDashboardComponent', () => ({ default: ({ actionComponent }: { actionComponent: ReactNode }) => <div>{actionComponent}</div> }));

vi.mock('../../../admin/components/workspaces/custom_dashboards/SelectDashboardButton', () => ({ default: () => <div data-testid="select-dashboard-button" /> }));

vi.mock('../../../utils/permissions/permissionsContext', () => ({
  Can: ({ children }: { children: ReactNode }) => <>{children}</>,
  useAbility: () => mockAbility.current,
}));

import Home from '../../../admin/components/Home';

const theme = createTheme();

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
};

const renderHome = (route: string) => render(
  <ThemeProvider theme={theme}>
    <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route
            path="/admin"
            element={(
              <>
                <Home />
                <LocationProbe />
              </>
            )}
          />
        </Routes>
      </MemoryRouter>
    </IntlProvider>
  </ThemeProvider>,
);

describe('Home access', () => {
  afterEach(() => {
    cleanup();
  });

  // Each home dashboard requires the capability of the endpoints it calls.
  describe.each([
    {
      label: 'the tenant home dashboard and the tenant settings capability',
      tenantSettings: { platform_home_dashboard: 'dashboard-id' },
      me: undefined,
      capabilities: ['ACCESS_TENANT_SETTINGS'],
      dashboardTestId: 'custom-home-dashboard',
      allowed: true,
    },
    {
      label: 'the user home dashboard and the tenant settings capability',
      tenantSettings: { platform_home_dashboard: undefined },
      me: { user_home_dashboard: 'dashboard-id' },
      capabilities: ['ACCESS_TENANT_SETTINGS'],
      dashboardTestId: 'custom-home-dashboard',
      allowed: true,
    },
    {
      label: 'a custom home dashboard and only the dashboards capability',
      tenantSettings: { platform_home_dashboard: 'dashboard-id' },
      me: undefined,
      capabilities: ['ACCESS_DASHBOARDS'],
      dashboardTestId: 'custom-home-dashboard',
      allowed: false,
    },
    {
      label: 'the default home dashboard and the dashboards capability',
      tenantSettings: { platform_home_dashboard: undefined },
      me: undefined,
      capabilities: ['ACCESS_DASHBOARDS'],
      dashboardTestId: 'default-home-dashboard',
      allowed: true,
    },
    {
      label: 'the default home dashboard and only the tenant settings capability',
      tenantSettings: { platform_home_dashboard: undefined },
      me: undefined,
      capabilities: ['ACCESS_TENANT_SETTINGS'],
      dashboardTestId: 'default-home-dashboard',
      allowed: false,
    },
  ])('with $label', ({ tenantSettings, me, capabilities, dashboardTestId, allowed }) => {
    it(`renders ${allowed ? 'the dashboard' : 'the no access message'}`, () => {
      // Arrange
      mockHelperState.tenantSettings = tenantSettings;
      mockHelperState.me = me;
      mockAbility.current = defineAbility(capabilities, {}, false);

      // Act
      renderHome('/admin');

      // Assert
      expect(screen.queryByTestId(dashboardTestId) !== null).toBe(allowed);
      expect(screen.queryByText('You dont have the right access. Please contact your administrator.') !== null).toBe(!allowed);
    });
  });
});

describe('Home permission dialog', () => {
  beforeEach(() => {
    mockDispatch.mockClear();
    mockAbility.current = defineAbility(ALL_HOME_CAPABILITIES, {}, false);
    sessionStorage.clear();
  });

  afterEach(() => {
    cleanup();
  });

  const dashboardCases = [
    {
      label: 'custom dashboard (resolvedDashboardId is set)',
      tenantSettings: { platform_home_dashboard: 'dashboard-id' as string | undefined },
    },
    {
      label: 'default dashboard (resolvedDashboardId is not set)',
      tenantSettings: { platform_home_dashboard: undefined },
    },
  ];

  describe.each(dashboardCases)('with $label', ({ tenantSettings }) => {
    beforeEach(() => {
      mockHelperState.tenantSettings = tenantSettings;
      mockHelperState.me = undefined;
    });

    it('opens the permission dialog from sessionStorage without mutating the URL', async () => {
      // Arrange
      sessionStorage.setItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY, 'true');
      renderHome('/admin?foo=bar');

      // Act
      const title = await screen.findByText('Permission required');
      const locationNode = await screen.findByTestId('location');

      // Assert
      expect(title).toBeDefined();
      expect(locationNode.textContent).toBe('/admin?foo=bar');
    });

    it('closes the permission dialog when user clicks close', async () => {
      // Arrange
      sessionStorage.setItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY, 'true');
      renderHome('/admin');

      // Act
      const closeButton = await screen.findByRole('button', { name: 'Close' });
      fireEvent.click(closeButton);

      // Assert
      await waitForElementToBeRemoved(() => screen.queryByRole('dialog'));
      expect(screen.queryByRole('dialog')).toBeNull();
    });
  });
});
