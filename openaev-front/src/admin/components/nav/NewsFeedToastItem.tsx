import { IconButton, Text, Thumbnail, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ArticleOutlined, CellTowerOutlined, NotificationsOutlined, OpenInNewOutlined, RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type NewsFeedItemOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { tint } from '../../../utils/tint';
import { buildConfiguredHubUrl, buildHubRedirectUrl, NEWS_FEED_BLOG_POST_TYPE, NEWS_FEED_GITHUB_RELEASE_TYPE } from '../profile/news_feed/newsFeedUtils';
import { useNewsFeedToastStyles } from './newsFeedToastSurface';

export type NewsFeedToastData = Pick<NewsFeedItemOutput, 'news_feed_item_id' | 'news_feed_item_title' | 'news_feed_item_type' | 'news_feed_item_url_path'>;

interface Props { item: NewsFeedToastData }

const NewsFeedToastItem: FunctionComponent<Props> = ({ item }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { card } = useNewsFeedToastStyles();
  const isBlogPost = item.news_feed_item_type === NEWS_FEED_BLOG_POST_TYPE;
  const isGithubRelease = item.news_feed_item_type === NEWS_FEED_GITHUB_RELEASE_TYPE;
  const isScenario = item.news_feed_item_type === 'RESOURCE_SCENARIO';
  let TypeIcon = NotificationsOutlined;
  let typeLabel = t('Unsupported type');
  // A blogpost is an article of the Filigran blog, whereas the other types are resources of XTM Hub.
  let openLabel = t('Open in XTM Hub');
  if (isBlogPost) {
    TypeIcon = ArticleOutlined;
    typeLabel = t('Blogpost');
    openLabel = t('Open on Filigran blog');
  } else if (isGithubRelease) {
    TypeIcon = CellTowerOutlined;
    typeLabel = t('GitHub release');
    // A GitHub release lands on GitHub, the redirect of XTM Hub only tells which release.
    openLabel = t('Open on GitHub');
  } else if (isScenario) {
    TypeIcon = RouteOutlined;
    typeLabel = t('New scenario');
  }
  // A blogpost or a GitHub release goes through the XTM Hub redirect, which is told the platform the reader comes from.
  const hubUrl = isBlogPost || isGithubRelease
    ? buildHubRedirectUrl(item.news_feed_item_url_path, settings.xtm_hub_url, settings.platform_id)
    : buildConfiguredHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url);

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
          {typeLabel}
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
              aria-label={openLabel}
              icon={<OpenInNewOutlined fontSize="small" />}
            >
              <a href={hubUrl} target="_blank" rel="noopener noreferrer" />
            </IconButton>
          </TooltipTrigger>
          <TooltipContent>{openLabel}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};

export default NewsFeedToastItem;
