import { useEffect, useRef, useState } from 'react';

import { type NewsFeedCategory, searchMyNewsFeedItems } from '../../../../actions/news_feed/news-feed-actions';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import type { NewsFeedItemOutput } from '../../../../utils/api-types';
import useNewsFeedStreamRefresh from './useNewsFeedStreamRefresh';

// XTM Hub provisions the latest items only: a safety net, not a pagination.
const MAX_ITEMS = 50;

const NEWEST_FIRST_SEARCH = buildSearchPagination({
  size: MAX_ITEMS,
  sorts: initSorting('news_feed_item_creation_date', 'DESC'),
});

/**
 * The items of a category XTM Hub provisioned for the user, newest first: `null` until the first load, `failed` when
 * the load failed. An item the collector created, changed or deleted is shown without a reload.
 */
const useNewsFeedItems = (category: NewsFeedCategory) => {
  const [items, setItems] = useState<NewsFeedItemOutput[] | null>(null);
  const [failed, setFailed] = useState(false);
  // Only the latest request counts: a slower older one must not put back the items a newer one removed.
  const latestRequest = useRef(0);
  const mounted = useRef(true);

  const loadItems = () => {
    const requestId = ++latestRequest.current;
    searchMyNewsFeedItems(NEWEST_FIRST_SEARCH, false, category)
      .then(({ data }) => {
        if (mounted.current && requestId === latestRequest.current) {
          setItems(data.content);
          setFailed(false);
        }
      })
      .catch(() => {
        // A failed refresh keeps the items shown; only a first load without any shows the error.
        if (mounted.current && requestId === latestRequest.current) {
          setFailed(true);
        }
      });
  };

  useEffect(() => {
    mounted.current = true;
    loadItems();
    return () => {
      mounted.current = false;
    };
  }, []);

  useNewsFeedStreamRefresh(loadItems);

  return {
    items,
    failed,
  };
};

export default useNewsFeedItems;
