import { IconButton, Text, Thumbnail, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { NotificationsOutlined, OpenInNewOutlined, RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type NewsFeedItemOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { tint } from '../../../utils/tint';
import { buildConfiguredHubUrl } from '../profile/news_feed/newsFeedUtils';
import { useNewsFeedToastStyles } from './newsFeedToastSurface';

export type NewsFeedToastData = Pick<NewsFeedItemOutput, 'news_feed_item_id' | 'news_feed_item_title' | 'news_feed_item_type' | 'news_feed_item_url_path'>;

interface Props { item: NewsFeedToastData }

const NewsFeedToastItem: FunctionComponent<Props> = ({ item }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { card } = useNewsFeedToastStyles();
  const isScenario = item.news_feed_item_type === 'RESOURCE_SCENARIO';
  const TypeIcon = isScenario ? RouteOutlined : NotificationsOutlined;
  const hubUrl = buildConfiguredHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url);

  return (
    <div style={card}>
      <Thumbnail style={{
        flexShrink: 0,
        backgroundColor: tint(theme.palette.primary.main, 20),
        borderColor: tint(theme.palette.primary.main, 33),
      }}
      >
        <TypeIcon color="primary" />
      </Thumbnail>
      <div style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Text variant="content-compact" style={{ color: theme.palette.primary.main }}>
          {isScenario ? t('New scenario') : t('Unsupported type')}
        </Text>
        <Text variant="content-base-bold" style={{ wordBreak: 'break-word' }}>
          {item.news_feed_item_title}
        </Text>
      </div>
      {hubUrl && (
        <Tooltip>
          <TooltipTrigger asChild>
            <IconButton
              asChild
              priority="tertiary"
              size="sm"
              aria-label={t('Open in XTM Hub')}
              icon={<OpenInNewOutlined fontSize="small" />}
            >
              <a href={hubUrl} target="_blank" rel="noopener noreferrer" />
            </IconButton>
          </TooltipTrigger>
          <TooltipContent>{t('Open in XTM Hub')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};

export default NewsFeedToastItem;
