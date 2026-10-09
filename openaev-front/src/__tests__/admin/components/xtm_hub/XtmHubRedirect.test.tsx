import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import XtmHubRedirect, {
  XTM_HUB_AUTO_REGISTER_QUERY_PARAM,
  XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY,
} from '../../../../admin/components/xtm_hub/XtmHubRedirect';
import { defineAbility } from '../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

const theme = createTheme();

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
};

const renderWithRouter = ({
  route,
  canManageTenantSettings,
  children,
}: {
  route: string;
  canManageTenantSettings: boolean;
  children: ReactNode;
}) => {
  const ability = defineAbility(
    canManageTenantSettings ? [`${ACTIONS.MANAGE}_${SUBJECTS.TENANT_SETTINGS}`] : [],
    {},
    false,
  );

  return render(
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <AbilityProvider value={ability}>
          <MemoryRouter initialEntries={[route]}>
            {children}
          </MemoryRouter>
        </AbilityProvider>
      </IntlProvider>
    </ThemeProvider>,
  );
};

describe('XtmHubRedirect', () => {
  afterEach(() => {
    cleanup();
    sessionStorage.clear();
  });

  it('preserves query params and adds auto-register for mapped redirects', async () => {
    renderWithRouter({
      route: '/redirect/connect-xtm-hub?foo=bar',
      canManageTenantSettings: true,
      children: (
        <Routes>
          <Route path="/redirect/*" element={<XtmHubRedirect />} />
          <Route path="/admin/settings/experience" element={<LocationProbe />} />
        </Routes>
      ),
    });

    const locationNode = await screen.findByTestId('location');
    expect(locationNode.textContent).toBe(`/admin/settings/experience?foo=bar&${XTM_HUB_AUTO_REGISTER_QUERY_PARAM}=true`);
  });

  it('redirects unauthorized users to admin home and sets permission flag in sessionStorage', async () => {
    renderWithRouter({
      route: '/redirect/connect-xtm-hub?foo=bar',
      canManageTenantSettings: false,
      children: (
        <Routes>
          <Route path="/redirect/*" element={<XtmHubRedirect />} />
          <Route path="/admin" element={<LocationProbe />} />
        </Routes>
      ),
    });

    const locationNode = await screen.findByTestId('location');
    expect(locationNode.textContent).toBe('/admin?foo=bar');
    expect(sessionStorage.getItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY)).toBe('true');
  });

  it('renders not found for unknown mapping key', () => {
    renderWithRouter({
      route: '/redirect/unknown',
      canManageTenantSettings: true,
      children: (
        <Routes>
          <Route path="/redirect/*" element={<XtmHubRedirect />} />
        </Routes>
      ),
    });

    expect(screen.getByText('This page is not found on this OpenAEV application.')).toBeDefined();
  });
});
