import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { act, cleanup, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_FETCH_SUCCESS } from '../../../../constants/ActionTypes';
import { expectNoMuiControls } from '../../../utils/designSystemAssertions';

const mocks = vi.hoisted(() => ({
  enabled: false,
  fetchMyUnreadNewsFeedCount: vi.fn(),
  streamHandlers: new Map<string, (event: MessageEvent) => void>(),
}));

vi.mock('../../../../utils/utils', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  isFeatureEnabled: () => mocks.enabled,
}));
vi.mock('../../../../actions/news_feed/news-feed-actions', () => ({ fetchMyUnreadNewsFeedCount: mocks.fetchMyUnreadNewsFeedCount }));
vi.mock('../../../../utils/hooks/useDataLoader', () => ({
  subscribeStreamEvent: (type: string, handler: (event: MessageEvent) => void) => {
    mocks.streamHandlers.set(type, handler);
    return () => mocks.streamHandlers.delete(type);
  },
}));

const { default: TopBarNewsFeed } = await import('../../../../admin/components/nav/TopBarNewsFeed');
const { NEWS_FEED_ITEM_SCHEMA, NEWS_FEED_STREAM_BATCH_MS } = await import('../../../../admin/components/nav/newsFeedStream');
const { MESSAGING$ } = await import('../../../../utils/Environment');

const renderMegaphone = (path = '/admin') => render(
  <MemoryRouter initialEntries={[path]}>
    <IntlProvider locale="en" messages={{}}>
      <TooltipProvider>
        <TopBarNewsFeed />
      </TooltipProvider>
    </IntlProvider>
  </MemoryRouter>,
);

const megaphone = () => screen.getByRole('link', { name: 'News feed' });

const streamEvent = (schema: string) => ({
  data: JSON.stringify({
    event_type: DATA_FETCH_SUCCESS,
    attribute_schema: schema,
    instance: { news_feed_item_id: 'item' },
  }),
} as MessageEvent);

const pushEvent = (event: MessageEvent) => {
  act(() => mocks.streamHandlers.get('message')?.(event));
};

const openStream = () => {
  act(() => mocks.streamHandlers.get('open')?.(new Event('open') as MessageEvent));
};

/** Lets the pending count request resolve and the batch timer elapse. */
const flushRefresh = async () => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(NEWS_FEED_STREAM_BATCH_MS);
  });
};

