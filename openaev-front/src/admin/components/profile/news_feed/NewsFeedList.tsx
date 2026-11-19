import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArticleOutlined, CampaignOutlined, OpenInNewOutlined, RouteOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useEffect, useRef, useState } from 'react';

import { searchMyNewsFeedItems } from '../../../../actions/news_feed/news-feed-actions';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import SimpleError from '../../../../components/SimpleError';
import { type NewsFeedItemOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { XTM_HUB_DEFAULT_URL } from '../../../../utils/Environment';
import useAuth from '../../../../utils/hooks/useAuth';
import TopBarIconLink from '../../nav/TopBarIconLink';
import { buildHubUrl, NEWS_FEED_BLOG_POST_TYPE } from './newsFeedUtils';
import useNewsFeedStreamRefresh from './useNewsFeedStreamRefresh';

const MAX_DISPLAYED_TAGS = 3;

const inlineStyles: Record<string, CSSProperties> = {
  news_feed_item_type: { width: '12%' },
  news_feed_item_title: { width: '36%' },
  news_feed_item_creation_date: { width: '22%' },
  news_feed_item_tags: { width: '22%' },
  news_feed_item_hub_action: { width: '8%' },
};

/** The resources of the news feed: the blogposts have their own tab. */
const NewsFeedList = () => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, fldt } = useFormatter();
  const { settings } = useAuth();
  const [items, setItems] = useState<NewsFeedItemOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [failed, setFailed] = useState<boolean>(false);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'my-news-feed',
    buildSearchPagination({ sorts: initSorting('news_feed_item_creation_date', 'DESC') }),
  );

  // Only the latest request may flag a failure: PaginationComponentV2 already ignores the
  // loading state and the content of a superseded one.
  const latestRequest = useRef(0);
  const loadsInFlight = useRef(0);
  const refreshPending = useRef(false);
  // Set from an effect, so that it survives the double mount of the strict mode.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // Refetches the list in place, with no loading placeholders: the rows the user looks at do not blink
  // out for a change elsewhere. It runs on the latest search of the user, and a request that a newer one
  // superseded, or that ends after the list is gone, is dropped. It never starts while a load is in
  // flight, whoever asks (the stream or the timer below): PaginationComponentV2 would apply the rows of
  // that older load over the refreshed ones. The refetch is made once the loads are over.
  const refreshItems = () => {
    if (loadsInFlight.current > 0) {
      refreshPending.current = true;
      return;
    }
    const requestId = ++latestRequest.current;
    searchMyNewsFeedItems(searchPaginationInput, false, 'RESOURCE')
      .then(({ data }) => {
        if (!mounted.current || requestId !== latestRequest.current) {
          return;
        }
        setFailed(false);
        setItems(data.content);
        queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
        // The current page fell past the end because items were deleted: back to the first page.
        if (data.pageable.pageNumber > 0 && data.totalPages <= data.pageable.pageNumber) {
          queryableHelpers.paginationHelpers.handleChangePage(0);
        }
      })
      // Deliberate: a failed refetch keeps the rows shown, and the next change or visit refreshes them.
      .catch(() => {});
  };
  const latestRefreshItems = useRef(refreshItems);
  latestRefreshItems.current = refreshItems;

  const searchItemsToLoad = (input: SearchPaginationInput) => {
    const requestId = ++latestRequest.current;
    loadsInFlight.current += 1;
    setFailed(false);
    return searchMyNewsFeedItems(input, true, 'RESOURCE')
      .catch((error) => {
        if (requestId === latestRequest.current) {
          setItems([]);
          setFailed(true);
        }
        throw error;
      })
      .finally(() => {
        loadsInFlight.current -= 1;
        // A change streamed while a load was in flight is refetched once it ends: the slower load could
        // otherwise put back rows the change removed. A timer, so that PaginationComponentV2 has shown the
        // rows of the load by then and the refetch has the last word.
        if (loadsInFlight.current === 0 && refreshPending.current) {
          refreshPending.current = false;
          setTimeout(() => {
            if (mounted.current) {
              latestRefreshItems.current();
            }
          }, 0);
        }
      });
  };

  // A change streamed to the user (the collector updated or deleted an item, or created one) is shown
  // without a reload.
  useNewsFeedStreamRefresh(refreshItems);

  const hubUrl = (item: NewsFeedItemOutput) => (
    item.news_feed_item_url_path
      ? buildHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url ?? XTM_HUB_DEFAULT_URL)
      : null
  );

  const typeLabel = (item: NewsFeedItemOutput) => {
    if (item.news_feed_item_type === NEWS_FEED_BLOG_POST_TYPE) {
      return t('Blogpost');
    }
    return item.news_feed_item_type === 'RESOURCE_SCENARIO' ? t('Scenario') : t('Unsupported type');
  };

  const headers = [
    {
      field: 'news_feed_item_type',
      label: 'Type',
      isSortable: true,
      value: (item: NewsFeedItemOutput) => typeLabel(item),
    },
    {
      field: 'news_feed_item_title',
      label: 'Title',
      isSortable: true,
      value: (item: NewsFeedItemOutput) => item.news_feed_item_title,
    },
    {
      field: 'news_feed_item_creation_date',
      label: 'Creation date',
      isSortable: true,
      value: (item: NewsFeedItemOutput) => fldt(item.news_feed_item_creation_date),
    },
    {
      field: 'news_feed_item_tags',
      label: 'Tags',
      isSortable: false,
      value: (item: NewsFeedItemOutput) => {
        const tags = item.news_feed_item_tags ?? [];
        if (tags.length === 0) {
          return '-';
        }
        return (
          <div style={{
            display: 'flex',
            gap: 4,
            minWidth: 0,
          }}
          >
            {tags.slice(0, MAX_DISPLAYED_TAGS).map(tag => (
              <Chip key={tag} severity="info" label={tag} style={{ maxWidth: '100%' }} />
            ))}
            {tags.length > MAX_DISPLAYED_TAGS && (
              <Chip severity="info" label={`+${tags.length - MAX_DISPLAYED_TAGS}`} />
            )}
          </div>
        );
      },
    },
    {
      field: 'news_feed_item_hub_action',
      label: '',
      isSortable: false,
      value: (item: NewsFeedItemOutput) => {
        const url = hubUrl(item);
        if (!url) {
          return '-';
        }
        return (
          <Tooltip>
            {/* TopBarIconLink does not forward the trigger's ref and handlers: a span carries them. */}
            <TooltipTrigger asChild>
              <span className="inline-flex">
                <TopBarIconLink
                  aria-label={t('Open in XTM Hub')}
                  href={url}
                  icon={<OpenInNewOutlined fontSize="small" />}
                />
              </span>
            </TooltipTrigger>
            <TooltipContent>{t('Open in XTM Hub')}</TooltipContent>
          </Tooltip>
        );
      },
    },
  ];

  return (
    <>
      <PaginationComponentV2
        fetch={searchItemsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setItems}
        setLoading={setLoading}
        queryableHelpers={queryableHelpers}
        entityPrefix="news_feed_item"
        disableFilters
      />
      <List>
        <ListItem sx={{
          textTransform: 'uppercase',
          paddingTop: 0,
        }}
        >
          <ListItemIcon style={{ minWidth: 40 }} />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading && <PaginatedListLoader Icon={CampaignOutlined} headers={headers} headerStyles={inlineStyles} />}
        {!loading && failed && <SimpleError />}
        {!loading && !failed && items.length === 0 && <Empty message={t('No news feed items')} icon={CampaignOutlined} />}
        {!loading && !failed && items.map((item) => {
          const TypeIcon = item.news_feed_item_type === NEWS_FEED_BLOG_POST_TYPE ? ArticleOutlined : RouteOutlined;
          return (
            <ListItem key={item.news_feed_item_id} divider sx={{ height: 50 }}>
              <ListItemIcon style={{ minWidth: 40 }}>
                <TypeIcon fontSize="small" color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div style={bodyItemsStyles.bodyItems}>
                    {headers.map(header => (
                      <div
                        key={header.field}
                        style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles[header.field],
                        }}
                      >
                        {header.value(item)}
                      </div>
                    ))}
                  </div>
                )}
              />
            </ListItem>
          );
        })}
      </List>
    </>
  );
};

export default NewsFeedList;
