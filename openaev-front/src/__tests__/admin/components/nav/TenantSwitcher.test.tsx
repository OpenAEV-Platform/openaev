import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import TenantSwitcher from '../../../../admin/components/nav/TenantSwitcher';
import { type TenantOutput } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

// -- TEST DATA --

const TENANT_ALPHA: TenantOutput = {
  tenant_id: 'tenant-alpha-id',
  tenant_name: 'Alpha Corp',
  tenant_description: 'Primary tenant',
};

const TENANT_BETA: TenantOutput = {
  tenant_id: 'tenant-beta-id',
  tenant_name: 'Beta Industries',
  tenant_description: 'Secondary tenant',
};

const TENANT_LONG_NAME: TenantOutput = {
  tenant_id: 'tenant-long-id',
  tenant_name: 'A Very Long Tenant Name That Should Be Truncated With Ellipsis In The UI',
  tenant_description: 'An equally long description that should also be truncated with an ellipsis when it overflows',
};

// -- HELPERS --

const theme = createTheme();

/**
 * Renders TenantSwitcher with all required providers (MUI theme, react-intl, UserContext).
 * Uses English i18n messages with passthrough (id = message) to keep assertions readable.
 */
const renderTenantSwitcher = (contextOverrides: Partial<UserContextType> = {}) => {
  const defaultContext: UserContextType = {
    me: { user_id: 'user-1' } as UserContextType['me'],
    settings: {} as UserContextType['settings'],
    isXTMHubAccessible: false,
    userTenants: [TENANT_ALPHA, TENANT_BETA],
    currentUserTenant: TENANT_ALPHA,
    switchUserTenant: vi.fn(),
    ...contextOverrides,
  };

  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" messages={{}} defaultLocale="en" onError={() => {}}>
        <UserContext.Provider value={defaultContext}>
          {children}
        </UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );

  return {
    ...render(<TenantSwitcher />, { wrapper }),
    context: defaultContext,
  };
};

/**
 * Opens the Autocomplete dropdown by clicking on the combobox input.
 */
const openDropdown = () => {
  const combobox = screen.getByRole('combobox');
  fireEvent.click(combobox);
  fireEvent.mouseDown(combobox);
};

// -- TESTS --

