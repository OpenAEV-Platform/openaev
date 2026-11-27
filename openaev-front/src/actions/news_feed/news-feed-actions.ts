import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NewsFeedSettingsInput, SearchPaginationInput } from '../../utils/api-types';

const NEWS_FEED_URI = '/api/news-feed';
const NEWS_FEED_SETTINGS_URI = '/api/news-feed-settings';

export const searchMyNewsFeedItems = (searchPaginationInput: SearchPaginationInput, notifyError = true) => {
  return simplePostCall(`${NEWS_FEED_URI}/me/search`, searchPaginationInput, undefined, notifyError);
};

/** The count only refreshes a badge: an error is not notified by default, and still rejects for the caller to handle. */
export const fetchMyUnreadNewsFeedCount = (notifyError = false) => {
  return simpleCall(`${NEWS_FEED_URI}/me/unread-count`, undefined, notifyError);
};

/** Neither the success nor the failure is notified: marking the items read is a side effect of visiting the page. */
export const markAllMyNewsFeedItemsRead = () => {
  return simplePutCall(`${NEWS_FEED_URI}/me/read-all`, undefined, undefined, false, false);
};

export const fetchMyNewsFeedSettings = () => {
  return simpleCall(`${NEWS_FEED_SETTINGS_URI}/me`);
};

export const updateMyNewsFeedSettings = (input: NewsFeedSettingsInput, notifyError = true) => {
  return simplePutCall(`${NEWS_FEED_SETTINGS_URI}/me`, input, undefined, notifyError);
};
