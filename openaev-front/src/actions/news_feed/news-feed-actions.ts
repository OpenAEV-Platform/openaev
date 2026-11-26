import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NewsFeedSettingsInput, SearchPaginationInput } from '../../utils/api-types';

const NEWS_FEED_URI = '/api/news-feed';
const NEWS_FEED_SETTINGS_URI = '/api/news-feed-settings';

/** The blogposts, the GitHub releases, or the resources (every other type): each category is read, and counted unread, on its own. */
export type NewsFeedCategory = 'BLOG_POST' | 'GITHUB_RELEASE' | 'RESOURCE';

// Without a category, the request is the one of all the items.
const categoryConfig = (category?: NewsFeedCategory) => (category ? { params: { category } } : undefined);

export const searchMyNewsFeedItems = (searchPaginationInput: SearchPaginationInput, notifyError = true, category?: NewsFeedCategory) => {
  return simplePostCall(`${NEWS_FEED_URI}/me/search`, searchPaginationInput, categoryConfig(category), notifyError);
};

/** The count only refreshes a badge: an error is not notified by default, and still rejects for the caller to handle. */
export const fetchMyUnreadNewsFeedCount = (notifyError = false, category?: NewsFeedCategory) => {
  return simpleCall(`${NEWS_FEED_URI}/me/unread-count`, categoryConfig(category), notifyError);
};

/** Neither the success nor the failure is notified: marking the items read is a side effect of visiting the page. */
export const markAllMyNewsFeedItemsRead = (category?: NewsFeedCategory) => {
  return simplePutCall(`${NEWS_FEED_URI}/me/read-all`, undefined, categoryConfig(category), false, false);
};

export const fetchMyNewsFeedSettings = () => {
  return simpleCall(`${NEWS_FEED_SETTINGS_URI}/me`);
};

export const updateMyNewsFeedSettings = (input: NewsFeedSettingsInput, notifyError = true) => {
  return simplePutCall(`${NEWS_FEED_SETTINGS_URI}/me`, input, undefined, notifyError);
};
