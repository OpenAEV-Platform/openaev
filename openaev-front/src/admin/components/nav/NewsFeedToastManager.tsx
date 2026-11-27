import { IconButton, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArrowForward, Close, OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';

import { searchMyNewsFeedItems } from '../../../actions/news_feed/news-feed-actions';
import { initSorting } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { DATA_FETCH_SUCCESS } from '../../../constants/ActionTypes';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import type { NewsFeedItemOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { subscribeStreamEvent } from '../../../utils/hooks/useDataLoader';
import { isFeatureEnabled } from '../../../utils/utils';
import { NEWS_FEED_PATH } from '../profile/news_feed/NewsFeedTabs';
import { buildConfiguredHubUrl, buildNewsFeedBlogSeeAllUrl, NEWS_FEED_BLOG_POST_TYPE } from '../profile/news_feed/newsFeedUtils';
import { NEWS_FEED_STREAM_BATCH_MS, parseNewsFeedItemStreamEvent } from './newsFeedStream';
import NewsFeedToastItem, { type NewsFeedToastData } from './NewsFeedToastItem';
import { useNewsFeedToastStyles } from './newsFeedToastSurface';

export const MAX_VISIBLE_TOASTS = 5;

// The unread items at login are a safety net, not a pagination: the newest ones are toasted, the others stay
// behind the badge of the news feed.
export const MAX_UNREAD_TOASTS = 50;

// A single item is enough for the registration check of the stream events: only whether the page is empty matters.
export const REGISTRATION_CHECK_SIZE = 1;

// The unread items first (false sorts before true), the newest first among them.
const UNREAD_SEARCH = buildSearchPagination({
  size: MAX_UNREAD_TOASTS,
  sorts: [
    ...initSorting('news_feed_item_is_read', 'ASC'),
    ...initSorting('news_feed_item_creation_date', 'DESC'),
  ],
});

// An item already toasted is not toasted twice, whether it came from the stream or from the unread items.
const addNewToasts = (toasts: NewsFeedToastData[], items: NewsFeedToastData[]) => items.reduce(
  (toastList, item) => (toastList.some(toast => toast.news_feed_item_id === item.news_feed_item_id) ? toastList : [...toastList, item]),
  toasts,
);

/** Toasts the unread news feed items of the current user at login, then those the collector creates, until they are dismissed. */
const NewsFeedToastManager: FunctionComponent = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { surface, card } = useNewsFeedToastStyles();
  const enabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');
  const [toasts, setToasts] = useState<NewsFeedToastData[]>([]);
  // The unread items that arrive after "Dismiss all" were already dismissed by the user.
  const dismissedAll = useRef(false);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let active = true;
    let pendingItems: NewsFeedToastData[] = [];
    let batchTimer: ReturnType<typeof setTimeout> | undefined;
    // This one search lists the unread items and also shows whether the tenant is registered on XTM Hub (an empty
    // page means it is not).
    searchMyNewsFeedItems(UNREAD_SEARCH, false)
      .then(({ data }) => {
        // Not redundant: the backend can sort on the read state but not filter on it, so when there are fewer than
        // MAX_UNREAD_TOASTS unread items, read items fill the rest of the page.
        const unreadItems: NewsFeedToastData[] = data.content.filter((item: NewsFeedItemOutput) => !item.news_feed_item_is_read);
        if (active && !dismissedAll.current && unreadItems.length > 0) {
          setToasts(previous => addNewToasts(previous, unreadItems));
        }
      })
      .catch(() => {});
    const flush = () => {
      const items = pendingItems;
      pendingItems = [];
      // Items carry no tenant and reach the user whatever tenant they browse: the search of the current
      // tenant answers an empty page when that tenant is not registered on XTM Hub.
      searchMyNewsFeedItems(buildSearchPagination({ size: REGISTRATION_CHECK_SIZE }), false)
        .then(({ data }) => {
          if (!active || data.content.length === 0) {
            return;
          }
          setToasts(previous => addNewToasts(previous, items));
        })
        .catch(() => {});
    };
    const unsubscribe = subscribeStreamEvent('message', (event: MessageEvent) => {
      const data = parseNewsFeedItemStreamEvent<NewsFeedToastData>(event);
      if (data === null || data.event_type !== DATA_FETCH_SUCCESS) {
        return;
      }
      pendingItems.push(data.instance);
      if (batchTimer === undefined) {
        batchTimer = setTimeout(() => {
          batchTimer = undefined;
          flush();
        }, NEWS_FEED_STREAM_BATCH_MS);
      }
    });
    return () => {
      active = false;
      unsubscribe();
      clearTimeout(batchTimer);
    };
  }, [enabled]);

  if (!enabled) {
    return null;
  }

  const { bannerHeightNumber } = computeBannerSettings(settings);
  const hiddenToasts = toasts.slice(MAX_VISIBLE_TOASTS);
  const hiddenCount = hiddenToasts.length;
  const isOnlyBlogPostHidden = hiddenCount > 0 && hiddenToasts.every(toast => toast.news_feed_item_type === NEWS_FEED_BLOG_POST_TYPE);
  // Every type but the blogposts is a resource of XTM Hub, including a type this version does not know.
  const isOnlyResourceHidden = hiddenCount > 0 && hiddenToasts.every(toast => toast.news_feed_item_type !== NEWS_FEED_BLOG_POST_TYPE);
  // The blogposts live on the Filigran blog, the resources on XTM Hub, and the news feed of the platform lists both.
  let overflowUrl: string | null = NEWS_FEED_PATH;
  let isOverflowExternal = false;
  let overflowMessage = t('(+{count}) Click to view all the new items', { count: String(hiddenCount) });
  let overflowLinkLabel = t('View all');
  if (isOnlyBlogPostHidden) {
    overflowUrl = buildNewsFeedBlogSeeAllUrl(settings.xtm_hub_url);
    isOverflowExternal = true;
    overflowMessage = t('(+{count}) Click to view all the new blogposts', { count: String(hiddenCount) });
    overflowLinkLabel = t('See all');
  } else if (isOnlyResourceHidden) {
    overflowUrl = buildConfiguredHubUrl('/app', settings.xtm_hub_url);
    isOverflowExternal = true;
    overflowMessage = t('(+{count}) Click to view all the new resources on the Hub', { count: String(hiddenCount) });
    overflowLinkLabel = t('View all on XTM Hub');
  }

  // The live region stays mounted, empty, so that the toasts inserted in it are announced.
  return (
    <section
      aria-label={t('News feed')}
      style={{
        position: 'fixed',
        top: `calc(${bannerHeightNumber}px + var(--fds-header-height, 68px) + ${theme.spacing(1.5)})`,
        right: theme.spacing(2.5),
        // Below the drawers and the Ask Ariane panel, so the toasts slide underneath them.
        zIndex: theme.zIndex.drawer - 10,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-end',
        gap: theme.spacing(1),
        maxWidth: `calc(100vw - ${theme.spacing(5)})`,
        pointerEvents: 'none',
      }}
    >
      {toasts.length > 0 && (
        <div style={{
          ...surface,
          display: 'flex',
          pointerEvents: 'auto',
        }}
        >
          <Tooltip>
            <TooltipTrigger asChild>
              <IconButton
                priority="tertiary"
                aria-label={t('Dismiss all')}
                icon={<Close fontSize="small" />}
                onClick={() => {
                  dismissedAll.current = true;
                  setToasts([]);
                }}
              />
            </TooltipTrigger>
            <TooltipContent>{t('Dismiss all')}</TooltipContent>
          </Tooltip>
        </div>
      )}
      <div
        role="status"
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-end',
          gap: theme.spacing(1),
          maxWidth: '100%',
        }}
      >
        {toasts.slice(0, MAX_VISIBLE_TOASTS).map(toast => (
          <NewsFeedToastItem key={toast.news_feed_item_id} item={toast} />
        ))}
        {hiddenCount > 0 && overflowUrl && (
          <div style={card}>
            <Text
              variant="content-compact-bold"
              style={{
                flex: 1,
                marginLeft: theme.spacing(1),
                color: theme.palette.primary.main,
              }}
            >
              {overflowMessage}
            </Text>
            <Tooltip>
              <TooltipTrigger asChild>
                <IconButton
                  asChild
                  priority="tertiary"
                  size="sm"
                  aria-label={overflowLinkLabel}
                  icon={isOverflowExternal ? <OpenInNewOutlined fontSize="small" /> : <ArrowForward fontSize="small" />}
                >
                  {isOverflowExternal
                    ? <a href={overflowUrl} target="_blank" rel="noopener noreferrer" />
                    : <Link to={overflowUrl} />}
                </IconButton>
              </TooltipTrigger>
              <TooltipContent>{overflowLinkLabel}</TooltipContent>
            </Tooltip>
          </div>
        )}
      </div>
    </section>
  );
};

export default NewsFeedToastManager;
