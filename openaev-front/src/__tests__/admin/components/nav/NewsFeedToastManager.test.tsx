import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_DELETE_SUCCESS, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS } from '../../../../constants/ActionTypes';
import { type PlatformSettings, type User } from '../../../../utils/api-types';
import { UserContext } from '../../../../utils/hooks/useAuth';
import { expectLibraryIconButton, expectNoMuiControls } from '../../../utils/designSystemAssertions';

const mocks = vi.hoisted(() => ({
  enabled: true,
  searchMyNewsFeedItems: vi.fn(),
  streamHandlers: new Map<string, (event: MessageEvent) => void>(),
}));

vi.mock('../../../../utils/utils', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  isFeatureEnabled: () => mocks.enabled,
}));

vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({ searchMyNewsFeedItems: mocks.searchMyNewsFeedItems }));

vi.mock('../../../../utils/hooks/useDataLoader', () => ({
  subscribeStreamEvent: (type: string, handler: (event: MessageEvent) => void) => {
    mocks.streamHandlers.set(type, handler);
    return () => mocks.streamHandlers.delete(type);
  },
}));

const { default: NewsFeedToastManager, MAX_VISIBLE_TOASTS } = await import('../../../../admin/components/nav/NewsFeedToastManager');
const { NEWS_FEED_ITEM_SCHEMA, NEWS_FEED_STREAM_BATCH_MS } = await import('../../../../admin/components/nav/newsFeedStream');

const HUB_URL = 'https://hub.example.com';
const PLATFORM_ID = 'platform-1';

const renderManager = () => render(
  <IntlProvider locale="en" messages={{}} onError={() => {}}>
    <MemoryRouter>
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
          <NewsFeedToastManager />
        </UserContext.Provider>
      </TooltipProvider>
    </MemoryRouter>
  </IntlProvider>,
);

const streamEvent = (
  title: string,
  {
    urlPath = `/cybersecurity-solutions/open-aev-scenarios/${title}`,
    eventType = DATA_FETCH_SUCCESS,
    schema = NEWS_FEED_ITEM_SCHEMA,
    type = 'RESOURCE_SCENARIO',
  }: {
    urlPath?: string;
    eventType?: string;
    schema?: string;
    type?: string;
  } = {},
) => ({
  data: JSON.stringify({
    event_type: eventType,
    attribute_schema: schema,
    attribute_id: 'news_feed_item_id',
    instance: {
      news_feed_item_id: title,
      news_feed_item_title: title,
      news_feed_item_type: type,
      news_feed_item_url_path: urlPath,
    },
    listened: true,
  }),
} as MessageEvent);

const pushEvents = async (...events: MessageEvent[]) => {
  await act(async () => {
    events.forEach(event => mocks.streamHandlers.get('message')?.(event));
    await vi.advanceTimersByTimeAsync(NEWS_FEED_STREAM_BATCH_MS);
  });
};

const searchAnswers = (itemCount: number) => mocks.searchMyNewsFeedItems.mockResolvedValue({ data: { content: Array.from({ length: itemCount }, () => ({})) } });

const liveRegion = () => screen.getByRole('status');

