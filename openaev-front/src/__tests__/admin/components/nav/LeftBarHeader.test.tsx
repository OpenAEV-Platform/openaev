import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import LeftBarHeader from '../../../../admin/components/nav/LeftBarHeader';

interface SwitcherOption {
  id: string;
  label: string;
  tooltip: string;
  href?: string;
  to?: string;
}

// Keep the production options; only render them as plain links to read them back.
vi.mock('@filigran/design-system', () => ({
  ProductSwitcher: ({ options }: { options: SwitcherOption[] }) => (
    <nav>
      {options.map(option => (
        <a key={option.id} data-testid={`switcher-${option.id}`} href={option.href ?? option.to} title={option.tooltip}>
          {option.label}
        </a>
      ))}
    </nav>
  ),
}));
vi.mock('@mui/material/styles', () => ({
  useTheme: () => ({
    palette: { mode: 'dark' },
    logo: '',
    logo_collapsed: '',
  }),
}));
vi.mock('react-router', () => ({ Link: () => null }));
vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));
vi.mock('../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../actions/xtmhub/xtmhub-actions', () => ({ fetchXtmHubRegistration: vi.fn() }));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => false }) }));
vi.mock('../../../../utils/hooks/useAuth', () => ({
  default: () => ({
    settings: {},
    isXTMHubAccessible: false,
  }),
}));
const tenantSettings = vi.fn();
vi.mock('../../../../store', () => ({
  useHelper: (selector: (helper: unknown) => unknown) => selector({
    getTenantSettings: () => tenantSettings(),
    getXtmHubRegistration: () => undefined,
  }),
}));

describe('LeftBarHeader product switcher', () => {
  afterEach(cleanup);

  it('opens the connected OpenCRQ instance when the tenant is connected to it', () => {
    // Arrange
    tenantSettings.mockReturnValue({
      xtm_opencrq_enable: true,
      xtm_opencrq_url: 'https://opencrq.example',
    });

    // Act
    render(<LeftBarHeader />);

    // Assert
    const entry = screen.getByTestId('switcher-opencrq');
    expect(entry.getAttribute('href')).toBe('https://opencrq.example');
    expect(entry.getAttribute('title')).toBe('Platform connected');
  });

  it('opens the OpenCRQ product page when the tenant is not connected', () => {
    // Arrange
    tenantSettings.mockReturnValue({ xtm_opencrq_enable: false });

    // Act
    render(<LeftBarHeader />);

    // Assert
    const entry = screen.getByTestId('switcher-opencrq');
    expect(entry.getAttribute('href')).toBe('https://filigran.io/products/opencrq');
    expect(entry.getAttribute('title')).toBe('Get OpenCRQ now');
  });

  it('lists OpenCTI, OpenCRQ and XTM Hub, in that order', () => {
    // Arrange
    tenantSettings.mockReturnValue({});

    // Act
    render(<LeftBarHeader />);

    // Assert
    expect(screen.getAllByRole('link').map(link => link.textContent)).toEqual(['OpenCTI', 'OpenCRQ', 'XTM Hub']);
  });
});
