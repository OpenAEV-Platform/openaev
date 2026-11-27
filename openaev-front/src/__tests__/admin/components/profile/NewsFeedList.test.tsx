import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../../constants/ActionTypes';
import { type NewsFeedItemOutput, type PlatformSettings, type SearchPaginationInput, type User } from '../../../../utils/api-types';
import { UserContext } from '../../../../utils/hooks/useAuth';

const { mockSearchMyNewsFeedItems, streamHandlers } = vi.hoisted(() => ({
  mockSearchMyNewsFeedItems: vi.fn(),
  streamHandlers: new Map<string, (event: MessageEvent) => void>(),
}));

// The stream is the only part of the data loader the list uses: a stand-in lets the tests push events.
vi.mock('../../../../utils/hooks/useDataLoader', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  subscribeStreamEvent: (type: string, handler: (event: MessageEvent) => void) => {
    streamHandlers.set(type, handler);
    return () => streamHandlers.delete(type);
  },
}));

// Only the network is replaced: the pagination, search, sort and local storage stack is the real one.
vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({ searchMyNewsFeedItems: mockSearchMyNewsFeedItems }));
vi.mock('../../../../actions/schema/schema-action', () => ({
  filterableProperties: () => Promise.resolve({ data: [] }),
  engineSchemas: () => Promise.resolve({ data: [] }),
}));

// vitest resolves the empty `components/Empty.jsx` before `Empty.tsx` (the app build prefers `.tsx`),
// which leaves the component undefined: a stand-in keeps the empty message observable.
vi.mock('../../../../components/Empty', () => ({ default: ({ message }: { message: string }) => <p>{message}</p> }));

// The attack matrix kill chains (store backed) only serve the filters, which this list disables.
vi.mock('../../../../admin/components/common/filters/useKillChains', () => ({
  default: () => ({
    killChains: [],
    activeKillChain: undefined,
    selectKillChain: () => {},
  }),
}));

const { default: NewsFeedList } = await import('../../../../admin/components/profile/news_feed/NewsFeedList');
const { NEWS_FEED_ITEM_SCHEMA, NEWS_FEED_STREAM_BATCH_MS } = await import('../../../../admin/components/nav/newsFeedStream');

// -- FIXTURES --

const HUB_URL = 'https://hub.example.com';
const DEFAULT_HUB_URL = 'https://hub.filigran.io';
const OPEN_IN_HUB = 'Open in XTM Hub';
const NO_ITEMS = 'No news feed items';

const buildItem = (overrides: Partial<NewsFeedItemOutput> = {}): NewsFeedItemOutput => ({
  news_feed_item_id: 'item-1',
  news_feed_item_type: 'RESOURCE_SCENARIO',
  news_feed_item_title: 'Ransomware double extortion',
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: [],
  news_feed_item_url_path: '/scenarios/ransomware-double-extortion',
  ...overrides,
} as NewsFeedItemOutput);

const buildPage = (content: NewsFeedItemOutput[], totalElements = content.length) => ({
  data: {
    content,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 20)),
    pageable: { pageNumber: 0 },
  },
});

