import '@testing-library/jest-dom/vitest';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type NewsFeedSettingsOutput } from '../../../../utils/api-types';

let enabled = false;
const { mockFetchSettings, mockUpdateSettings } = vi.hoisted(() => ({
  mockFetchSettings: vi.fn(),
  mockUpdateSettings: vi.fn(),
}));

vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => enabled }));
vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({
  fetchMyNewsFeedSettings: mockFetchSettings,
  updateMyNewsFeedSettings: mockUpdateSettings,
}));
vi.mock('../../../../admin/components/profile/news_feed/NewsFeedList', () => ({ default: () => <div data-testid="news-feed-list" /> }));
vi.mock('../../../../components/NotFound', () => ({ default: () => <div data-testid="not-found" /> }));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');

const DISCONNECTED = 'XTM Hub is disconnected';
const ENABLE_ALL = 'Enable News Feed notifications';

const buildSettings = (overrides: Partial<NewsFeedSettingsOutput> = {}): NewsFeedSettingsOutput => ({
  news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'],
  news_feed_settings_hub_registered: true,
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
    mockFetchSettings.mockResolvedValue({ data: buildSettings() });
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

  describe('header', () => {
    it('is titled News feed in the breadcrumbs', () => {
      renderPage('items');
      expect(within(screen.getByRole('navigation')).getByText('News feed')).toBeInTheDocument();
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
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_hub_registered: false }) });
      renderPage(tab);
      expect(await screen.findByText(DISCONNECTED)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Connect product to XTM Hub' })).toHaveAttribute('href', '/redirect/connect-xtm-hub');
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
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }));
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).not.toBeChecked());
    });

    it('subscribes to a type again when its switch is turned back on', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_subscribed_types: [] }) });
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'] }));
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).toBeChecked());
    });

    it('unsubscribes from everything with the global switch', async () => {
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: ENABLE_ALL }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: [] }));
    });

    it('subscribes to every type with the global switch', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_subscribed_types: [] }) });
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: ENABLE_ALL }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalledWith({ news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'] }));
    });

    it('keeps the previous state when the save fails', async () => {
      mockUpdateSettings.mockRejectedValue(new Error('boom'));
      renderPage('settings');
      fireEvent.click(await screen.findByRole('switch', { name: 'Scenario' }));
      await waitFor(() => expect(mockUpdateSettings).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByRole('switch', { name: 'Scenario' })).not.toBeDisabled());
      expect(screen.getByRole('switch', { name: 'Scenario' })).toBeChecked();
    });

    it('cannot be changed when the tenant is not registered', async () => {
      mockFetchSettings.mockResolvedValue({ data: buildSettings({ news_feed_settings_hub_registered: false }) });
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
