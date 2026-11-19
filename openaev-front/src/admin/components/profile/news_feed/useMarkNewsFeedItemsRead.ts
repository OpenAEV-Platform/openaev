import { useEffect } from 'react';

import { markAllMyNewsFeedItemsRead, type NewsFeedCategory } from '../../../../actions/news_feed/news-feed-actions';
import { MESSAGING$ } from '../../../../utils/Environment';

/**
 * Marks the news feed items of the user as read once they are displayed, only those of `category` when there is
 * one, then tells the top bar to refresh its unread counter. A failure is silent and keeps the counter: the items
 * stay unread, and the next visit marks them again.
 *
 * `visitKey` identifies a navigation to the page (the router's `location.key`): it marks the items again when
 * the user comes back to the page they are already on, e.g. by clicking the top bar icon to see a new item.
 */
const useMarkNewsFeedItemsRead = (displayed: boolean, visitKey?: string, category?: NewsFeedCategory) => {
  useEffect(() => {
    if (!displayed) {
      return;
    }
    markAllMyNewsFeedItemsRead(category)
      .then(() => MESSAGING$.refreshNewsFeedUnreadCount.next())
      .catch(() => {});
  }, [displayed, visitKey, category]);
};

export default useMarkNewsFeedItemsRead;