describe('TopBarNewsFeed', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.enabled = true;
    mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 0 });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.clearAllMocks();
    mocks.enabled = false;
    mocks.streamHandlers.clear();
  });

  it('links to the news feed page when the feature is enabled', async () => {
    const { container } = renderMegaphone();
    await flushRefresh();
    expect(megaphone()).toHaveAttribute('href', '/admin/profile/news-feed');
    expectNoMuiControls(container, 'the news feed megaphone');
  });

  it('is current on the news feed page', async () => {
    renderMegaphone('/admin/profile/news-feed');
    await flushRefresh();
    expect(megaphone()).toHaveAttribute('aria-current', 'page');
  });

  it('renders nothing and loads nothing when the feature is disabled', async () => {
    mocks.enabled = false;
    const { container } = renderMegaphone();
    await flushRefresh();
    expect(container).toBeEmptyDOMElement();
    expect(mocks.fetchMyUnreadNewsFeedCount).not.toHaveBeenCalled();
    expect(mocks.streamHandlers.size).toBe(0);
  });

  describe('unread counter', () => {
    it('shows the unread count, announced as the description of the link', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 3 });
      renderMegaphone();
      await flushRefresh();
      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 3');
      expect(megaphone()).toHaveAccessibleName('News feed');
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('caps a large count', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 250 });
      renderMegaphone();
      await flushRefresh();
      expect(screen.getByText('99+')).toBeInTheDocument();
    });

    it('shows nothing when everything is read, or when the tenant is not registered', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 0 });
      renderMegaphone();
      await flushRefresh();
      expect(megaphone()).toHaveAccessibleDescription('');
      expect(screen.queryByText('0')).not.toBeInTheDocument();
    });

    it('keeps no count when it cannot be loaded', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockRejectedValue(new Error('unavailable'));
      renderMegaphone();
      await flushRefresh();
      expect(megaphone()).toHaveAccessibleDescription('');
    });

    it('refreshes the count from the server when a news feed item reaches the stream', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValueOnce({ data: 3 });
      renderMegaphone();
      await flushRefresh();
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 4 });

      pushEvent(streamEvent(NEWS_FEED_ITEM_SCHEMA));
      await flushRefresh();

      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 4');
    });

    it('refreshes the count once for a burst of items', async () => {
      renderMegaphone();
      await flushRefresh();
      mocks.fetchMyUnreadNewsFeedCount.mockClear();

      pushEvent(streamEvent(NEWS_FEED_ITEM_SCHEMA));
      pushEvent(streamEvent(NEWS_FEED_ITEM_SCHEMA));
      pushEvent(streamEvent(NEWS_FEED_ITEM_SCHEMA));
      await flushRefresh();

      expect(mocks.fetchMyUnreadNewsFeedCount).toHaveBeenCalledTimes(1);
    });

    it('ignores the stream events of other entities', async () => {
      renderMegaphone();
      await flushRefresh();
      mocks.fetchMyUnreadNewsFeedCount.mockClear();

      pushEvent(streamEvent('notifications'));
      await flushRefresh();

      expect(mocks.fetchMyUnreadNewsFeedCount).not.toHaveBeenCalled();
    });

    it('refreshes the count once the news feed page marked the items read', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValueOnce({ data: 3 });
      renderMegaphone();
      await flushRefresh();
      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 3');
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 0 });

      act(() => MESSAGING$.refreshNewsFeedUnreadCount.next());
      await flushRefresh();

      expect(megaphone()).toHaveAccessibleDescription('');
      expect(screen.queryByText('3')).not.toBeInTheDocument();
    });

    it('keeps the latest count when the answers of two refreshes arrive in reverse order', async () => {
      renderMegaphone();
      await flushRefresh();
      const answers: ((value: { data: number }) => void)[] = [];
      mocks.fetchMyUnreadNewsFeedCount.mockImplementation(() => new Promise((resolve) => {
        answers.push(resolve);
      }));

      act(() => MESSAGING$.refreshNewsFeedUnreadCount.next());
      act(() => MESSAGING$.refreshNewsFeedUnreadCount.next());
      expect(answers).toHaveLength(2);
      await act(async () => {
        answers[1]({ data: 5 });
        await vi.advanceTimersByTimeAsync(0);
      });
      await act(async () => {
        answers[0]({ data: 2 });
        await vi.advanceTimersByTimeAsync(0);
      });

      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 5');
    });

    it('refreshes the count when the stream reconnects, without any item message', async () => {
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValueOnce({ data: 3 });
      renderMegaphone();
      await flushRefresh();
      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 3');
      // Items were created while the tab was disconnected: no message told it.
      mocks.fetchMyUnreadNewsFeedCount.mockResolvedValue({ data: 5 });

      openStream();
      await flushRefresh();

      expect(megaphone()).toHaveAccessibleDescription('Unread news feed items: 5');
    });

    it('does not load the count twice when the first connection opens right after the mount', async () => {
      renderMegaphone();
      expect(mocks.fetchMyUnreadNewsFeedCount).toHaveBeenCalledTimes(1);

      openStream();
      await flushRefresh();

      expect(mocks.fetchMyUnreadNewsFeedCount).toHaveBeenCalledTimes(1);
    });

    it('stops listening once unmounted', async () => {
      const { unmount } = renderMegaphone();
      await flushRefresh();
      unmount();
      expect(mocks.streamHandlers.has('message')).toBe(false);
      expect(mocks.streamHandlers.has('open')).toBe(false);
      expect(mocks.streamHandlers.size).toBe(0);
      mocks.fetchMyUnreadNewsFeedCount.mockClear();

      act(() => MESSAGING$.refreshNewsFeedUnreadCount.next());

      expect(mocks.fetchMyUnreadNewsFeedCount).not.toHaveBeenCalled();
    });
  });
});
