import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockT } = vi.hoisted(() => ({ mockT: vi.fn((value: string) => value) }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: mockT }) }));

import DefaultTenantDangerZone from '../../../../../admin/components/platform/tenants/DefaultTenantDangerZone';

const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    dangerZone: {
      main: '#f6685e',
      light: '#fbc2be',
      dark: '#f44336',
      contrastText: '#000000',
    },
  },
});

const lightTheme = createTheme({
  palette: {
    mode: 'light',
    dangerZone: {
      main: '#f6685e',
      light: '#fbc2be',
      dark: '#d1584f',
      contrastText: '#000000',
    },
  },
});

const renderComponent = (theme = darkTheme, children?: ReactNode) =>
  render(
    <ThemeProvider theme={theme}>
      <DefaultTenantDangerZone>{children}</DefaultTenantDangerZone>
    </ThemeProvider>,
  );

const LABEL = 'Default Tenant / Danger Zone';
const ACTION_BTN_LABEL = 'Dangerous Action';

describe('DefaultTenantDangerZone', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Label rendering', () => {
    it('renders the persistent Danger Zone label in dark theme', () => {
      renderComponent(darkTheme);
      expect(screen.getByText(LABEL)).toBeDefined();
    });

    it('renders the persistent Danger Zone label in light theme', () => {
      renderComponent(lightTheme);
      expect(screen.getByText(LABEL)).toBeDefined();
    });
  });

  describe('No dismissal affordance (AC4)', () => {
    it('does not render any close/dismiss/remove control on the label', () => {
      const { container } = renderComponent(darkTheme);
      // The MUI Chip delete icon has class .MuiChip-deleteIcon
      expect(container.querySelector('.MuiChip-deleteIcon')).toBeNull();
      // No ARIA role button labeled close/dismiss/remove
      expect(screen.queryByRole('button', { name: /close/i })).toBeNull();
      expect(screen.queryByRole('button', { name: /dismiss/i })).toBeNull();
      expect(screen.queryByRole('button', { name: /remove/i })).toBeNull();
    });
  });

  describe('Action buttons (children)', () => {
    it('renders correctly with no action buttons (empty critical-actions state)', () => {
      const { getByTestId } = renderComponent(darkTheme);
      expect(getByTestId('default-tenant-danger-zone')).toBeDefined();
    });

    it('renders provided action buttons alongside the label', () => {
      renderComponent(darkTheme, <button type="button">{ACTION_BTN_LABEL}</button>);
      expect(screen.getByText(LABEL)).toBeDefined();
      expect(screen.getByRole('button', { name: ACTION_BTN_LABEL })).toBeDefined();
    });
  });

  describe('Edge cases', () => {
    it('renders without error when translated label is very long', () => {
      const longLabel
        = 'Cette zone concerne le locataire par défaut et contient des actions critiques dangereuses pour la plateforme entière';
      mockT.mockImplementation((value: string) =>
        value === 'Default Tenant / Danger Zone' ? longLabel : value,
      );
      const { getByTestId } = renderComponent(darkTheme);
      expect(getByTestId('default-tenant-danger-zone')).toBeDefined();
      expect(screen.getByText(longLabel)).toBeDefined();
    });
  });
});
