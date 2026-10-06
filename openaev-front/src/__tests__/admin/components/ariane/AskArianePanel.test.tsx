import { type ChatPanelProps } from '@filigran/chatbot';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AskArianePanel from '../../../../admin/components/ariane/AskArianePanel';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

// The panel reads the accent straight off the palette, with no fallback.
const theme = createTheme({ palette: { ai: { main: '#B286FF' } } });

const { chatPanelRenders } = vi.hoisted(() => ({ chatPanelRenders: [] as ChatPanelProps[] }));

vi.mock('@filigran/chatbot', () => ({
  ChatPanel: (props: ChatPanelProps) => {
    chatPanelRenders.push(props);
    return null;
  },
}));
vi.mock('../../../../admin/components/ariane/installChatbotCsrf', () => ({ default: () => {} }));
vi.mock('../../../../network', async importOriginal => ({
  ...(await importOriginal<object>()),
  api: () => ({ get: () => Promise.resolve({}) }),
}));

const userContext: UserContextType = {
  me: {
    user_id: 'user-1',
    user_email: 'jane@openaev.io',
  } as User,
  settings: { platform_xtm_one_url: 'https://xtmone.example.com' } as PlatformSettings,
  isXTMHubAccessible: true,
  userTenants: [],
  currentUserTenant: null,
  switchUserTenant: vi.fn(),
  reloadUserTenants: vi.fn(),
};

interface IntlSetup {
  locale?: string;
  messages?: Record<string, string>;
  onError?: (error: { code: string }) => void;
}

const renderPanel = async ({ locale = 'en', messages = {}, onError = () => {} }: IntlSetup = {}) => {
  const Providers = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale={locale} defaultLocale="en" messages={messages} onError={onError}>
        <UserContext.Provider value={userContext}>
          <MemoryRouter initialEntries={['/admin']}>{children}</MemoryRouter>
        </UserContext.Provider>
      </IntlProvider>
    </ThemeProvider>
  );
  render(
    <AskArianePanel mode="sidebar" onClose={() => {}} onModeChange={() => {}} />,
    { wrapper: Providers },
  );
  await waitFor(() => expect(chatPanelRenders.length).toBeGreaterThan(0));
  return chatPanelRenders[chatPanelRenders.length - 1];
};

describe('AskArianePanel', () => {
  beforeEach(() => {
    chatPanelRenders.length = 0;
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({
      ok: true,
      status: 200,
    })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('given_theChatPanel_should_nameThePromptAndQuotaRoutesOfTheProxy', async () => {
    // Act
    const props = await renderPanel();

    // Assert
    expect(props.apiBaseUrl).toBe('/api/xtmone/chat');
    expect(props.apiEndpoints?.prompts).toBe('/prompts');
    expect(props.apiEndpoints?.quota).toBe('/quota');
  });

  it('given_theChatPanel_should_neverFallBackToTheXtmOneDefaultPaths', async () => {
    // Act
    const props = await renderPanel();

    // Assert: the chatbot defaults ('/chat/...') 404 behind the OpenAEV proxy.
    const paths = Object.values(props.apiEndpoints ?? {}).filter(value => typeof value === 'string');
    expect(paths.length).toBeGreaterThan(0);
    expect(paths.filter(path => path.startsWith('/chat/'))).toEqual([]);
  });

  it('given_theChatPanel_should_nameTheFeedbackRouteOfTheProxy', async () => {
    // Act
    const props = await renderPanel();

    // Assert: the chatbot appends '/{conversationId}/messages/{messageId}/feedback',
    // which is the proxy's POST / DELETE route under /api/xtmone/chat.
    expect(props.apiEndpoints?.feedback).toBe('/conversations');
  });

  it('given_aFrenchUser_should_passTheLocaleToTheChatPanel', async () => {
    // Act
    const props = await renderPanel({ locale: 'fr' });

    // Assert
    expect(props.locale).toBe('fr');
  });

  it('given_aPlaceholderKey_should_handTheRawCatalogStringToTheChatbot', async () => {
    // Arrange
    const onError = vi.fn();
    const messages = { '{quota} (shared across all users)': '{quota} (partagé entre tous les utilisateurs)' };

    // Act
    const props = await renderPanel({
      locale: 'fr',
      messages,
      onError,
    });

    // Assert: the chatbot fills '{quota}' itself, so it must stay literal and
    // never go through an ICU pass that has no value for it.
    expect(props.t?.('{quota} (shared across all users)')).toBe('{quota} (partagé entre tous les utilisateurs)');
    expect(onError).not.toHaveBeenCalledWith(expect.objectContaining({ code: 'FORMAT_ERROR' }));
  });

  it('given_aKeyMissingFromTheCatalog_should_fallBackToTheEnglishKey', async () => {
    // Act
    const props = await renderPanel({ locale: 'fr' });

    // Assert
    expect(props.t?.('Read aloud')).toBe('Read aloud');
  });
});
