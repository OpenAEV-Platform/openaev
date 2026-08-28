import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY } from '../../../admin/components/xtm_hub/XtmHubRedirect';

const { mockDispatch, mockHelperState } = vi.hoisted(() => ({
  mockDispatch: vi.fn(),
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

vi.mock('../../../admin/components/workspaces/custom_dashboards/CustomDashboardWrapper', () => ({ default: ({ noDashboardSlot }: { noDashboardSlot: ReactNode }) => <div>{noDashboardSlot}</div> }));

vi.mock('../../../admin/components/workspaces/custom_dashboards/NoDashboardComponent', () => ({ default: ({ actionComponent }: { actionComponent: ReactNode }) => <div>{actionComponent}</div> }));

vi.mock('../../../admin/components/workspaces/custom_dashboards/SelectDashboardButton', () => ({ default: () => <div data-testid="select-dashboard-button" /> }));

vi.mock('../../../utils/permissions/permissionsContext', () => ({ Can: ({ children }: { children: ReactNode }) => <>{children}</> }));

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

describe('Home permission dialog', () => {
  beforeEach(() => {
    mockDispatch.mockClear();
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
