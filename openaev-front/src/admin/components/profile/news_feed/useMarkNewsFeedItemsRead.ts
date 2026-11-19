import { useEffect } from 'react';

import { markAllMyNewsFeedItemsRead, type NewsFeedCategory } from '../../../../actions/news_feed/news-feed-actions';
import { MESSAGING$ } from '../../../../utils/Environment';

/** Every item, whatever its category, in a single request. */
export const ALL_NEWS_FEED_ITEMS = 'ALL';

/** The categories whose items are marked read, each on its own, or every item at once. */
export type NewsFeedItemsToMark = readonly NewsFeedCategory[] | typeof ALL_NEWS_FEED_ITEMS;

/**
 * Marks the news feed items of the user as read once they are displayed, those of `itemsToMark`, then tells the top
 * bar to refresh its unread counter. A failure is silent and keeps the counter: the items stay unread, and the next
 * visit marks them again.
 *
 * `visitKey` identifies a navigation to the page (the router's `location.key`): it marks the items again when
 * the user comes back to the page they are already on, e.g. by clicking the top bar icon to see a new item.
 */
const useMarkNewsFeedItemsRead = (displayed: boolean, visitKey: string | undefined, itemsToMark: NewsFeedItemsToMark) => {
  // A value, not the list itself: a list built at each render must not mark the items again.
  const itemsToMarkKey = itemsToMark === ALL_NEWS_FEED_ITEMS ? ALL_NEWS_FEED_ITEMS : itemsToMark.join(',');

  useEffect(() => {
    if (!displayed) {
      return;
    }
    const requests = itemsToMark === ALL_NEWS_FEED_ITEMS
      ? [markAllMyNewsFeedItemsRead()]
      : itemsToMark.map(category => markAllMyNewsFeedItemsRead(category));
    Promise.all(requests)
      .then(() => MESSAGING$.refreshNewsFeedUnreadCount.next())
      .catch(() => {});
  }, [displayed, visitKey, itemsToMarkKey]);
};

export default useMarkNewsFeedItemsRead;
