import { simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput } from '../../utils/api-types';

const NEWS_FEED_URI = '/api/news-feed';

export const searchMyNewsFeedItems = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${NEWS_FEED_URI}/me/search`, searchPaginationInput);
};
