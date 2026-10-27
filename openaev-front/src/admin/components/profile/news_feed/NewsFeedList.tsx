import { Button, Chip } from '@filigran/design-system';
import { CampaignOutlined, MovieFilterOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useState } from 'react';

import { searchMyNewsFeedItems } from '../../../../actions/news_feed/news-feed-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type NewsFeedItemOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { XTM_HUB_DEFAULT_URL } from '../../../../utils/Environment';
import useAuth from '../../../../utils/hooks/useAuth';
import { getUrl } from '../../../../utils/utils';

const MAX_DISPLAYED_TAGS = 3;

const inlineStyles: Record<string, CSSProperties> = {
  news_feed_item_type: { width: '18%' },
  news_feed_item_title: { width: '32%' },
  news_feed_item_creation_date: { width: '16%' },
  news_feed_item_tags: { width: '22%' },
  news_feed_item_hub_action: { width: '12%' },
};

const NewsFeedList = () => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, fldt } = useFormatter();
  const { settings } = useAuth();
  const [items, setItems] = useState<NewsFeedItemOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'my-news-feed',
    buildSearchPagination({ sorts: initSorting('news_feed_item_creation_date', 'DESC') }),
  );

  const searchItemsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchMyNewsFeedItems(input).finally(() => setLoading(false));
  };

  const hubUrl = (item: NewsFeedItemOutput) => (
    item.news_feed_item_url_path
      ? getUrl(item.news_feed_item_url_path, settings.xtm_hub_url ?? XTM_HUB_DEFAULT_URL)
      : null
  );

  const typeLabel = (item: NewsFeedItemOutput) => (
    item.news_feed_item_type === 'RESOURCE_SCENARIO' ? t('Scenario library') : '-'
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
          <Button size="sm" priority="secondary" asChild>
            <a href={url} target="_blank" rel="noreferrer" title={t('Open in XTM Hub')}>
              <OpenInNewOutlined fontSize="small" />
              {t('Open in XTM Hub')}
            </a>
          </Button>
        );
      },
    },
  ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('News feed'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchItemsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setItems}
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
        {!loading && items.length === 0 && <Empty message={t('No news feed items')} icon={CampaignOutlined} />}
        {!loading && items.map(item => (
          <ListItem key={item.news_feed_item_id} divider sx={{ height: 50 }}>
            <ListItemIcon style={{ minWidth: 40 }}>
              <MovieFilterOutlined fontSize="small" />
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
