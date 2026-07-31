import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AskArianeButton from '../../../../admin/components/ariane/AskArianeButton';
import { ChatbotContext, type ChatbotContextType } from '../../../../admin/components/ariane/chatbotContext';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';

const theme = createTheme({
  palette: {
    ai: {
      main: '#9575ff',
      light: '#c4b5fd',
    },
  },
});

const LABEL = 'Ask Ariane';

const DEFAULT_SETTINGS: Partial<PlatformSettings> = {
  platform_xtm_one_configured: true,
  platform_xtm_one_url: 'https://xtmone.example.com',
  filigran_chatbot_ai_cgu_status: 'enabled',
  platform_license: { license_is_validated: true },
};

const chatbotContext: ChatbotContextType = {
  isOpen: false,
  mode: 'sidebar',
  sidebarWidth: 400,
  isResizing: false,
  openChat: vi.fn(),
  closeChat: vi.fn(),
  toggleChat: vi.fn(),
  setMode: vi.fn(),
  setSidebarWidth: vi.fn(),
  setIsResizing: vi.fn(),
};

const ability = { can: () => true } as unknown as AppAbility;

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
        <UserContext.Provider value={userContext}>
          <AbilityContext.Provider value={ability}>
            <ChatbotContext.Provider value={chatbotContext}>
              {children}
            </ChatbotContext.Provider>
          </AbilityContext.Provider>
        </UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );

  return render(<AskArianeButton />, { wrapper });
};

describe('AskArianeButton', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Visibility', () => {
    it('renders the button when XTM One is configured and the AI is enabled', () => {
      renderButton();
      expect(screen.getByText(LABEL)).toBeDefined();
    });

    it('renders nothing when the agentic AI is disabled', () => {
      const { container } = renderButton({ filigran_chatbot_ai_cgu_status: 'disabled' });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when XTM One is not configured', () => {
      const { container } = renderButton({ platform_xtm_one_configured: false });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One configuration flag is absent', () => {
      const { container } = renderButton({ platform_xtm_one_configured: undefined });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One URL is missing', () => {
      const { container } = renderButton({ platform_xtm_one_url: undefined });
      expect(container.firstChild).toBeNull();
    });

    it('renders nothing when the XTM One URL is not an http(s) URL', () => {
      const { container } = renderButton({ platform_xtm_one_url: 'javascript:alert(1)' });
      expect(container.firstChild).toBeNull();
    });
  });
});
