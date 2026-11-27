import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { type NewsFeedItemOutput, type PlatformSettings, type User } from '../../../../../utils/api-types';
import { UserContext } from '../../../../../utils/hooks/useAuth';

const mocks = vi.hoisted(() => ({
  searchMyNewsFeedItems: vi.fn(),
  streamHandlers: new Map<string, (event: MessageEvent) => void>(),
}));

vi.mock('../../../../../actions/news_feed/news-feed-actions', () => ({ searchMyNewsFeedItems: mocks.searchMyNewsFeedItems }));
// The tests resolve the empty, legacy Empty.jsx before Empty.tsx, as in the other news feed tests
vi.mock('../../../../../components/Empty', () => ({ default: ({ message }: { message: string }) => <p>{message}</p> }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({
  subscribeStreamEvent: (type: string, handler: (event: MessageEvent) => void) => {
    mocks.streamHandlers.set(type, handler);
    return () => mocks.streamHandlers.delete(type);
  },
}));

const { default: NewsFeedBlogPosts } = await import('../../../../../admin/components/profile/news_feed/NewsFeedBlogPosts');
const { NEWS_FEED_ITEM_SCHEMA, NEWS_FEED_STREAM_BATCH_MS } = await import('../../../../../admin/components/nav/newsFeedStream');

const PLATFORM_ID = 'platform-1';
const INFO = 'The news feed shows the latest six blogposts';

const buildBlogPost = (id: string, title: string): NewsFeedItemOutput => ({
  news_feed_item_id: id,
  news_feed_item_hub_id: `hub-${id}`,
  news_feed_item_is_read: false,
  news_feed_item_type: 'BLOG_POST',
  news_feed_item_title: title,
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: [],
  news_feed_item_url_path: `/redirect/blog/${id}`,
  news_feed_item_metadata: [],
});

const answer = (content: NewsFeedItemOutput[]) => ({ data: { content } });

const renderBlogPosts = () => render(
  <IntlProvider locale="en" onError={() => {}}>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <UserContext.Provider
          value={{
            me: {} as User,
            settings: {
              xtm_hub_url: 'https://hub.example.com',
              platform_id: PLATFORM_ID,
            } as PlatformSettings,
            isXTMHubAccessible: true,
            userTenants: [],
            currentUserTenant: null,
            switchUserTenant: async () => {},
            reloadUserTenants: async () => {},
          }}
        >
          <NewsFeedBlogPosts />
        </UserContext.Provider>
      </TooltipProvider>
    </ThemeProvider>
  </IntlProvider>,
);

describe('NewsFeedBlogPosts', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}

        disconnect() {}
      };
    }
  });

  beforeEach(() => {
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([buildBlogPost('1', 'First blogpost'), buildBlogPost('2', 'Second blogpost')]));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    mocks.streamHandlers.clear();
  });

  it('searches only the blogposts, the newest first', async () => {
    // Act
    renderBlogPosts();
    await screen.findByText('First blogpost');

    // Assert
    expect(mocks.searchMyNewsFeedItems).toHaveBeenCalledTimes(1);
    const [input, notifyError, category] = mocks.searchMyNewsFeedItems.mock.calls[0];
    expect(category).toBe('BLOG_POST');
    expect(notifyError).toBe(false);
    expect(input.sorts).toEqual([{
      property: 'news_feed_item_creation_date',
      direction: 'DESC',
    }]);
  });

  it('shows a card per blogpost under the information that only the latest six are shown', async () => {
    // Act
    renderBlogPosts();

    // Assert
    expect(await screen.findByRole('heading', { name: 'First blogpost' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Second blogpost' })).toBeInTheDocument();
    expect(screen.getByText(INFO)).toBeInTheDocument();
  });

  it('links to the Filigran blog in a new tab, with the UTM of the news feed, the product as source, and no platform id', async () => {
    // Act
    renderBlogPosts();

    // Assert
    const link = await screen.findByRole('link', { name: 'See all' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    const url = new URL(link.getAttribute('href') as string);
    expect(url.origin + url.pathname).toBe('https://filigran.io/blog');
    expect(url.searchParams.get('utm_source')).toBe('openaev');
    expect(url.searchParams.get('utm_medium')).toBe('newsfeed');
    expect(url.searchParams.get('utm_campaign')).toBe('blog_posts');
    expect(url.searchParams.get('utm_content')).toBe('see_all');
    expect(url.searchParams.has('platform_id')).toBe(false);
  });

  it('shows the empty state when there is no blogpost', async () => {
    // Arrange
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([]));

    // Act
    renderBlogPosts();

    // Assert
    expect(await screen.findByText('No news feed items')).toBeInTheDocument();
    expect(screen.getByText(INFO)).toBeInTheDocument();
  });

  it('shows an error when the blogposts cannot be loaded', async () => {
    // Arrange
    mocks.searchMyNewsFeedItems.mockRejectedValue(new Error('unavailable'));

    // Act
    renderBlogPosts();

    // Assert
    expect(await screen.findByText('Error')).toBeInTheDocument();
  });

  it('reloads the blogposts when a news feed item reaches the stream', async () => {
    // Arrange
    renderBlogPosts();
    await screen.findByText('First blogpost');
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([buildBlogPost('3', 'New blogpost')]));

    // Act
    act(() => mocks.streamHandlers.get('message')?.({
      data: JSON.stringify({
        event_type: 'DATA_FETCH_SUCCESS',
        attribute_schema: NEWS_FEED_ITEM_SCHEMA,
        instance: { news_feed_item_id: '3' },
      }),
    } as MessageEvent));

    // Assert
    await waitFor(() => expect(screen.getByText('New blogpost')).toBeInTheDocument(), { timeout: NEWS_FEED_STREAM_BATCH_MS * 3 });
    expect(screen.queryByText('First blogpost')).not.toBeInTheDocument();
  });
});
