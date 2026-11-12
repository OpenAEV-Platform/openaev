import { useEffect } from 'react';

import { markAllMyNewsFeedItemsRead } from '../../../../actions/news_feed/news-feed-actions';
import { MESSAGING$ } from '../../../../utils/Environment';

/**
 * Marks all the news feed items of the user as read once the items are displayed, then tells the top bar
 * to refresh its unread counter. A failure is silent and keeps the counter: the items stay unread, and
 * the next visit marks them again.
 */
const useMarkNewsFeedItemsRead = (displayed: boolean) => {
  useEffect(() => {
    if (!displayed) {
      return;
    }
    markAllMyNewsFeedItemsRead()
      .then(() => MESSAGING$.refreshNewsFeedUnreadCount.next())
      .catch(() => {});
  }, [displayed]);
};

export default useMarkNewsFeedItemsRead;
