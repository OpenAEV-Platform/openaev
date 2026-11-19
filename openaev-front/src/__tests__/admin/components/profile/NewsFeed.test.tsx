import '@testing-library/jest-dom/vitest';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { Link, MemoryRouter } from 'react-router';
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
const { mockBlogPostsMounted, mockUnreadCount } = vi.hoisted(() => ({
  mockBlogPostsMounted: vi.fn(),
  mockUnreadCount: vi.fn(),
}));
vi.mock('../../../../admin/components/profile/news_feed/NewsFeedBlogPosts', async () => {
  const { useEffect } = await import('react');
  return {
    default: () => {
      useEffect(() => mockBlogPostsMounted(), []);
      return <div data-testid="news-feed-blog-posts" />;
    },
  };
});
vi.mock('../../../../admin/components/nav/useNewsFeedUnreadCount', () => ({ default: mockUnreadCount }));
vi.mock('../../../../components/NotFound', () => ({ default: () => <div data-testid="not-found" /> }));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');
const { MESSAGING$ } = await import('../../../../utils/Environment');

const DISCONNECTED = 'XTM Hub is disconnected';
const ENABLE_ALL = 'Enable News Feed notifications';
const TOP_BAR_LINK = 'Top bar icon';

const buildSettings = (overrides: Partial<NewsFeedSettingsOutput> = {}): NewsFeedSettingsOutput => ({
  news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'],
  news_feed_settings_hub_registered: true,
  news_feed_settings_hub_connected: true,
  news_feed_settings_available_types: ['RESOURCE_SCENARIO', 'BLOG_POST'],
  ...overrides,
});

const WITHOUT_BLOG_POSTS = buildSettings({ news_feed_settings_available_types: ['RESOURCE_SCENARIO'] });

