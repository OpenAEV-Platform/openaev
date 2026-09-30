import { type ChatPanelProps } from '@filigran/chatbot';
import { cleanup, render, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AskArianePanel from '../../../../admin/components/ariane/AskArianePanel';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext, type UserContextType } from '../../../../utils/hooks/useAuth';

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

const ignoreIntlErrors = () => {};

const Providers = ({ children }: { children: ReactNode }) => (
  <IntlProvider locale="en" defaultLocale="en" messages={{}} onError={ignoreIntlErrors}>
    <UserContext.Provider value={userContext}>
      <MemoryRouter initialEntries={['/admin']}>{children}</MemoryRouter>
    </UserContext.Provider>
  </IntlProvider>
);

const renderPanel = async () => {
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
});
