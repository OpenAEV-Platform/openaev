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
import { expectLibraryButton, expectNoMuiControls } from '../../../utils/designSystemAssertions';

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

  // Scope rule (designer, round 2): where the library ships a component, use it.
  describe('Design system adoption', () => {
    it('is the library Button, not a MUI look-alike', () => {
      renderButton();
      // `ia` is the library's AI variant - the gradient treatment this button
      // hand-rolled with backgroundClip on a MUI Button. Asserting the variant
      // and not merely "some library class" is what keeps the AI identity from
      // silently degrading to a default button.
      expectLibraryButton(
        screen.getByRole('button', { name: new RegExp(LABEL, 'i') }),
        'Ask Ariane',
        {
          variant: 'ia',
          priority: 'tertiary',
        },
      );
    });

    it('marks the Enterprise Edition feature with the library EE chip, decoratively', () => {
      // Rule: an implemented component is composed of library components
      // only. This marker was a hand-styled span in a MUI Tooltip (9px text,
      // 21x14 box, theme.palette.ee.*) - now the library's own EE severity.
      renderButton({ platform_license: { license_is_validated: false } });
      const button = screen.getByRole('button', { name: new RegExp(LABEL, 'i') });
      expectNoMuiControls(button, 'the Ask Ariane button');
      const marker = screen.getByText('EE');
      // The fill sits on the chip root, the text on its label span.
      const painted = [marker, marker.parentElement, marker.parentElement?.parentElement]
        .filter(Boolean)
        .map(el => String((el as Element).getAttribute('class') ?? ''))
        .join(' ');
      expect(painted).toContain('bg-filigran-tonic-accent');
      // Decorative: the button owns the behaviour and the accessible name, so the
      // marker must not add itself to that name (same split as the glyph slot).
      expect(marker.closest('[aria-hidden="true"]')).not.toBeNull();
      expect(button.getAttribute('aria-label')).toBeNull();
    });
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
