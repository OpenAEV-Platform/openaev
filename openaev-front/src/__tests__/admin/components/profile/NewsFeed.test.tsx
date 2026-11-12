import '@testing-library/jest-dom/vitest';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type NewsFeedSettingsOutput } from '../../../../utils/api-types';

let enabled = false;
let canManageTenantSettings = true;
let xtmHubReachable = true;
const {
  mockFetchSettings,
  mockUpdateSettings,
  mockMarkAllRead,
  mockNotifyError,
  mockNotifyErrorHandler,
} = vi.hoisted(() => ({
  mockFetchSettings: vi.fn(),
  mockUpdateSettings: vi.fn(),
  mockMarkAllRead: vi.fn(),
  mockNotifyError: vi.fn(),
  mockNotifyErrorHandler: vi.fn(),
}));

vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => enabled }));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: (action: string, subject: string) => canManageTenantSettings && action === 'MANAGE' && subject === 'TENANT_SETTINGS' }) }));
vi.mock('../../../../utils/hooks/useAuth', () => ({ default: () => ({ settings: { xtm_hub_reachable: xtmHubReachable } }) }));
vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({
  fetchMyNewsFeedSettings: mockFetchSettings,
  updateMyNewsFeedSettings: mockUpdateSettings,
  markAllMyNewsFeedItemsRead: mockMarkAllRead,
}));
vi.mock(import('../../../../utils/Environment'), async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    MESSAGING$: {
      ...original.MESSAGING$,
      notifyError: mockNotifyError,
    },
  };
});
vi.mock('../../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: mockNotifyErrorHandler }));
vi.mock('../../../../admin/components/profile/news_feed/NewsFeedList', () => ({ default: () => <div data-testid="news-feed-list" /> }));
vi.mock('../../../../components/NotFound', () => ({ default: () => <div data-testid="not-found" /> }));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');
const { MESSAGING$ } = await import('../../../../utils/Environment');

const DISCONNECTED = 'XTM Hub is disconnected';
const ENABLE_ALL = 'Enable News Feed notifications';

const buildSettings = (overrides: Partial<NewsFeedSettingsOutput> = {}): NewsFeedSettingsOutput => ({
  news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'],
  news_feed_settings_hub_registered: true,
  news_feed_settings_hub_connected: true,
  ...overrides,
});

const renderPage = (tab: 'items' | 'settings') => render(
  <MemoryRouter>
    <IntlProvider locale="en" onError={() => {}}>
      <ThemeProvider theme={createTheme()}>
        <NewsFeed tab={tab} />
      </ThemeProvider>
    </IntlProvider>
  </MemoryRouter>,
);

describe('NewsFeed page', () => {
  beforeEach(() => {
    enabled = true;
    canManageTenantSettings = true;
    xtmHubReachable = true;
    mockFetchSettings.mockResolvedValue({ data: buildSettings() });
    mockMarkAllRead.mockResolvedValue({});
    mockUpdateSettings.mockImplementation(async input => ({ data: buildSettings({ news_feed_settings_subscribed_types: input.news_feed_settings_subscribed_types }) }));
  });

  afterEach(() => {
    cleanup();
    enabled = false;
    vi.clearAllMocks();
  });

  describe('route guard', () => {
    it('is not reachable when the feature is disabled', () => {
      enabled = false;
      renderPage('items');
      expect(screen.getByTestId('not-found')).toBeInTheDocument();
      expect(screen.queryByTestId('news-feed-list')).not.toBeInTheDocument();
      expect(mockFetchSettings).not.toHaveBeenCalled();
    });
  });

  describe('unread items', () => {
    it('marks all the items read once under the News feed tab, then refreshes the counter', async () => {
      const refreshCounter = vi.fn();
      const subscription = MESSAGING$.refreshNewsFeedUnreadCount.subscribe(refreshCounter);
      renderPage('items');
      await waitFor(() => expect(refreshCounter).toHaveBeenCalledTimes(1));
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
      subscription.unsubscribe();
    });

    it('does not mark anything read under the Settings tab', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: ENABLE_ALL });
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('does not mark anything read when the feature is disabled', () => {
      enabled = false;
      renderPage('items');
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('keeps the counter and the page when the items cannot be marked read', async () => {
      mockMarkAllRead.mockRejectedValue(new Error('unavailable'));
      const refreshCounter = vi.fn();
      const subscription = MESSAGING$.refreshNewsFeedUnreadCount.subscribe(refreshCounter);
      renderPage('items');
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(1));
      expect(screen.getByTestId('news-feed-list')).toBeInTheDocument();
      expect(refreshCounter).not.toHaveBeenCalled();
      expect(mockNotifyErrorHandler).not.toHaveBeenCalled();
      subscription.unsubscribe();
    });
  });

  describe('header', () => {
    it('is titled XTM Hub News Feed in the breadcrumbs', () => {
      renderPage('items');
      expect(within(screen.getByRole('navigation')).getByText('XTM Hub News Feed')).toBeInTheDocument();
    });

    it('shows the XTM Hub News Feed title over the tabs', () => {
      renderPage('items');
      expect(screen.getByRole('heading', {
        level: 1,
        name: 'XTM Hub News Feed',
      })).toBeInTheDocument();
    });
  });

  describe('settings outline', () => {
    it('titles the settings under the page title, as a level 2 heading', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: ENABLE_ALL });
      expect(screen.getByRole('heading', {
        level: 1,
        name: 'XTM Hub News Feed',
      })).toBeInTheDocument();
      expect(screen.getByRole('heading', {
        level: 2,
        name: 'XTM Hub News feed settings',
      })).toBeInTheDocument();
    });
  });

  describe('tabs', () => {
    it('shows the list under the News feed tab', () => {
      renderPage('items');
      expect(screen.getByTestId('news-feed-list')).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'News feed' })).toHaveAttribute('aria-current', 'page');
      expect(screen.getByRole('tab', { name: 'Settings' })).not.toHaveAttribute('aria-current');
    });

    it('links both tabs to their route', () => {
      renderPage('items');
      expect(screen.getByRole('tab', { name: 'News feed' })).toHaveAttribute('href', '/admin/profile/news-feed');
      expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute('href', '/admin/profile/news-feed/settings');
    });

    it('shows the settings, not the list, under the Settings tab', async () => {
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: ENABLE_ALL })).toBeInTheDocument();
      expect(screen.queryByTestId('news-feed-list')).not.toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute('aria-current', 'page');
    });
  });

  describe('XTM Hub banner', () => {
    it.each(['items', 'settings'] as const)('invites to connect the product on the %s tab when the tenant is not registered', async (tab) => {
      mockFetchSettings.mockResolvedValue({
        data: buildSettings({
          news_feed_settings_hub_registered: false,
          news_feed_settings_hub_connected: false,
        }),
      });
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Connect product to XTM Hub' })).toHaveAttribute('href', '/redirect/connect-xtm-hub');
    });

    it.each(['items', 'settings'] as const)('asks a user who cannot connect XTM Hub to ask an administrator, with no way to connect, on the %s tab', async (tab) => {
      canManageTenantSettings = false;
      mockFetchSettings.mockResolvedValue({
        data: buildSettings({
          news_feed_settings_hub_registered: false,
          news_feed_settings_hub_connected: false,
        }),
      });
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByText('Ask an administrator to connect your product to XTM Hub so you can receive News Feeds')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Connect product to XTM Hub' })).not.toBeInTheDocument();
    });

    it.each(['items', 'settings'] as const)('invites to reconnect on the %s tab when the platform lost its connectivity', async (tab) => {
      // still registered, so the news feed keeps being served, but no longer connected
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_hub_connected: false }) });
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Connect product to XTM Hub' })).toBeInTheDocument();
    });

    it.each(['items', 'settings'] as const)('says XTM Hub is unreachable, with no way to connect, on the %s tab', async (tab) => {
      xtmHubReachable = false;
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByText('XTM Hub is unreachable and connection can\'t be established')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Connect product to XTM Hub' })).not.toBeInTheDocument();
    });

    it('says XTM Hub is unreachable to a user who cannot connect XTM Hub too', async () => {
      xtmHubReachable = false;
      canManageTenantSettings = false;
      renderPage('items');
      expect(await screen.findByText('XTM Hub is unreachable and connection can\'t be established')).toBeInTheDocument();
    });

    it('is not shown when the tenant is registered', async () => {
      renderPage('items');
      await waitFor(() => expect(mockFetchSettings).toHaveBeenCalled());
      expect(screen.queryByText(DISCONNECTED)).not.toBeInTheDocument();
    });

    it('is not shown while the settings are loading or when they fail to load', async () => {
      mockFetchSettings.mockRejectedValue(new Error('boom'));
      renderPage('items');
      await waitFor(() => expect(mockFetchSettings).toHaveBeenCalled());
      expect(screen.queryByText(DISCONNECTED)).not.toBeInTheDocument();
      expect(screen.getByTestId('news-feed-list')).toBeInTheDocument();
    });
  });

  describe('settings', () => {
    it('shows the type the user is subscribed to as enabled', async () => {
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Scenario' })).toBeChecked();
      expect(screen.getByRole('switch', { name: ENABLE_ALL })).toBeChecked();
    });

    it('shows nothing as enabled when the user unsubscribed from everything', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_subscribed_types: [] }) });
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Scenario' })).not.toBeChecked();
      expect(screen.getByRole('switch', { name: ENABLE_ALL })).not.toBeChecked();
    });

    it('unsubscribes from a type when its switch is turned off', async () => {
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }, false));
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).not.toBeChecked());
    });

    it('unsubscribes from a type when its label is clicked', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: 'Scenario' });
      fireEvent.click(screen.getByText('Scenario'));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }, false));
    });

    it('unsubscribes from everything when the label of the global switch is clicked', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: ENABLE_ALL });
      fireEvent.click(screen.getByText(ENABLE_ALL));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }, false));
    });

    it('subscribes to a type again when its switch is turned back on', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_subscribed_types: [] }) });
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'] }, false));
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).toBeChecked());
    });

    it('unsubscribes from everything with the global switch', async () => {
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: ENABLE_ALL }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }, false));
    });

    it('subscribes to every type with the global switch', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_subscribed_types: [] }) });
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: ENABLE_ALL }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'] }, false));
    });

    it('keeps the previous state when the save fails', async () => {
      mockUpdateSettings.mockRejectedValue(new Error('boom'));
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).not.toBeDisabled());
      expect(screen.getByRole('switch', { name: 'Scenario' })).toBeChecked();
    });

    it('explains that the product is no longer connected when the save is refused with a 403', async () => {
      mockUpdateSettings.mockRejectedValue({ status: 403 });
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockNotifyError).toHaveBeenCalledWith('Your OpenAEV product is no longer connected to XTM Hub'));
      expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }, false);
      expect(mockNotifyErrorHandler).not.toHaveBeenCalled();
      expect(screen.getByRole('switch', { name: 'Scenario' })).toBeChecked();
    });

    it('lets the generic handler notify any other save failure', async () => {
      const error = new Error('boom');
      mockUpdateSettings.mockRejectedValue(error);
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockNotifyErrorHandler).toHaveBeenCalledWith(error));
      expect(mockNotifyError).not.toHaveBeenCalled();
    });

    it('cannot be changed when the tenant is not registered', async () => {
      mockFetchSettings.mockResolvedValue({
        data: buildSettings({
          news_feed_settings_hub_registered: false,
          news_feed_settings_hub_connected: false,
        }),
      });
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Scenario' })).toBeDisabled();
      expect(screen.getByRole('switch', { name: ENABLE_ALL })).toBeDisabled();
    });

    it('shows an error when the settings cannot be loaded', async () => {
      mockFetchSettings.mockRejectedValue(new Error('boom'));
      renderPage('settings');
      expect(await screen.findByText('Error')).toBeInTheDocument();
      expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    });
  });
});
