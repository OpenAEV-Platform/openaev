import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import CtemCommandCenterButton from '../../../../admin/components/ariane/CtemCommandCenterButton';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

const theme = createTheme({ palette: { ai: { main: '#9575ff' } } });

const LABEL = 'CTEM Command Center';

const DEFAULT_SETTINGS: Partial<PlatformSettings> = {
  platform_xtm_one_configured: true,
  platform_xtm_one_url: 'https://xtmone.example.com',
  filigran_chatbot_ai_cgu_status: 'enabled',
};

const renderButton = (settingsOverrides: Partial<PlatformSettings> = {}) => {
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

  return render(<CtemCommandCenterButton />, { wrapper });
};

describe('CtemCommandCenterButton', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Visibility', () => {
    it('renders the shortcut when XTM One is configured and the AI is enabled', () => {
      renderButton();
      expect(screen.getByLabelText(LABEL)).toBeDefined();
    });

    it('renders nothing when the agentic AI is disabled', () => {
      const { container } = renderButton({ filigran_chatbot_ai_cgu_status: 'disabled' });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when XTM One is not configured', () => {
      const { container } = renderButton({ platform_xtm_one_configured: false });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One URL is missing', () => {
      const { container } = renderButton({ platform_xtm_one_url: undefined });
      expect(container.firstChild).toBeNull();
    });
  });

  describe('Navigation target', () => {
    it('points to the configured XTM One URL, opening in a new tab safely', () => {
      renderButton();
      const link = screen.getByLabelText(LABEL);
      expect(link.tagName).toBe('A');
      expect(link.getAttribute('href')).toBe('https://xtmone.example.com');
      expect(link.getAttribute('target')).toBe('_blank');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    });

    it('renders nothing when the XTM One URL is not an http(s) URL', () => {
      const { container } = renderButton({ platform_xtm_one_url: 'javascript:alert(1)' });
      expect(container.firstChild).toBeNull();
    });
  });
});
