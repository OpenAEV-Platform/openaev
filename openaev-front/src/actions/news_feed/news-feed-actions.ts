import { simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NewsFeedSettingsInput, SearchPaginationInput } from '../../utils/api-types';

const NEWS_FEED_URI = '/api/news-feed';
const NEWS_FEED_SETTINGS_URI = '/api/news-feed-settings';

export const searchMyNewsFeedItems = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${NEWS_FEED_URI}/me/search`, searchPaginationInput);
};

export const fetchMyNewsFeedSettings = () => {
  return simpleCall(`${NEWS_FEED_SETTINGS_URI}/me`);
};

export const updateMyNewsFeedSettings = (input: NewsFeedSettingsInput) => {
  return simplePutCall(`${NEWS_FEED_SETTINGS_URI}/me`, input);
};
