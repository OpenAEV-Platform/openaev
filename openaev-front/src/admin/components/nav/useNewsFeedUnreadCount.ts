import { useEffect, useState } from 'react';

import { fetchMyUnreadNewsFeedCount } from '../../../actions/news_feed/news-feed-actions';
import { MESSAGING$ } from '../../../utils/Environment';
import { subscribeStreamEvent } from '../../../utils/hooks/useDataLoader';
import { isFeatureEnabled } from '../../../utils/utils';
import { NEWS_FEED_ITEM_SCHEMA } from './NewsFeedToastManager';

/** The stream events of a burst of items are folded into one refresh of the count. */
export const NEWS_FEED_UNREAD_COUNT_REFRESH_MS = 1000;

/**
 * Number of unread news feed items of the current user, 0 when the tenant is not registered on XTM Hub.
 * It is loaded on mount, then refreshed from the server on each news feed item stream event (items reach
 * the user whatever tenant they browse, so the stream alone cannot tell whether they show here), and when
 * the news feed page marked the items read. A failed refresh keeps the last known count.
 */
const useNewsFeedUnreadCount = (): number => {
  const enabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let active = true;
    let latestRequest = 0;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;
    const refresh = () => {
      // Only the latest answer counts: an older request may resolve after a newer one.
      const requestId = ++latestRequest;
      fetchMyUnreadNewsFeedCount()
        .then(({ data }) => {
          if (active && requestId === latestRequest) {
            setCount(data ?? 0);
          }
        })
        .catch(() => {});
    };
    refresh();

    const unsubscribe = subscribeStreamEvent('message', (event: MessageEvent) => {
      if (!event.data.includes(`"${NEWS_FEED_ITEM_SCHEMA}"`)) {
        return;
      }
      const data: { attribute_schema: string } = JSON.parse(event.data);
      if (data.attribute_schema !== NEWS_FEED_ITEM_SCHEMA || refreshTimer !== undefined) {
        return;
      }
      refreshTimer = setTimeout(() => {
        refreshTimer = undefined;
        refresh();
      }, NEWS_FEED_UNREAD_COUNT_REFRESH_MS);
    });
    const subscription = MESSAGING$.refreshNewsFeedUnreadCount.subscribe(refresh);

    return () => {
      active = false;
      unsubscribe();
      subscription.unsubscribe();
      clearTimeout(refreshTimer);
    };
  }, [enabled]);

  return enabled ? count : 0;
};

export default useNewsFeedUnreadCount;