describe('NewsFeedToastManager', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.enabled = true;
    searchAnswers(1);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
    mocks.streamHandlers.clear();
  });

  describe('rendering', () => {
    it('toasts a created item with its type, its title and a link to it on XTM Hub', async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(streamEvent('Ransomware'));

      // Assert
      const toasts = screen.getByRole('region', { name: 'News feed' });
      expect(within(toasts).getByText('New scenario')).toBeInTheDocument();
      expect(within(toasts).getByText('Ransomware')).toBeInTheDocument();
      const link = within(toasts).getByRole('link', { name: 'Open in XTM Hub' });
      expect(link).toHaveAttribute('href', `${HUB_URL}/cybersecurity-solutions/open-aev-scenarios/Ransomware`);
      expect(link).toHaveAttribute('target', '_blank');
      expectLibraryIconButton(link, 'the open in XTM Hub link');
      expectNoMuiControls(toasts, 'the news feed toasts');
    });

    it('announces the toasts through a live region mounted before they arrive', async () => {
      // Arrange
      renderManager();
      const region = liveRegion();
      expect(region).toBeEmptyDOMElement();

      // Act
      await pushEvents(streamEvent('Ransomware'));

      // Assert
      expect(liveRegion()).toBe(region);
      expect(within(region).getByText('Ransomware')).toBeInTheDocument();
      expect(within(region).queryByRole('button', { name: 'Dismiss all' })).not.toBeInTheDocument();
    });

    it('has a single dismiss all control instead of a close control per toast', async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(streamEvent('A'), streamEvent('B'));

      // Assert
      const buttons = screen.getAllByRole('button');
      expect(buttons).toHaveLength(1);
      expect(buttons[0]).toHaveAccessibleName('Dismiss all');
      expectLibraryIconButton(buttons[0], 'the dismiss all control');
    });

    it('keeps the toasts until they are dismissed', async () => {
      // Arrange
      renderManager();
      await pushEvents(streamEvent('Ransomware'));

      // Act
      await act(async () => {
        await vi.advanceTimersByTimeAsync(10 * 60 * 1000);
      });

      // Assert
      expect(within(liveRegion()).getByText('Ransomware')).toBeInTheDocument();
    });

    it('dismisses every toast at once', async () => {
      // Arrange
      renderManager();
      await pushEvents(streamEvent('A'), streamEvent('B'));

      // Act
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Dismiss all' }));
      });

      // Assert
      expect(liveRegion()).toBeEmptyDOMElement();
      expect(screen.queryByRole('button', { name: 'Dismiss all' })).not.toBeInTheDocument();
    });

    it(`shows ${MAX_VISIBLE_TOASTS} toasts at most and links the others to XTM Hub`, async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(...['A', 'B', 'C', 'D', 'E', 'F', 'G'].map(title => streamEvent(title)));

      // Assert
      const region = liveRegion();
      expect(within(region).getAllByRole('link', { name: 'Open in XTM Hub' })).toHaveLength(MAX_VISIBLE_TOASTS);
      expect(within(region).queryByText('F')).not.toBeInTheDocument();
      expect(within(region).getByText('(+2) Click to view all the new resources on the Hub')).toBeInTheDocument();
      expect(within(region).getByRole('link', { name: 'View all on XTM Hub' })).toHaveAttribute('href', `${HUB_URL}/app`);
    });

    it('toasts a blogpost with its label and a link through the XTM Hub redirect', async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(streamEvent('Threat report', {
        type: 'BLOG_POST',
        urlPath: '/redirect/blog/threat-report',
      }));

      // Assert
      const region = liveRegion();
      expect(within(region).getByText('Blogpost')).toBeInTheDocument();
      expect(within(region).queryByText('Unsupported type')).not.toBeInTheDocument();
      expect(within(region).getByRole('link', { name: 'Open on Filigran blog' }))
        .toHaveAttribute('href', `${HUB_URL}/redirect/blog/threat-report?platform_id=${PLATFORM_ID}`);
    });

    describe('overflow banner', () => {
      const pushTypes = (types: string[]) => pushEvents(...types.map((type, index) => streamEvent(`item-${index}`, { type })));

      it('links to the Filigran blog when only blogposts are hidden', async () => {
        // Arrange
        renderManager();

        // Act
        await pushTypes(Array(7).fill('BLOG_POST'));

        // Assert
        const region = liveRegion();
        expect(within(region).getByText('(+2) Click to view all the new blogposts')).toBeInTheDocument();
        expect(within(region).queryByText(/new resources on the Hub/)).not.toBeInTheDocument();
        const url = new URL(within(region).getByRole('link', { name: 'See all' }).getAttribute('href') as string);
        expect(url.origin + url.pathname).toBe('https://filigran.io/blog');
        expect(url.searchParams.get('utm_source')).toBe('openaev');
        expect(url.searchParams.get('utm_content')).toBe('see_all');
        expect(url.searchParams.has('platform_id')).toBe(false);
      });

      it('uses a neutral wording when blogposts and resources are hidden together', async () => {
        // Arrange
        renderManager();

        // Act
        await pushTypes([...Array(5).fill('RESOURCE_SCENARIO'), 'BLOG_POST', 'RESOURCE_SCENARIO']);

        // Assert
        const region = liveRegion();
        expect(within(region).getByText('(+2) Click to view all the new items')).toBeInTheDocument();
        // the blogposts are not in the XTM Hub library: the link goes to the news feed of the platform, in the same tab
        const link = within(region).getByRole('link', { name: 'View all' });
        expect(link).toHaveAttribute('href', '/admin/profile/news-feed');
        expect(link).not.toHaveAttribute('target');
      });

      it('keeps the XTM Hub wording when only resources are hidden', async () => {
        // Arrange
        renderManager();

        // Act
        await pushTypes([...Array(5).fill('BLOG_POST'), 'RESOURCE_SCENARIO', 'RESOURCE_SCENARIO']);

        // Assert
        expect(within(liveRegion()).getByText('(+2) Click to view all the new resources on the Hub')).toBeInTheDocument();
      });
    });

    it('links nowhere when the item has no XTM Hub path', async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(streamEvent('Ransomware', { urlPath: '' }));

      // Assert
      expect(within(liveRegion()).getByText('Ransomware')).toBeInTheDocument();
      expect(within(liveRegion()).queryByRole('link')).not.toBeInTheDocument();
    });
  });

  describe('stream filtering', () => {
    it('toasts each item of a burst once, with a single registration check', async () => {
      // Arrange
      renderManager();

      // Act
      await pushEvents(streamEvent('A'), streamEvent('B'), streamEvent('A'));

      // Assert
      expect(mocks.searchMyNewsFeedItems).toHaveBeenCalledTimes(1);
      expect(within(liveRegion()).getAllByText('A')).toHaveLength(1);
      expect(within(liveRegion()).getByText('B')).toBeInTheDocument();
    });

    describe.each([
      ['an updated item', streamEvent('Ransomware', { eventType: DATA_UPDATE_SUCCESS })],
      ['a deleted item', streamEvent('Ransomware', { eventType: DATA_DELETE_SUCCESS })],
      ['another entity', streamEvent('Ransomware', { schema: 'injects' })],
      ['another entity mentioning the news feed schema', streamEvent(NEWS_FEED_ITEM_SCHEMA, { schema: 'notifications' })],
    ])('given %s', (_, event) => {
      it('shows no toast', async () => {
        // Arrange
        renderManager();

        // Act
        await pushEvents(event);

        // Assert
        expect(mocks.searchMyNewsFeedItems).not.toHaveBeenCalled();
        expect(liveRegion()).toBeEmptyDOMElement();
      });
    });

    it('does not listen to the stream when the feature is disabled', () => {
      // Arrange
      mocks.enabled = false;

      // Act
      renderManager();

      // Assert
      expect(mocks.streamHandlers.size).toBe(0);
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });
  });

  describe('registration check', () => {
    it('shows no toast when the current tenant is not registered on XTM Hub', async () => {
      // Arrange
      searchAnswers(0);
      renderManager();

      // Act
      await pushEvents(streamEvent('Ransomware'));

      // Assert
      expect(mocks.searchMyNewsFeedItems).toHaveBeenCalledWith(expect.objectContaining({ size: 1 }), false);
      expect(liveRegion()).toBeEmptyDOMElement();
    });

    it('shows no toast when the registration check fails', async () => {
      // Arrange
      mocks.searchMyNewsFeedItems.mockRejectedValue(new Error('Network Error'));
      renderManager();

      // Act
      await pushEvents(streamEvent('Ransomware'));

      // Assert
      expect(liveRegion()).toBeEmptyDOMElement();
    });
  });
});
