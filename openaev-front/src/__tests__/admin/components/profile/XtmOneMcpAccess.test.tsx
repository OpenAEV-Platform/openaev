import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import XtmOneMcpAccess from '../../../../admin/components/profile/XtmOneMcpAccess';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

const theme = createTheme();

const TITLE = 'XTM One MCP server';
const MANAGE_LABEL = 'Manage in XTM One';

const DEFAULT_SETTINGS: Partial<PlatformSettings> = {
  platform_xtm_one_configured: true,
  platform_xtm_one_url: 'https://xtmone.example.com',
};

const renderCard = (settingsOverrides: Partial<PlatformSettings> = {}) => {
  const userContext: UserContextType = {
    me: { user_id: 'user-1' } as User,
    settings: {
      ...DEFAULT_SETTINGS,
      ...settingsOverrides,
    } as PlatformSettings,
    isXTMHubAccessible: true,
    userTenants: [],
    currentUserTenant: null,
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

  return render(<XtmOneMcpAccess />, { wrapper });
};

describe('XtmOneMcpAccess', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Visibility', () => {
    it('renders the card when XTM One is configured', () => {
      renderCard();
      expect(screen.getByText(TITLE)).toBeDefined();
    });

    it('renders nothing when XTM One is not configured', () => {
      const { container } = renderCard({ platform_xtm_one_configured: false });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One URL is missing', () => {
      const { container } = renderCard({ platform_xtm_one_url: undefined });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One URL is not an http(s) URL', () => {
      const { container } = renderCard({ platform_xtm_one_url: 'javascript:alert(1)' });
      expect(container.firstChild).toBeNull();
    });
  });

  describe('MCP endpoint', () => {
    it('displays the MCP endpoint derived from the XTM One URL', () => {
      renderCard();
      expect(screen.getByText('https://xtmone.example.com/mcp/openaev')).toBeDefined();
    });

    it('normalizes trailing slashes in the configured URL', () => {
      renderCard({ platform_xtm_one_url: 'https://xtmone.example.com///' });
      expect(screen.getByText('https://xtmone.example.com/mcp/openaev')).toBeDefined();
    });
  });

  describe('Manage in XTM One link', () => {
    it('points to the XTM One profile MCP page, opening in a new tab safely', () => {
      renderCard();
      const link = screen.getByText(MANAGE_LABEL).closest('a');
      expect(link).not.toBeNull();
      expect(link?.getAttribute('href')).toBe('https://xtmone.example.com/profile/mcp');
      expect(link?.getAttribute('target')).toBe('_blank');
      expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
    });
  });
});