const deferred = () => {
  let resolve!: (value: ReturnType<typeof buildPage>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<ReturnType<typeof buildPage>>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return {
    promise,
    resolve,
    reject,
  };
};

// -- HELPERS --

const renderList = (
  settings: Partial<PlatformSettings> = { xtm_hub_url: HUB_URL },
  messages: Record<string, string> = {},
) => render(
  <MemoryRouter initialEntries={['/admin/profile/news-feed']}>
    <IntlProvider locale="en" messages={messages} onError={() => {}}>
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <UserContext.Provider
            value={{
              me: {} as User,
              settings: settings as PlatformSettings,
              isXTMHubAccessible: true,
              userTenants: [],
              currentUserTenant: null,
              switchUserTenant: async () => {},
              reloadUserTenants: async () => {},
            }}
          >
            <NewsFeedList />
          </UserContext.Provider>
        </TooltipProvider>
      </ThemeProvider>
    </IntlProvider>
  </MemoryRouter>,
);

const lastSearch = (): SearchPaginationInput => mockSearchMyNewsFeedItems.mock.calls.at(-1)![0];

const rowOf = (title: string) => screen.getByText(title).closest('li') as HTMLElement;

describe('NewsFeedList', () => {
  beforeEach(() => {
    localStorage.clear();
    mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([]));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('Listing the items of the user', () => {
    it('asks for the newest items first on arrival', async () => {
      // Act
      renderList();

      // Assert
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());
      expect(lastSearch().sorts).toEqual([{
        property: 'news_feed_item_creation_date',
        direction: 'DESC',
      }]);
    });

    it('shows the type, title, creation date and tags of each item', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([
        buildItem({ news_feed_item_tags: ['ransomware', 'finance'] }),
      ]));

      // Act
      renderList();

      // Assert
      const row = await screen.findByText('Ransomware double extortion').then(el => el.closest('li') as HTMLElement);
      expect(within(row).getByText('Scenario')).toBeInTheDocument();
      expect(within(row).getByText(/March 15, 2026/)).toBeInTheDocument();
      expect(within(row).getByText('ransomware')).toBeInTheDocument();
      expect(within(row).getByText('finance')).toBeInTheDocument();
    });

    it('lists every returned item, one row each', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([
        buildItem({
          news_feed_item_id: 'a',
          news_feed_item_title: 'First scenario',
        }),
        buildItem({
          news_feed_item_id: 'b',
          news_feed_item_title: 'Second scenario',
        }),
      ]));

      // Act
      renderList();

      // Assert
      expect(await screen.findByText('First scenario')).toBeInTheDocument();
      expect(screen.getByText('Second scenario')).toBeInTheDocument();
      expect(screen.queryByText(NO_ITEMS)).not.toBeInTheDocument();
    });

    it('labels an item type it does not know as unsupported', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([
        buildItem({ news_feed_item_type: 'RESOURCE_UNKNOWN' as NewsFeedItemOutput['news_feed_item_type'] }),
      ]));

      // Act
      renderList();

      // Assert
      await screen.findByText('Ransomware double extortion');
      const row = rowOf('Ransomware double extortion');
      expect(within(row).getByText('Unsupported type')).toBeInTheDocument();
      expect(within(row).queryByText('Scenario')).not.toBeInTheDocument();
    });

    it('translates the unsupported type label in the language of the user', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([
        buildItem({ news_feed_item_type: 'RESOURCE_UNKNOWN' as NewsFeedItemOutput['news_feed_item_type'] }),
      ]));

      // Act
      renderList(undefined, { 'Unsupported type': 'Type non pris en charge' });

      // Assert
      await screen.findByText('Ransomware double extortion');
      expect(within(rowOf('Ransomware double extortion')).getByText('Type non pris en charge')).toBeInTheDocument();
      expect(screen.queryByText('Unsupported type')).not.toBeInTheDocument();
    });
  });

  describe('Tags', () => {
    it('shows a dash when the item has no tag', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_tags: [] })]));

      // Act
      renderList();

      // Assert
      await screen.findByText('Ransomware double extortion');
      expect(within(rowOf('Ransomware double extortion')).getAllByText('-').length).toBeGreaterThan(0);
    });

    it('shows every tag up to three without a counter', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_tags: ['one', 'two', 'three'] })]));

      // Act
      renderList();

      // Assert
      await screen.findByText('Ransomware double extortion');
      const row = rowOf('Ransomware double extortion');
      ['one', 'two', 'three'].forEach(tag => expect(within(row).getByText(tag)).toBeInTheDocument());
      expect(within(row).queryByText(/^\+\d+$/)).not.toBeInTheDocument();
    });

    it('keeps the first three tags and counts the hidden ones', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([
        buildItem({ news_feed_item_tags: ['one', 'two', 'three', 'four', 'five'] }),
      ]));

      // Act
      renderList();

      // Assert
      await screen.findByText('Ransomware double extortion');
      const row = rowOf('Ransomware double extortion');
      ['one', 'two', 'three'].forEach(tag => expect(within(row).getByText(tag)).toBeInTheDocument());
      expect(within(row).queryByText('four')).not.toBeInTheDocument();
      expect(within(row).queryByText('five')).not.toBeInTheDocument();
      expect(within(row).getByText('+2')).toBeInTheDocument();
    });
  });

  describe('Open in XTM Hub', () => {
    const linkOfTheItemWith = async (urlPath: string | undefined, settings?: Partial<PlatformSettings>) => {
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_url_path: urlPath })]));
      renderList(settings);
      await screen.findByText('Ransomware double extortion');
      return within(rowOf('Ransomware double extortion')).queryByRole('link', { name: OPEN_IN_HUB });
    };

    it('links the item to its page on the configured XTM Hub, in a new tab', async () => {
      // Act
      const link = await linkOfTheItemWith('/scenarios/ransomware-double-extortion');

      // Assert
      expect(link).toHaveAttribute('href', `${HUB_URL}/scenarios/ransomware-double-extortion`);
      expect(link).toHaveAttribute('target', '_blank');
      expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    });

    it('falls back to the public XTM Hub when the platform has no Hub url configured', async () => {
      // Act
      const link = await linkOfTheItemWith('/scenarios/ransomware-double-extortion', {});

      // Assert
      expect(link).toHaveAttribute('href', `${DEFAULT_HUB_URL}/scenarios/ransomware-double-extortion`);
    });

    it('accepts an absolute url that stays on the Hub origin', async () => {
      // Act
      const link = await linkOfTheItemWith(`${HUB_URL}/scenarios/abc`);

      // Assert
      expect(link).toHaveAttribute('href', `${HUB_URL}/scenarios/abc`);
    });

    it('offers no link when the item has no url path', async () => {
      // Act
      const link = await linkOfTheItemWith(undefined);

      // Assert
      expect(link).not.toBeInTheDocument();
    });

    it.each([
      ['an absolute url on another host', 'https://evil.example.org/scenarios/abc'],
      ['a protocol-relative url on another host', '//evil.example.org/scenarios/abc'],
      ['a host that only looks like the Hub', 'https://hub.example.com.evil.org/scenarios/abc'],
      ['a javascript url', 'javascript:alert(1)'],
      ['a non web url', 'ftp://hub.example.com/scenarios/abc'],
    ])('offers no link for %s, since the path comes from XTM Hub data', async (_label, urlPath) => {
      // Act
      const link = await linkOfTheItemWith(urlPath);

      // Assert
      expect(link).not.toBeInTheDocument();
    });

    it('names the icon-only link for assistive technologies', async () => {
      // Act
      const link = await linkOfTheItemWith('/scenarios/abc');

      // Assert
      expect(link).toHaveAccessibleName(OPEN_IN_HUB);
    });
  });

  describe('Loading, empty and error states', () => {
    it('shows neither the empty message nor an error while the items are loading', async () => {
      // Arrange
      const pending = deferred();
      mockSearchMyNewsFeedItems.mockReturnValue(pending.promise);

      // Act
      renderList();

      // Assert
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());
      expect(screen.queryByText(NO_ITEMS)).not.toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(screen.getAllByRole('button', { name: 'More actions' }).length).toBeGreaterThan(0);

      // Cleanup: let the request settle
      pending.resolve(buildPage([]));
      expect(await screen.findByText(NO_ITEMS)).toBeInTheDocument();
    });

    it('replaces the loading placeholders by the items once they arrive', async () => {
      // Arrange
      const pending = deferred();
      mockSearchMyNewsFeedItems.mockReturnValue(pending.promise);
      renderList();
      await waitFor(() => expect(screen.getAllByRole('button', { name: 'More actions' }).length).toBeGreaterThan(0));

      // Act
      pending.resolve(buildPage([buildItem()]));

      // Assert
      expect(await screen.findByText('Ransomware double extortion')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'More actions' })).not.toBeInTheDocument();
    });

    it('tells the user there is nothing to show when the user has no item', async () => {
      // Act
      renderList();

      // Assert
      expect(await screen.findByText(NO_ITEMS)).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('shows an error instead of an empty list when the search fails', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockRejectedValue(new Error('boom'));

      // Act
      renderList();

      // Assert
      expect(await screen.findByRole('alert')).toBeInTheDocument();
      expect(screen.queryByText(NO_ITEMS)).not.toBeInTheDocument();
    });

    it('recovers from an error when a later search succeeds', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockRejectedValueOnce(new Error('boom'));
      renderList();
      await screen.findByRole('alert');
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()]));

      // Act
      fireEvent.click(screen.getByText('Title'));

      // Assert
      expect(await screen.findByText('Ransomware double extortion')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('does not report an error for a search that a newer one has superseded', async () => {
      // Arrange
      const first = deferred();
      const second = deferred();
      mockSearchMyNewsFeedItems.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
      renderList();
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1));
      fireEvent.click(screen.getByText('Title'));
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2));

      // Act
      second.resolve(buildPage([buildItem()]));
      await screen.findByText('Ransomware double extortion');
      first.reject(new Error('late failure'));

      // Assert
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(screen.getByText('Ransomware double extortion')).toBeInTheDocument();
    });
  });

  describe('Searching and sorting', () => {
    it('searches the items by title as the user types', async () => {
      // Arrange
      renderList();
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());

      // Act
      fireEvent.change(screen.getByRole('searchbox', { name: /search/i }), { target: { value: 'ransom' } });

      // Assert
      await waitFor(() => expect(lastSearch().textSearch).toBe('ransom'));
    });

    it('shows the items matching the search', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockImplementation((input: SearchPaginationInput) => Promise.resolve(buildPage(
        input.textSearch === 'phishing'
          ? [buildItem({
              news_feed_item_id: 'p',
              news_feed_item_title: 'Phishing campaign',
            })]
          : [buildItem({
              news_feed_item_id: 'r',
              news_feed_item_title: 'Ransomware double extortion',
            })],
      )));
      renderList();
      await screen.findByText('Ransomware double extortion');

      // Act
      fireEvent.change(screen.getByRole('searchbox', { name: /search/i }), { target: { value: 'phishing' } });

      // Assert
      expect(await screen.findByText('Phishing campaign')).toBeInTheDocument();
      expect(screen.queryByText('Ransomware double extortion')).not.toBeInTheDocument();
    });

    it('tells the user nothing matches when the search finds no item', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockImplementation((input: SearchPaginationInput) => Promise.resolve(buildPage(
        input.textSearch ? [] : [buildItem()],
      )));
      renderList();
      await screen.findByText('Ransomware double extortion');

      // Act
      fireEvent.change(screen.getByRole('searchbox', { name: /search/i }), { target: { value: 'nothing like this' } });

      // Assert
      expect(await screen.findByText(NO_ITEMS)).toBeInTheDocument();
      expect(screen.queryByText('Ransomware double extortion')).not.toBeInTheDocument();
    });

    it.each([
      ['Type', 'news_feed_item_type'],
      ['Title', 'news_feed_item_title'],
      ['Creation date', 'news_feed_item_creation_date'],
    ])('sorts on %s when its column heading is clicked', async (heading, property) => {
      // Arrange
      renderList();
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());

      // Act
      fireEvent.click(screen.getByText(heading));

      // Assert
      await waitFor(() => expect(lastSearch().sorts?.[0]?.property).toBe(property));
    });

    it('reverses the sort direction when the same heading is clicked twice', async () => {
      // Arrange
      renderList();
      await waitFor(() => expect(mockSearchMyNewsFeedItems).toHaveBeenCalled());

      // Act
      fireEvent.click(screen.getByText('Title'));
      await waitFor(() => expect(lastSearch().sorts?.[0]).toEqual({
        property: 'news_feed_item_title',
        direction: 'ASC',
      }));
      fireEvent.click(screen.getByText('Title'));

      // Assert
      await waitFor(() => expect(lastSearch().sorts?.[0]).toEqual({
        property: 'news_feed_item_title',
        direction: 'DESC',
      }));
    });

    it('does not offer to sort on tags', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()]));
      renderList();
      await screen.findByText('Ransomware double extortion');
      const callsBefore = mockSearchMyNewsFeedItems.mock.calls.length;

      // Act
      fireEvent.click(screen.getByText('Tags'));

      // Assert
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(mockSearchMyNewsFeedItems.mock.calls.length).toBe(callsBefore);
    });
  });

  describe('Pagination', () => {
    it('loads the next page when the user goes forward', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()], 120));
      renderList();
      await screen.findByText('Ransomware double extortion');
      expect(lastSearch().page).toBe(0);

      // Act
      fireEvent.click(await screen.findByRole('button', { name: /next page/i }));

      // Assert
      await waitFor(() => expect(lastSearch().page).toBe(1));
    });
  });
  describe('Live refresh', () => {
    const streamEvent = (eventType: string, schema = NEWS_FEED_ITEM_SCHEMA) => ({
      data: JSON.stringify({
        event_type: eventType,
        attribute_schema: schema,
        attribute_id: 'news_feed_item_id',
        instance: { news_feed_item_id: 'item-1' },
        listened: true,
      }),
    } as MessageEvent);

    const pushEvents = async (...events: MessageEvent[]) => {
      await act(async () => {
        events.forEach(event => streamHandlers.get('message')?.(event));
        await vi.advanceTimersByTimeAsync(NEWS_FEED_STREAM_BATCH_MS);
      });
    };

    const renderLoadedList = async (...pages: ReturnType<typeof buildPage>[]) => {
      mockSearchMyNewsFeedItems.mockResolvedValueOnce(pages[0]);
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
    };

    beforeEach(() => {
      vi.useFakeTimers();
      streamHandlers.clear();
    });

    afterEach(() => {
      vi.useRealTimers();
      streamHandlers.clear();
    });

    it('shows the new title of an updated item without a reload', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      expect(screen.getByText('Ransomware double extortion')).toBeInTheDocument();
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_title: 'Renamed scenario' })]));

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS));

      // Assert
      expect(screen.getByText('Renamed scenario')).toBeInTheDocument();
      expect(screen.queryByText('Ransomware double extortion')).not.toBeInTheDocument();
    });

    it('removes a deleted item without a reload', async () => {
      // Arrange
      await renderLoadedList(buildPage([
        buildItem({
          news_feed_item_id: 'a',
          news_feed_item_title: 'Kept scenario',
        }),
        buildItem({
          news_feed_item_id: 'b',
          news_feed_item_title: 'Deleted scenario',
        }),
      ]));
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({
        news_feed_item_id: 'a',
        news_feed_item_title: 'Kept scenario',
      })]));

      // Act
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));

      // Assert
      expect(screen.getByText('Kept scenario')).toBeInTheDocument();
      expect(screen.queryByText('Deleted scenario')).not.toBeInTheDocument();
    });

    it('shows the empty message when the last item is deleted', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([]));

      // Act
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));

      // Assert
      expect(screen.getByText(NO_ITEMS)).toBeInTheDocument();
    });

    it('shows a created item without a reload', async () => {
      // Arrange
      await renderLoadedList(buildPage([]));
      expect(screen.getByText(NO_ITEMS)).toBeInTheDocument();
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()]));

      // Act
      await pushEvents(streamEvent(DATA_FETCH_SUCCESS));

      // Assert
      expect(screen.getByText('Ransomware double extortion')).toBeInTheDocument();
      expect(screen.queryByText(NO_ITEMS)).not.toBeInTheDocument();
    });

    it('keeps the rows on screen while it refetches, with the search of the user and no error notification', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      const refetch = deferred();
      mockSearchMyNewsFeedItems.mockReturnValue(refetch.promise);

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS));

      // Assert: no loading placeholders replace the rows
      expect(screen.getByText('Ransomware double extortion')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'More actions' })).not.toBeInTheDocument();
      expect(lastSearch().sorts).toEqual([{
        property: 'news_feed_item_creation_date',
        direction: 'DESC',
      }]);
      expect(mockSearchMyNewsFeedItems.mock.calls.at(-1)![1]).toBe(false);

      // Cleanup: let the request settle
      await act(async () => {
        refetch.resolve(buildPage([buildItem()]));
      });
    });

    it('refetches once for a burst of events', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      mockSearchMyNewsFeedItems.mockClear();
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()]));

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS), streamEvent(DATA_UPDATE_SUCCESS), streamEvent(DATA_DELETE_SUCCESS));

      // Assert
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
    });

    it('keeps the rows when the refetch fails', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      mockSearchMyNewsFeedItems.mockRejectedValue(new Error('boom'));

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS));

      // Assert
      expect(screen.getByText('Ransomware double extortion')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('ignores the events of other entities', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      mockSearchMyNewsFeedItems.mockClear();

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS, 'injects'));

      // Assert
      expect(mockSearchMyNewsFeedItems).not.toHaveBeenCalled();
    });

    it('refetches when the stream reconnects after a while', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(NEWS_FEED_STREAM_BATCH_MS);
      });
      mockSearchMyNewsFeedItems.mockClear();
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_title: 'Renamed while offline' })]));

      // Act
      await act(async () => {
        streamHandlers.get('open')?.(new Event('open') as MessageEvent);
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert
      expect(screen.getByText('Renamed while offline')).toBeInTheDocument();
    });

    it('goes back to the first page when deletions leave the current page past the end', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()], 120));
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /next page/i }));
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(lastSearch().page).toBe(1);
      // Everything left fits on one page: the page the user is on no longer exists
      mockSearchMyNewsFeedItems.mockResolvedValue({
        data: {
          content: [],
          totalElements: 0,
          totalPages: 1,
          pageable: { pageNumber: 1 },
        },
      });

      // Act
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert
      expect(lastSearch().page).toBe(0);
    });

    it('stays on the current page when it still exists after a change', async () => {
      // Arrange
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()], 120));
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /next page/i }));
        await vi.advanceTimersByTimeAsync(0);
      });
      mockSearchMyNewsFeedItems.mockResolvedValue({
        data: {
          content: [buildItem()],
          totalElements: 120,
          totalPages: 6,
          pageable: { pageNumber: 1 },
        },
      });

      // Act
      await pushEvents(streamEvent(DATA_UPDATE_SUCCESS));

      // Assert
      expect(lastSearch().page).toBe(1);
    });

    it('refetches once the load in flight ends, so that a slower load cannot put back removed rows', async () => {
      // Arrange
      const slowLoad = deferred();
      mockSearchMyNewsFeedItems.mockReturnValueOnce(slowLoad.promise);
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_title: 'After the deletion' })]));

      // Act: a change streams while the load is in flight, which then ends with the older rows
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      await act(async () => {
        slowLoad.resolve(buildPage([buildItem({ news_feed_item_title: 'Before the deletion' })]));
        await vi.advanceTimersByTimeAsync(0);
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
      expect(screen.getByText('After the deletion')).toBeInTheDocument();
      expect(screen.queryByText('Before the deletion')).not.toBeInTheDocument();
    });

    it('does not refetch while a newer load is in flight, so that its older rows cannot overwrite the refreshed ones', async () => {
      // Arrange: a change streams during the first load, whose end schedules the refetch
      const firstLoad = deferred();
      const secondLoad = deferred();
      mockSearchMyNewsFeedItems.mockReturnValueOnce(firstLoad.promise);
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));
      await act(async () => {
        firstLoad.resolve(buildPage([buildItem({ news_feed_item_title: 'First load' })], 120));
        // Microtasks only: the refetch timer must not fire yet
        await Promise.resolve().then().then().then().then().then().then();
      });
      // The user goes to the next page before the refetch timer fires
      mockSearchMyNewsFeedItems.mockReturnValueOnce(secondLoad.promise);
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem({ news_feed_item_title: 'Refreshed rows' })], 120));
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /next page/i }));
      });
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);

      // Act: the timer fires while the second load is in flight, then the second load ends last
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(2);
      await act(async () => {
        secondLoad.resolve(buildPage([buildItem({ news_feed_item_title: 'Older rows' })], 120));
        await vi.advanceTimersByTimeAsync(0);
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert: the refetch ran once that load ended and has the last word
      expect(mockSearchMyNewsFeedItems).toHaveBeenCalledTimes(3);
      expect(screen.getByText('Refreshed rows')).toBeInTheDocument();
      expect(screen.queryByText('Older rows')).not.toBeInTheDocument();
    });

    it('does not reset the page, nor store it, for a refetch that ends after the list is gone', async () => {
      // Arrange: the user is on page 1 and a change is being refetched
      mockSearchMyNewsFeedItems.mockResolvedValue(buildPage([buildItem()], 120));
      renderList();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: /next page/i }));
        await vi.advanceTimersByTimeAsync(0);
      });
      expect(lastSearch().page).toBe(1);
      const refetch = deferred();
      mockSearchMyNewsFeedItems.mockReturnValue(refetch.promise);
      await pushEvents(streamEvent(DATA_DELETE_SUCCESS));
      cleanup();
      mockSearchMyNewsFeedItems.mockClear();
      const store = vi.spyOn(window.localStorage, 'setItem');

      // Act: the page is left, then the refetch ends with the page past the end of the data
      await act(async () => {
        refetch.resolve({
          data: {
            content: [],
            totalElements: 0,
            totalPages: 1,
            pageable: { pageNumber: 1 },
          },
        } as ReturnType<typeof buildPage>);
        await vi.advanceTimersByTimeAsync(0);
      });

      // Assert: the reset to the first page, which would be stored, is not made
      expect(store).not.toHaveBeenCalled();
      expect(mockSearchMyNewsFeedItems).not.toHaveBeenCalled();

      // Cleanup
      store.mockRestore();
    });

    it('stops listening to the stream once the list is gone', async () => {
      // Arrange
      await renderLoadedList(buildPage([buildItem()]));
      expect(streamHandlers.has('message')).toBe(true);

      // Act
      cleanup();

      // Assert
      expect(streamHandlers.size).toBe(0);
    });
  });
});
