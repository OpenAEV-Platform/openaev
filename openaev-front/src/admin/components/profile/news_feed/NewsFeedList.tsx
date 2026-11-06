import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CampaignOutlined, OpenInNewOutlined, RouteOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useRef, useState } from 'react';

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

const MAX_DISPLAYED_TAGS = 3;

// The path comes from XTM Hub data: only a link that stays on the XTM Hub origin is rendered.
const buildHubUrl = (urlPath: string, hubUrl: string): string | null => {
  try {
    const base = new URL(hubUrl);
    const url = new URL(urlPath, base);
    const isWeb = url.protocol === 'https:' || url.protocol === 'http:';
    return isWeb && url.origin === base.origin ? url.toString() : null;
  } catch {
    return null;
  }
};

const inlineStyles: Record<string, CSSProperties> = {
  news_feed_item_type: { width: '12%' },
  news_feed_item_title: { width: '36%' },
  news_feed_item_creation_date: { width: '22%' },
  news_feed_item_tags: { width: '22%' },
  news_feed_item_hub_action: { width: '8%' },
};

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
  const searchItemsToLoad = (input: SearchPaginationInput) => {
    const requestId = ++latestRequest.current;
    setFailed(false);
    return searchMyNewsFeedItems(input).catch((error) => {
      if (requestId === latestRequest.current) {
        setItems([]);
        setFailed(true);
      }
      throw error;
    });
  };

  const hubUrl = (item: NewsFeedItemOutput) => (
    item.news_feed_item_url_path
      ? buildHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url ?? XTM_HUB_DEFAULT_URL)
      : null
  );

  const typeLabel = (item: NewsFeedItemOutput) => (
    item.news_feed_item_type === 'RESOURCE_SCENARIO' ? t('Scenario') : t('Unsupported type')
  );

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
        {!loading && !failed && items.map(item => (
          <ListItem key={item.news_feed_item_id} divider sx={{ height: 50 }}>
            <ListItemIcon style={{ minWidth: 40 }}>
              <RouteOutlined fontSize="small" color="primary" />
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
        ))}
      </List>
    </>
  );
};

export default NewsFeedList;
