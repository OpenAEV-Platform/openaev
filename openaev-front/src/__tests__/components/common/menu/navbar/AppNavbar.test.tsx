import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import AppNavbar from '../../../../../components/common/menu/navbar/AppNavbar';
import { type NavMenuEntries } from '../../../../../components/common/menu/navbar/nav-menu-model';
import { NAV_OPEN_STORAGE_KEY } from '../../../../../components/common/menu/navbar/useNavbarState';
import { type PlatformSettings, type User } from '../../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../../utils/hooks/useAuth';

const theme = createTheme();

const settings = { platform_license: { license_is_validated: false } } as PlatformSettings;

const userContext = {
  me: {} as User,
  settings,
  userTenants: [],
} as unknown as UserContextType;

const entries: NavMenuEntries[] = [
  {
    userRight: true,
    items: [
      {
        path: '/admin',
        icon: () => <span />,
        label: 'Home',
        userRight: true,
      },
      {
        path: '/admin/secret',
        icon: () => <span />,
        label: 'Secret',
        userRight: false,
      },
    ],
  },
  {
    userRight: true,
    items: [
      {
        path: '/admin/components',
        icon: () => <span />,
        label: 'Components',
        href: 'components',
        userRight: true,
        subItems: [
          {
            link: '/admin/components/documents',
            label: 'Documents',
            userRight: true,
          },
          {
            link: '/admin/components/channels',
            label: 'Channels',
            userRight: false,
          },
        ],
      },
      {
        path: '/admin/getting-started',
        icon: () => <span />,
        label: 'Getting Started',
        userRight: true,
      },
    ],
  },
];

const renderNavbar = (initialPath = '/admin') => render(
  <MemoryRouter initialEntries={[initialPath]}>
    <IntlProvider locale="en" messages={{}}>
      <ThemeProvider theme={theme}>
        <UserContext.Provider value={userContext}>
          <AppNavbar entries={entries} />
        </UserContext.Provider>
      </ThemeProvider>
    </IntlProvider>
  </MemoryRouter>,
);

describe('AppNavbar', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, 'true');
  });

  afterEach(cleanup);

  it('only renders the entries the user is allowed to see', () => {
    renderNavbar();
    expect(screen.getByRole('link', { name: 'Home' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Secret' })).toBeNull();
  });

  it('renders every row as a real link so it can be opened in a new tab', () => {
    renderNavbar();
    // The whole point of wiring the rows through `asChild`: a middle-click or
    // Ctrl/Cmd-click must open the destination, which only a real anchor does.
    expect(screen.getByRole('link', { name: 'Home' }).getAttribute('href')).toBe('/admin');
    expect(screen.getByRole('link', { name: 'Getting Started' }).getAttribute('href')).toBe('/admin/getting-started');
  });

  it('keeps every menu row in the scrolling list, including "Getting Started"', () => {
    renderNavbar();
    // It used to be pinned to the bottom of the rail. It now lives right under
    // "Settings", in the same scrollable container as its neighbours.
    const home = screen.getByRole('link', { name: 'Home' });
    const gettingStarted = screen.getByRole('link', { name: 'Getting Started' });
    const list = home.closest('.overflow-y-auto');
    expect(list).toBeTruthy();
    expect(gettingStarted.closest('.overflow-y-auto')).toBe(list);
  });

  it('marks the current page and only the current page', () => {
    renderNavbar('/admin');
    expect(screen.getByRole('link', { name: 'Home' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('link', { name: 'Getting Started' }).getAttribute('aria-current')).toBeNull();
  });

  it('filters submenu entries on permissions', () => {
    renderNavbar();
    fireEvent.click(screen.getByRole('button', { name: /Components/ }));
    expect(screen.getByRole('link', { name: 'Documents' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Channels' })).toBeNull();
  });

  it('persists the collapse state under the shared storage key', () => {
    renderNavbar();
    fireEvent.click(screen.getByRole('button', { name: /Collapse/i }));
    expect(localStorage.getItem(NAV_OPEN_STORAGE_KEY)).toBe('false');
  });

  it('keeps every row nameable and navigable once the rail is collapsed', () => {
    // The collapsed rail is a separate branch of the hand-composed row anatomy
    // (`NavbarRowContent`), which only exists because `asChild` makes the
    // library's own `icon`/`chevron` props no-ops. It is also the branch no
    // screenshot review looks at twice: the label is hidden but must stay in
    // the DOM, or the row loses its accessible name and its collapsed tooltip.
    localStorage.setItem(NAV_OPEN_STORAGE_KEY, 'false');
    renderNavbar();
    const home = screen.getByRole('link', { name: 'Home' });
    expect(home.getAttribute('href')).toBe('/admin');
    expect(home.textContent).toContain('Home');
  });
});