const renderPage = (tab: 'blog_posts' | 'settings') => render(
  <MemoryRouter>
    <IntlProvider locale="en" onError={() => {}}>
      <ThemeProvider theme={createTheme()}>
        <Link to="/admin/profile/news-feed">{TOP_BAR_LINK}</Link>
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
    mockUnreadCount.mockReturnValue(0);
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
      renderPage('blog_posts');
      expect(screen.getByTestId('not-found')).toBeInTheDocument();
      expect(screen.queryByTestId('news-feed-blog-posts')).not.toBeInTheDocument();
      expect(mockFetchSettings).not.toHaveBeenCalled();
    });
  });

  describe('unread items', () => {
    it('marks the blogposts read once under the Blogpost tab, then refreshes the counter', async () => {
      const refreshCounter = vi.fn();
      const subscription = MESSAGING$.refreshNewsFeedUnreadCount.subscribe(refreshCounter);
      renderPage('blog_posts');
      await waitFor(() => expect(refreshCounter).toHaveBeenCalledTimes(1));
      expect(mockMarkAllRead).toHaveBeenCalledTimes(2);
      expect(mockMarkAllRead).toHaveBeenCalledWith('BLOG_POST');
      subscription.unsubscribe();
    });

    it('marks the resources read too, as no tab shows them, so that the top bar counter goes back to 0', async () => {
      renderPage('blog_posts');
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(2));
      expect(mockMarkAllRead).toHaveBeenCalledWith('RESOURCE');
      expect(mockMarkAllRead).not.toHaveBeenCalledWith();
    });

    it('marks every item read, whatever its category, when XTM Hub provisions no blogpost', async () => {
      mockFetchSettings.mockResolvedValue({ data: WITHOUT_BLOG_POSTS });
      renderPage('blog_posts');
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(1));
      expect(mockMarkAllRead).toHaveBeenCalledWith();
      expect(mockMarkAllRead).not.toHaveBeenCalledWith('RESOURCE');
    });

    it('marks nothing read before it knows whether XTM Hub provisions blogposts', async () => {
      mockFetchSettings.mockRejectedValue(new Error('boom'));
      renderPage('blog_posts');
      await screen.findByText('Error');
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('marks the blogposts read and reloads them again when the user navigates to the page already shown', async () => {
      renderPage('blog_posts');
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(2));
      expect(mockBlogPostsMounted).toHaveBeenCalledTimes(1);
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(4));
      expect(mockBlogPostsMounted).toHaveBeenCalledTimes(2);
    });

    it('does not mark anything read when navigating to the page under the Settings tab', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: ENABLE_ALL });
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await screen.findByRole('switch', { name: ENABLE_ALL });
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('does not mark anything read under the Settings tab', async () => {
      renderPage('settings');
      await screen.findByRole('switch', { name: ENABLE_ALL });
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('does not mark anything read when the feature is disabled', () => {
      enabled = false;
      renderPage('blog_posts');
      expect(mockMarkAllRead).not.toHaveBeenCalled();
    });

    it('keeps the counter and the page when the items cannot be marked read', async () => {
      mockMarkAllRead.mockRejectedValue(new Error('unavailable'));
      const refreshCounter = vi.fn();
      const subscription = MESSAGING$.refreshNewsFeedUnreadCount.subscribe(refreshCounter);
      renderPage('blog_posts');
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(2));
      expect(screen.getByTestId('news-feed-blog-posts')).toBeInTheDocument();
      expect(refreshCounter).not.toHaveBeenCalled();
      expect(mockNotifyError).not.toHaveBeenCalled();
      expect(mockNotifyErrorHandler).not.toHaveBeenCalled();
      subscription.unsubscribe();
    });
  });

  describe('header', () => {
    it('is titled XTM Hub News Feed in the breadcrumbs', () => {
      renderPage('blog_posts');
      expect(within(screen.getByRole('navigation')).getByText('XTM Hub News Feed')).toBeInTheDocument();
    });

    it('shows the XTM Hub News Feed title over the tabs', () => {
      renderPage('blog_posts');
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
    it('shows the blogposts under the Blogpost tab, and no tab of the resources', async () => {
      renderPage('blog_posts');
      expect(await screen.findByTestId('news-feed-blog-posts')).toBeInTheDocument();
      expect(screen.getByRole('tab', { name: /Blogpost/ })).toHaveAttribute('aria-current', 'page');
      expect(screen.getByRole('tab', { name: 'Settings' })).not.toHaveAttribute('aria-current');
      expect(screen.queryByRole('tab', { name: 'News feed' })).not.toBeInTheDocument();
    });

    it('links both tabs to their route', async () => {
      renderPage('blog_posts');
      expect(await screen.findByRole('tab', { name: /Blogpost/ })).toHaveAttribute('href', '/admin/profile/news-feed');
      expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute('href', '/admin/profile/news-feed/settings');
    });

    it('shows the unread blogposts on the Blogpost tab', async () => {
      mockUnreadCount.mockReturnValue(4);
      renderPage('settings');
      const tab = await screen.findByRole('tab', { name: /Blogpost/ });
      expect(within(tab).getByText('4')).toBeInTheDocument();
      expect(mockUnreadCount).toHaveBeenCalledWith('BLOG_POST');
    });

    it('falls back to the settings, without a Blogpost tab, when XTM Hub provisions no blogpost', async () => {
      mockFetchSettings.mockResolvedValue({ data: WITHOUT_BLOG_POSTS });
      renderPage('blog_posts');
      expect(await screen.findByRole('switch', { name: ENABLE_ALL })).toBeInTheDocument();
      expect(screen.queryByRole('tab', { name: /Blogpost/ })).not.toBeInTheDocument();
      expect(screen.queryByTestId('news-feed-blog-posts')).not.toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute('aria-current', 'page');
    });

    it('shows no panel while it does not know whether XTM Hub provisions blogposts', () => {
      mockFetchSettings.mockReturnValue(new Promise(() => {}));
      renderPage('blog_posts');
      expect(screen.queryByTestId('news-feed-blog-posts')).not.toBeInTheDocument();
      expect(screen.queryByRole('switch')).not.toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Settings' })).not.toHaveAttribute('aria-current');
    });

    it('shows the settings, not the blogposts, under the Settings tab', async () => {
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: ENABLE_ALL })).toBeInTheDocument();
      expect(screen.queryByTestId('news-feed-blog-posts')).not.toBeInTheDocument();
      expect(screen.getByRole('tab', { name: 'Settings' })).toHaveAttribute('aria-current', 'page');
    });
  });

  describe('XTM Hub banner', () => {
    it.each(['blog_posts', 'settings'] as const)('invites to connect the product on the %s tab when the tenant is not registered', async (tab) => {
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

    it.each(['blog_posts', 'settings'] as const)('asks a user who cannot connect XTM Hub to ask an administrator, with no way to connect, on the %s tab', async (tab) => {
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

    it.each(['blog_posts', 'settings'] as const)('invites to reconnect on the %s tab when the platform lost its connectivity', async (tab) => {
      // still registered, so the news feed keeps being served, but no longer connected
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_hub_connected: false }) });
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Connect product to XTM Hub' })).toBeInTheDocument();
    });

    it.each(['blog_posts', 'settings'] as const)('says XTM Hub is unreachable, with no way to connect, on the %s tab', async (tab) => {
      xtmHubReachable = false;
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByText('XTM Hub is unreachable and connection can\'t be established')).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Connect product to XTM Hub' })).not.toBeInTheDocument();
    });

    it('says XTM Hub is unreachable to a user who cannot connect XTM Hub too', async () => {
      xtmHubReachable = false;
      canManageTenantSettings = false;
      renderPage('blog_posts');
      expect(await screen.findByText('XTM Hub is unreachable and connection can\'t be established')).toBeInTheDocument();
    });

    it('is not shown when the tenant is registered', async () => {
      renderPage('blog_posts');
      await waitFor(() => expect(mockFetchSettings).toHaveBeenCalled());
      expect(screen.queryByText(DISCONNECTED)).not.toBeInTheDocument();
    });

    it('is not shown while the settings are loading or when they fail to load', async () => {
      mockFetchSettings.mockRejectedValue(new Error('boom'));
      renderPage('blog_posts');
      expect(await screen.findByText('Error')).toBeInTheDocument();
      expect(screen.queryByText(DISCONNECTED)).not.toBeInTheDocument();
    });
  });

  describe('settings', () => {
    it('shows the type the user is subscribed to as enabled', async () => {
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Scenario' })).toBeChecked();
      expect(screen.getByRole('switch', { name: 'Blogpost' })).not.toBeChecked();
      expect(screen.getByRole('switch', { name: ENABLE_ALL })).toBeChecked();
    });

    it('lists every known type until XTM Hub told which types it provisions', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_available_types: [] }) });
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Scenario' })).toBeChecked();
      expect(screen.getByRole('switch', { name: 'Blogpost' })).not.toBeChecked();
    });

    it('turns the global switch off when the user is subscribed to none of the listed types', async () => {
      mockFetchSettings.mockResolvedValue({
        data: buildSettings({
          news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'],
          news_feed_settings_available_types: ['BLOG_POST'],
        }),
      });
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Blogpost' })).not.toBeChecked();
      expect(screen.getByRole('switch', { name: ENABLE_ALL })).not.toBeChecked();
    });

    it('lists only the types XTM Hub provisions', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_available_types: ['BLOG_POST'] }) });
      renderPage('settings');
      expect(await screen.findByRole('switch', { name: 'Blogpost' })).toBeInTheDocument();
      expect(screen.queryByRole('switch', { name: 'Scenario' })).not.toBeInTheDocument();
    });

    it('subscribes to the blogposts when their switch is turned on', async () => {
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Blogpost' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO', 'BLOG_POST'] }, false));
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
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO', 'BLOG_POST'] }, false));
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