describe('TenantSwitcher', () => {
  afterEach(cleanup);

  describe('Rendering', () => {
    it('renders the current tenant name in the input', () => {
      renderTenantSwitcher();

      const combobox = screen.getByRole('combobox') as HTMLInputElement;
      expect(combobox.value).toBe('Alpha Corp');
    });

    it('shows an empty input when no current tenant is set', () => {
      renderTenantSwitcher({ currentUserTenant: null });

      const combobox = screen.getByRole('combobox') as HTMLInputElement;
      expect(combobox.value).toBe('');
    });

    it('renders the input as enabled', () => {
      renderTenantSwitcher();

      expect(screen.getByRole('combobox').hasAttribute('disabled')).toBe(false);
    });
  });

  describe('Dropdown interaction', () => {
    it('opens the dropdown when the input is clicked', () => {
      renderTenantSwitcher();

      expect(screen.queryByRole('listbox')).toBeNull();

      openDropdown();

      expect(screen.getByRole('listbox')).toBeDefined();
    });

    it('displays all available tenants in the dropdown', () => {
      renderTenantSwitcher();
      openDropdown();

      const options = screen.getAllByRole('option');
      expect(options).toHaveLength(2);
      expect(options[0].textContent).toContain('Alpha Corp');
      expect(options[1].textContent).toContain('Beta Industries');
    });

    it('displays tenant descriptions as secondary text', () => {
      renderTenantSwitcher();
      openDropdown();

      expect(screen.getByText('Primary tenant')).toBeDefined();
      expect(screen.getByText('Secondary tenant')).toBeDefined();
    });

    it('marks the current tenant as selected via aria-selected', () => {
      renderTenantSwitcher();
      openDropdown();

      const options = screen.getAllByRole('option');
      expect(options[0].getAttribute('aria-selected')).toBe('true');
      expect(options[1].getAttribute('aria-selected')).toBe('false');
    });

    it('shows a check icon next to the current tenant', () => {
      renderTenantSwitcher();
      openDropdown();

      const options = screen.getAllByRole('option');
      // The selected option (Alpha Corp) should have a CheckOutlinedIcon
      expect(within(options[0]).getByTestId('CheckOutlinedIcon')).toBeDefined();
      // The non-selected option should not
      expect(within(options[1]).queryByTestId('CheckOutlinedIcon')).toBeNull();
    });

    it('closes the dropdown when pressing Escape', async () => {
      renderTenantSwitcher();
      openDropdown();

      expect(screen.getByRole('listbox')).toBeDefined();

      fireEvent.keyDown(screen.getByRole('combobox'), { key: 'Escape' });

      await waitFor(() => {
        expect(screen.queryByRole('listbox')).toBeNull();
      });
    });
  });

  describe('Tenant switching', () => {
    it('calls switchUserTenant when selecting a different tenant', async () => {
      const switchUserTenant = vi.fn().mockResolvedValue(undefined);
      renderTenantSwitcher({ switchUserTenant });

      openDropdown();
      fireEvent.click(screen.getAllByRole('option')[1]); // click Beta Industries

      await waitFor(() => {
        expect(switchUserTenant).toHaveBeenCalledWith('tenant-beta-id');
      });
    });

    it('does not call switchUserTenant when selecting the already-active tenant', async () => {
      const switchUserTenant = vi.fn().mockResolvedValue(undefined);
      renderTenantSwitcher({ switchUserTenant });

      openDropdown();
      fireEvent.click(screen.getAllByRole('option')[0]); // click Alpha Corp (current)

      // Give time for any async work
      await waitFor(() => {
        expect(switchUserTenant).not.toHaveBeenCalled();
      });
    });

    it('shows an error notification when switching fails', async () => {
      const switchUserTenant = vi.fn().mockRejectedValue(new Error('Network error'));

      const notifyErrorSpy = vi.fn();
      vi.doMock('../../../../utils/Environment', async (importOriginal) => {
        // eslint-disable-next-line @typescript-eslint/consistent-type-imports
        const original = await importOriginal<typeof import('../../../../utils/Environment')>();
        return {
          ...original,
          MESSAGING$: {
            ...original.MESSAGING$,
            notifyError: notifyErrorSpy,
          },
        };
      });

      renderTenantSwitcher({ switchUserTenant });

      openDropdown();
      fireEvent.click(screen.getAllByRole('option')[1]);

      await waitFor(() => {
        expect(switchUserTenant).toHaveBeenCalledWith('tenant-beta-id');
      });
    });
  });

  describe('Edge cases', () => {
    it('renders correctly with a single tenant', () => {
      renderTenantSwitcher({
        userTenants: [TENANT_ALPHA],
        currentUserTenant: TENANT_ALPHA,
      });

      openDropdown();

      expect(screen.getAllByRole('option')).toHaveLength(1);
    });

    it('renders correctly with no tenants', () => {
      renderTenantSwitcher({
        userTenants: [],
        currentUserTenant: null,
      });

      openDropdown();

      expect(screen.queryAllByRole('option')).toHaveLength(0);
    });

    it('handles long tenant names (truncation is CSS-based)', () => {
      renderTenantSwitcher({
        userTenants: [TENANT_LONG_NAME],
        currentUserTenant: TENANT_LONG_NAME,
      });

      const combobox = screen.getByRole('combobox') as HTMLInputElement;
      expect(combobox.value).toBe(TENANT_LONG_NAME.tenant_name);
    });

    it('applies ellipsis on long tenant name and description in dropdown options', () => {
      renderTenantSwitcher({
        userTenants: [TENANT_LONG_NAME],
        currentUserTenant: TENANT_LONG_NAME,
      });

      openDropdown();

      const nameElement = screen.getByText(TENANT_LONG_NAME.tenant_name);
      expect(nameElement.classList.toString()).toContain('MuiTypography-noWrap');
      expect(nameElement.style.textOverflow || window.getComputedStyle(nameElement).textOverflow).toBeDefined();

      const descriptionElement = screen.getByText(TENANT_LONG_NAME.tenant_description!);
      expect(descriptionElement.classList.toString()).toContain('MuiTypography-noWrap');
      expect(descriptionElement.style.textOverflow || window.getComputedStyle(descriptionElement).textOverflow).toBeDefined();
    });
  });
});
