import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
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

const { default: NewsFeedGithubReleases } = await import('../../../../../admin/components/profile/news_feed/NewsFeedGithubReleases');
const { NEWS_FEED_ITEM_SCHEMA, NEWS_FEED_STREAM_BATCH_MS } = await import('../../../../../admin/components/nav/newsFeedStream');

const PLATFORM_ID = 'platform-1';
const HUB_URL = 'https://hub.example.com';

const buildRelease = (id: string, title: string, overrides: Partial<NewsFeedItemOutput> = {}): NewsFeedItemOutput => ({
  news_feed_item_id: id,
  news_feed_item_hub_id: `hub-${id}`,
  news_feed_item_is_read: false,
  news_feed_item_type: 'GITHUB_RELEASE',
  news_feed_item_title: title,
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: [],
  news_feed_item_url_path: `/redirect/release/${id}`,
  news_feed_item_metadata: [{
    news_feed_item_metadata_key: 'product_version',
    news_feed_item_metadata_value: '2.4.0',
  }],
  ...overrides,
});

const answer = (content: NewsFeedItemOutput[]) => ({ data: { content } });

const renderReleases = () => render(
  <IntlProvider locale="en" onError={() => {}}>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <UserContext.Provider
          value={{
            me: {} as User,
            settings: {
              xtm_hub_url: HUB_URL,
              platform_id: PLATFORM_ID,
            } as PlatformSettings,
            isXTMHubAccessible: true,
            userTenants: [],
            currentUserTenant: null,
            switchUserTenant: async () => {},
            reloadUserTenants: async () => {},
          }}
        >
          <NewsFeedGithubReleases />
        </UserContext.Provider>
      </TooltipProvider>
    </ThemeProvider>
  </IntlProvider>,
);

describe('NewsFeedGithubReleases', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}

        disconnect() {}
      };
    }
  });

  beforeEach(() => {
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([buildRelease('1', 'OpenAEV 2.4.0'), buildRelease('2', 'OpenAEV 2.3.0')]));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    mocks.streamHandlers.clear();
  });

  it('searches only the GitHub releases, the newest first', async () => {
    // Act
    renderReleases();
    await screen.findByText('OpenAEV 2.4.0');

    // Assert
    expect(mocks.searchMyNewsFeedItems).toHaveBeenCalledTimes(1);
    const [input, notifyError, category] = mocks.searchMyNewsFeedItems.mock.calls[0];
    expect(category).toBe('GITHUB_RELEASE');
    expect(notifyError).toBe(false);
    expect(input.sorts).toEqual([{
      property: 'news_feed_item_creation_date',
      direction: 'DESC',
    }]);
  });

  it('shows a row per release with its title, version, date and a link through the XTM Hub redirect', async () => {
    // Act
    renderReleases();

    // Assert
    const table = await screen.findByRole('table', { name: 'GitHub releases' });
    expect(within(table).getAllByRole('columnheader').map(header => header.textContent))
      .toEqual(['GitHub release', 'Version', 'Release date', 'Link']);
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    const firstRow = within(rows[0]);
    expect(firstRow.getByText('OpenAEV 2.4.0')).toBeInTheDocument();
    expect(firstRow.getByText('2.4.0')).toBeInTheDocument();
    const link = firstRow.getByRole('link', { name: 'Open on GitHub: OpenAEV 2.4.0' });
    expect(link).toHaveAttribute('href', `${HUB_URL}/redirect/release/1?platform_id=${PLATFORM_ID}`);
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('shows a dash without a version, and no link when the path leaves XTM Hub', async () => {
    // Arrange
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([buildRelease('1', 'OpenAEV 2.4.0', {
      news_feed_item_metadata: [],
      news_feed_item_url_path: 'https://evil.example.com/release',
    })]));

    // Act
    renderReleases();

    // Assert
    const row = within((await screen.findAllByRole('row'))[1]);
    expect(row.getByText('-')).toBeInTheDocument();
    expect(row.queryByRole('link')).not.toBeInTheDocument();
  });

  it('links to all the releases on GitHub, in a new tab, without going through XTM Hub', async () => {
    // Act
    renderReleases();

    // Assert
    const link = await screen.findByRole('link', { name: 'See all releases' });
    expect(link).toHaveAttribute('href', 'https://github.com/OpenAEV-Platform/openaev/releases');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('shows the empty state, with the link to all the releases, when there is no release', async () => {
    // Arrange
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([]));

    // Act
    renderReleases();

    // Assert
    expect(await screen.findByText('No GitHub releases yet')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'See all releases' })).toBeInTheDocument();
  });

  it('shows an error when the releases cannot be loaded', async () => {
    // Arrange
    mocks.searchMyNewsFeedItems.mockRejectedValue(new Error('unavailable'));

    // Act
    renderReleases();

    // Assert
    expect(await screen.findByText('Error')).toBeInTheDocument();
  });

  it('reloads the releases when a news feed item reaches the stream', async () => {
    // Arrange
    renderReleases();
    await screen.findByText('OpenAEV 2.4.0');
    mocks.searchMyNewsFeedItems.mockResolvedValue(answer([buildRelease('3', 'OpenAEV 2.5.0')]));

    // Act
    act(() => mocks.streamHandlers.get('message')?.({
      data: JSON.stringify({
        event_type: 'DATA_FETCH_SUCCESS',
        attribute_schema: NEWS_FEED_ITEM_SCHEMA,
        instance: { news_feed_item_id: '3' },
      }),
    } as MessageEvent));

    // Assert
    await waitFor(() => expect(screen.getByText('OpenAEV 2.5.0')).toBeInTheDocument(), { timeout: NEWS_FEED_STREAM_BATCH_MS * 3 });
    expect(screen.queryByText('OpenAEV 2.4.0')).not.toBeInTheDocument();
  });
});
