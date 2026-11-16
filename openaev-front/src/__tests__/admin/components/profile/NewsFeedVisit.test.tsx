import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type NewsFeedItemOutput, type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext } from '../../../../utils/hooks/useAuth';

// The router, the queryable state (local storage and URL) and the list are the real ones: the page
// loops on their interplay. Only the network and what the list does not use are replaced.
const {
  mockSearchMyNewsFeedItems,
  mockMarkAllRead,
  mockFetchSettings,
} = vi.hoisted(() => ({
  mockSearchMyNewsFeedItems: vi.fn(),
  mockMarkAllRead: vi.fn(),
  mockFetchSettings: vi.fn(),
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
  updateMyNewsFeedSettings: vi.fn(),
}));
vi.mock('../../../../actions/schema/schema-action', () => ({
  filterableProperties: () => Promise.resolve({ data: [] }),
  engineSchemas: () => Promise.resolve({ data: [] }),
}));
vi.mock('../../../../components/Empty', () => ({ default: ({ message }: { message: string }) => <p>{message}</p> }));
vi.mock('../../../../admin/components/common/filters/useKillChains', () => ({
  default: () => ({
    killChains: [],
    activeKillChain: undefined,
    selectKillChain: () => {},
  }),
}));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');

const PAGE = '/admin/profile/news-feed';
const OTHER_PAGE = '/admin/home';
const TOP_BAR_LINK = 'Top bar icon';
const OTHER_PAGE_LINK = 'Other page';
const OTHER_PAGE_TEXT = 'Another page';
const SETTLE_MS = 400;
// A page that settles navigates twice (the initial location, the write of the query of the list)
const MAX_NAVIGATIONS = 40;

const buildItem = (): NewsFeedItemOutput => ({
  news_feed_item_id: 'item-1',
  news_feed_item_hub_id: 'hub-1',
  news_feed_item_is_read: false,
  news_feed_item_type: 'RESOURCE_SCENARIO',
  news_feed_item_title: 'Ransomware double extortion',
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: [],
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
              <Route path={PAGE} element={<NewsFeed tab="items" />} />
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
    localStorage.clear();
    navigations.length = 0;
    mockSearchMyNewsFeedItems.mockResolvedValue({
      data: {
        content: [buildItem()],
        totalElements: 1,
        totalPages: 1,
        pageable: { pageNumber: 0 },
      },
    });
    mockMarkAllRead.mockResolvedValue({});
    mockFetchSettings.mockResolvedValue({
      data: {
        news_feed_settings_subscribed_types: ['RESOURCE_SCENARIO'],
        news_feed_settings_hub_registered: true,
        news_feed_settings_hub_connected: true,
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('opening the page', () => {
    it('settles: the list loads and marks the items read once, and the URL is written once', async () => {
      // Act
      renderPage();
      await screen.findByText('Ransomware double extortion');
      await settle();

      // Assert: a remount per URL write would repeat these calls without end
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      // The initial location, then the single write of the query of the list
      expect(navigations).toHaveLength(2);
      expect(screen.getByTestId('search')).toHaveTextContent('?query=');
    });

    it('settles when the page is opened on a URL that already holds the query of the list', async () => {
      // Arrange
      renderPage();
      await screen.findByText('Ransomware double extortion');
      const storedSearch = screen.getByTestId('search').textContent as string;
      cleanup();
      vi.clearAllMocks();
      navigations.length = 0;
      mockSearchMyNewsFeedItems.mockResolvedValue({
        data: {
          content: [buildItem()],
          totalElements: 1,
          totalPages: 1,
          pageable: { pageNumber: 0 },
        },
      });
      mockMarkAllRead.mockResolvedValue({});

      // Act
      renderPage(`${PAGE}${storedSearch}`);
      await screen.findByText('Ransomware double extortion');
      await settle();

      // Assert
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
    });
  });

  describe('visiting the page again', () => {
    it('reloads the list and marks the items read again when the page is clicked while shown', async () => {
      // Arrange
      renderPage();
      await screen.findByText('Ransomware double extortion');
      await settle();
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);

      // Act
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(2));
      await settle();

      // Assert: once more, not in a loop
      expect(mockMarkAllRead).toHaveBeenCalledTimes(2);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
    });

    it('reloads the list and marks the items read again when the user comes back from another page', async () => {
      // Arrange
      renderPage();
      await screen.findByText('Ransomware double extortion');
      await settle();
      fireEvent.click(screen.getByRole('link', { name: OTHER_PAGE_LINK }));
      await settle();

      // Act
      fireEvent.click(screen.getByRole('link', { name: TOP_BAR_LINK }));
      await waitFor(() => expect(mockMarkAllRead).toHaveBeenCalledTimes(2));
      await settle();

      // Assert
      expect(mockMarkAllRead).toHaveBeenCalledTimes(2);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
    });
  });

  describe('using the list', () => {
    it('sorts, written once to the URL, without reloading the page nor marking the items read again', async () => {
      // Arrange
      renderPage();
      await screen.findByText('Ransomware double extortion');
      await settle();
      const searchBefore = screen.getByTestId('search').textContent;
      mockSearchMyNewsFeedItems.mockClear();

      // Act
      fireEvent.click(screen.getByText('Title'));
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());
      await settle();

      // Assert
      expect(mockSearchMyNewsFeedItems.mock.calls.at(-1)![0].sorts).toEqual([{
        property: 'news_feed_item_title',
        direction: 'ASC',
      }]);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId('search').textContent).not.toBe(searchBefore);
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
    });

    it('goes to the next page, written to the URL, without reloading the page', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue({
        data: {
          content: [buildItem()],
          totalElements: 120,
          totalPages: 6,
          pageable: { pageNumber: 0 },
        },
      });
      renderPage();
      await screen.findByText('Ransomware double extortion');
      await settle();
      mockSearchMyNewsFeedItems.mockClear();

      // Act
      fireEvent.click(await screen.findByRole('button', { name: /next page/i }));
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());
      await settle();

      // Assert
      expect(mockSearchMyNewsFeedItems.mock.calls.at(-1)![0].page).toBe(1);
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
    });
  });
});
