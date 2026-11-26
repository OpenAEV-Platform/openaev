import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type NewsFeedItemOutput, type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext } from '../../../../utils/hooks/useAuth';

// The router and the blogposts are the real ones: a visit must load them and mark them read once, not in a loop.
// Only the network is replaced.
const {
  mockSearchMyNewsFeedItems,
  mockMarkAllRead,
  mockFetchSettings,
  mockFetchUnreadCount,
} = vi.hoisted(() => ({
  mockSearchMyNewsFeedItems: vi.fn(),
  mockMarkAllRead: vi.fn(),
  mockFetchSettings: vi.fn(),
  mockFetchUnreadCount: vi.fn(),
}));

vi.mock('../../../../utils/utils', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  isFeatureEnabled: () => true,
}));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));
vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({
  searchMyNewsFeedItems: mockSearchMyNewsFeedItems,
  markAllMyNewsFeedItemsRead: mockMarkAllRead,
  fetchMyNewsFeedSettings: mockFetchSettings,
  fetchMyUnreadNewsFeedCount: mockFetchUnreadCount,
  updateMyNewsFeedSettings: vi.fn(),
}));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');

const PAGE = '/admin/profile/news-feed';
const OTHER_PAGE = '/admin/home';
const TOP_BAR_LINK = 'Top bar icon';
const OTHER_PAGE_LINK = 'Other page';
const OTHER_PAGE_TEXT = 'Another page';
const SETTLE_MS = 400;
// A page that settles navigates once (the initial location)
const MAX_NAVIGATIONS = 40;
const BLOG_POST_TITLE = 'Ransomware double extortion';

const buildItem = (): NewsFeedItemOutput => ({
  news_feed_item_id: 'item-1',
  news_feed_item_hub_id: 'hub-1',
  news_feed_item_is_read: false,
  news_feed_item_type: 'BLOG_POST',
  news_feed_item_title: BLOG_POST_TITLE,
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: [],
  news_feed_item_metadata: [],
});

const navigations: string[] = [];

/**
 * Records every navigation the router makes, whatever its cause. A page that loops would keep this test
 * busy for ever, so it stops the loop with an error instead.
 */
const NavigationLog = () => {
  const {
    key,
    pathname,
    search,
  } = useLocation();
  const entry = `${key} ${pathname}${search}`;
  if (!navigations.includes(entry)) {
    navigations.push(entry);
  }
  if (navigations.length > MAX_NAVIGATIONS) {
    throw new Error(`The page navigates in a loop: ${navigations.length} navigations`);
  }
  return <output data-testid="search">{search}</output>;
};

const renderPage = (initialEntry = PAGE) => render(
  <MemoryRouter initialEntries={[initialEntry]}>
    <IntlProvider locale="en" onError={() => {}}>
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <UserContext.Provider
            value={{
              me: {} as User,
              settings: { xtm_hub_reachable: true } as PlatformSettings,
              isXTMHubAccessible: true,
              userTenants: [],
              currentUserTenant: null,
              switchUserTenant: async () => {},
              reloadUserTenants: async () => {},
            }}
          >
            <Link to={PAGE}>{TOP_BAR_LINK}</Link>
            <Link to={OTHER_PAGE}>{OTHER_PAGE_LINK}</Link>
            <NavigationLog />
            <Routes>
              <Route path={PAGE} element={<NewsFeed tab="blog_posts" />} />
              <Route path={OTHER_PAGE} element={<p>{OTHER_PAGE_TEXT}</p>} />
            </Routes>
          </UserContext.Provider>
        </TooltipProvider>
      </ThemeProvider>
    </IntlProvider>
  </MemoryRouter>,
);

/** Lets a loop, if any, run: it needs nothing but time to repeat itself. */
const settle = async () => {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, SETTLE_MS));
  });
};

describe('NewsFeed page visit', () => {
  beforeEach(() => {
    navigations.length = 0;
    mockSearchMyNewsFeedItems.mockResolvedValue({ data: { content: [buildItem()] } });
    mockMarkAllRead.mockResolvedValue({});
    mockFetchUnreadCount.mockResolvedValue({ data: 0 });
    mockFetchSettings.mockResolvedValue({
      data: {
        news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO', 'BLOG_POST', 'GITHUB_RELEASE'],
        news_feed_settings_hub_registered: true,
        news_feed_settings_hub_connected: true,
        news_feed_settings_available_types: ['RESOURCE_SCENARIO', 'BLOG_POST', 'GITHUB_RELEASE'],
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('opening the page', () => {
    it('settles: the blogposts and resources are marked read once, and the page does not navigate', async () => {
      // Act
      renderPage();
      await screen.findByText(BLOG_POST_TITLE);
      await settle();

      // Assert: a remount per navigation would repeat these calls without end
      expect(mockMarkAllRead).toHaveBeenCalledTimes(2);
      expect(mockMarkAllRead).toHaveBeenCalledWith('BLOG_POST');
      expect(mockMarkAllRead).toHaveBeenCalledWith('RESOURCE');
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      expect(navigations).toHaveLength(1);
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    });
  });

  describe('visiting the page again', () => {
    it('reloads the blogposts and marks the items read again when the page is clicked while shown', async () => {
      // Arrange
      renderPage();
      await screen.findByText(BLOG_POST_TITLE);
      await settle();
      expect(mockMarkAllRead).toHaveBeenCalledTimes(2);

      // Act
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(4));
      await settle();

      // Assert: once more, not in a loop
      expect(mockMarkAllRead).toHaveBeenCalledTimes(4);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
    });

    it('reloads the blogposts and marks the items read again when the user comes back from another page', async () => {
      // Arrange
      renderPage();
      await screen.findByText(BLOG_POST_TITLE);
      await settle();
      fireEvent.click(screen.getByRole('link', { name: OTHER_PAGE_LINK }));
      await settle();

      // Act
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(4));
      await settle();

      // Assert
      expect(mockMarkAllRead).toHaveBeenCalledTimes(4);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
    });
  });
});
