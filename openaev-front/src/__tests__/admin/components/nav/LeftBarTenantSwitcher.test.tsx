import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import TenantSwitcher from '../../../../admin/components/nav/LeftBarTenantSwitcher';
import EnterpriseEditionContext from '../../../../components/EnterpriseEditionContext';
import { type PlatformSettings, type TenantOutput, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

const tenants = [
  {
    tenant_id: 'tenant-1',
    tenant_name: 'Filigran HQ',
  },
  {
    tenant_id: 'tenant-2',
    tenant_name: 'ACME Corporation',
  },
] as unknown as TenantOutput[];

const renderSwitcher = (options: {
  licenceValidated: boolean;
  userTenants?: TenantOutput[];
  openDialog?: () => void;
  initialPath?: string;
}) => {
  const userTenants = options.userTenants ?? tenants;
  const userContext = {
    me: {} as User,
    settings: { platform_license: { license_is_validated: options.licenceValidated } } as PlatformSettings,
    userTenants,
    currentUserTenant: userTenants[0],
  } as unknown as UserContextType;
  const eeContext = {
    open: false,
    openDialog: options.openDialog ?? (() => {}),
    closeDialog: () => {},
    EEFeatureDetectedInfo: '',
    setEEFeatureDetectedInfo: () => {},
  };
  return render(
    <MemoryRouter initialEntries={[options.initialPath ?? '/admin/scenarios']}>
      <IntlProvider locale="en" messages={{}}>
        <UserContext.Provider value={userContext}>
          <EnterpriseEditionContext.Provider value={eeContext}>
            <TenantSwitcher navOpen />
          </EnterpriseEditionContext.Provider>
        </UserContext.Provider>
      </IntlProvider>
    </MemoryRouter>,
  );
};

// Radix opens its menu on `pointerdown`, and jsdom implements neither
// PointerEvent nor the pointer-capture methods Radix calls on the trigger.
const openMenu = (trigger: HTMLElement) => {
  fireEvent.pointerDown(trigger, {
    button: 0,
    ctrlKey: false,
  });
  fireEvent.click(trigger);
};

describe('LeftBarTenantSwitcher', () => {
  afterEach(cleanup);

  it('renders nothing when the user has a single tenant', () => {
    renderSwitcher({
      licenceValidated: true,
      userTenants: [tenants[0]],
    });
    expect(screen.queryByTestId('tenant-switcher')).toBeNull();
    expect(screen.queryByText('Filigran HQ')).toBeNull();
  });

  it('gates the tenant list behind the Enterprise Edition dialog', () => {
    const openDialog = vi.fn();
    renderSwitcher({
      licenceValidated: false,
      openDialog,
    });
    // No list at all without a validated licence: the row is a plain button.
    expect(screen.queryAllByTestId('tenant-switcher-option')).toHaveLength(0);
    fireEvent.click(screen.getByTestId('tenant-switcher'));
    expect(openDialog).toHaveBeenCalledOnce();
  });

  it('renders every tenant as a real link so it can be opened in a new tab', () => {
    renderSwitcher({ licenceValidated: true });
    openMenu(screen.getByTestId('tenant-switcher'));
    const options = screen.getAllByTestId('tenant-switcher-option');
    expect(options).toHaveLength(2);
    // Same destination the legacy click handler navigated to: the current
    // page, detail segments stripped, under the target tenant.
    expect(options[1].getAttribute('href')).toBe('/tenant-2/admin/scenarios');
  });

  it('marks the current tenant inside the menu, never on the rail row', () => {
    renderSwitcher({ licenceValidated: true });
    const trigger = screen.getByTestId('tenant-switcher');
    openMenu(trigger);
    const options = screen.getAllByTestId('tenant-switcher-option');
    // `MenuItem selected` owns both channels: the announcement and the check.
    expect(options[0].getAttribute('aria-current')).toBe('true');
    expect(options[0].querySelector('[data-menu-item-icon="end"]')).not.toBeNull();
    expect(options[1].getAttribute('aria-current')).toBeNull();
    // The state belongs to the menu; the rail row is not a current page.
    expect(trigger.getAttribute('aria-current')).toBeNull();
  });
});
