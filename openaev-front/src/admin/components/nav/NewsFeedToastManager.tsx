import { IconButton, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Close, OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchMyNewsFeedItems } from '../../../actions/news_feed/news-feed-actions';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { DATA_FETCH_SUCCESS } from '../../../constants/ActionTypes';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import useAuth from '../../../utils/hooks/useAuth';
import { subscribeStreamEvent } from '../../../utils/hooks/useDataLoader';
import { isFeatureEnabled } from '../../../utils/utils';
import { buildConfiguredHubUrl } from '../profile/news_feed/newsFeedUtils';
import { NEWS_FEED_STREAM_BATCH_MS, parseNewsFeedItemStreamEvent } from './newsFeedStream';
import NewsFeedToastItem, { type NewsFeedToastData } from './NewsFeedToastItem';
import { useNewsFeedToastStyles } from './newsFeedToastSurface';

export const MAX_VISIBLE_TOASTS = 5;

/** Toasts the news feed items the collector creates for the current user, until they are dismissed. */
const NewsFeedToastManager: FunctionComponent = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { surface, card } = useNewsFeedToastStyles();
  const enabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');
  const [toasts, setToasts] = useState<NewsFeedToastData[]>([]);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    let active = true;
    let pendingItems: NewsFeedToastData[] = [];
    let batchTimer: ReturnType<typeof setTimeout> | undefined;
    const flush = () => {
      const items = pendingItems;
      pendingItems = [];
      // Items carry no tenant and reach the user whatever tenant they browse: the search of the current
      // tenant answers an empty page when that tenant is not registered on XTM Hub.
      searchMyNewsFeedItems(buildSearchPagination({ size: 1 }), false)
        .then(({ data }) => {
          if (!active || data.content.length === 0) {
            return;
          }
          setToasts(previous => items.reduce(
            (toastList, item) => (toastList.some(toast => toast.news_feed_item_id === item.news_feed_item_id) ? toastList : [...toastList, item]),
            previous,
          ));
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
  const hiddenCount = toasts.length - MAX_VISIBLE_TOASTS;
  const hubLibraryUrl = buildConfiguredHubUrl('/app', settings.xtm_hub_url);

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
              <IconButton priority="tertiary" aria-label={t('Dismiss all')} icon={<Close fontSize="small" />} onClick={() => setToasts([])} />
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
        {hiddenCount > 0 && hubLibraryUrl && (
          <div style={card}>
            <Text
              variant="content-compact-bold"
              style={{
                flex: 1,
                marginLeft: theme.spacing(1),
                color: theme.palette.primary.main,
              }}
            >
              {t('(+{count}) Click to view all the new resources on the Hub', { count: hiddenCount })}
            </Text>
            <Tooltip>
              <TooltipTrigger asChild>
                <IconButton
                  asChild
                  priority="tertiary"
                  size="sm"
                  aria-label={t('View all on XTM Hub')}
                  icon={<OpenInNewOutlined fontSize="small" />}
                >
                  <a href={hubLibraryUrl} target="_blank" rel="noopener noreferrer" />
                </IconButton>
              </TooltipTrigger>
              <TooltipContent>{t('View all on XTM Hub')}</TooltipContent>
            </Tooltip>
          </div>
        )}
      </div>
    </section>
  );
};

export default NewsFeedToastManager;
