import { useEffect } from 'react';

import { markAllMyNewsFeedItemsRead } from '../../../../actions/news_feed/news-feed-actions';
import { MESSAGING$ } from '../../../../utils/Environment';

/**
 * Marks all the news feed items of the user as read once the items are displayed, then tells the top bar
 * to refresh its unread counter. A failure is silent and keeps the counter: the items stay unread, and
 * the next visit marks them again.
 *
 * `visitKey` identifies a navigation to the page (the router's `location.key`): it marks the items again when
 * the user comes back to the page they are already on, e.g. by clicking the top bar icon to see a new item.
 */
const useMarkNewsFeedItemsRead = (displayed: boolean, visitKey?: string) => {
  useEffect(() => {
    if (!displayed) {
      return;
    }
    markAllMyNewsFeedItemsRead()
      .then(() => MESSAGING$.refreshNewsFeedUnreadCount.next())
      .catch(() => {});
  }, [displayed, visitKey]);
};

export default useMarkNewsFeedItemsRead;
