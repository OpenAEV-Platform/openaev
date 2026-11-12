import { simplePostCall } from '../../utils/Action';
import type { SearchPaginationInput } from '../../utils/api-types';

const NEWS_FEED_URI = '/api/news-feed';

export const searchMyNewsFeedItems = (searchPaginationInput: SearchPaginationInput, notifyError = true) => {
  return simplePostCall(`${NEWS_FEED_URI}/me/search`, searchPaginationInput, undefined, notifyError);
};
