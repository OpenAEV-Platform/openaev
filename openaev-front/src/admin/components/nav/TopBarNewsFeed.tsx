import { Badge, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CampaignOutlined } from '@mui/icons-material';
import { useLocation } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import TopBarIconLink from './TopBarIconLink';
import useNewsFeedUnreadCount from './useNewsFeedUnreadCount';

/**
 * Top bar megaphone: entry point of the XTM Hub news feed, with its unread counter.
 * Only shown when the feature is enabled.
 */
const TopBarNewsFeed = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const enabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');
  const unreadCount = useNewsFeedUnreadCount();

  if (!enabled) {
    return null;
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {/* The Badge wraps the LINK, not the glyph: it describes its child, and the glyph slot is aria-hidden. */}
        <Badge
          content={unreadCount}
          max={99}
          invisible={unreadCount === 0}
          bareAnchor="md"
          accessibleText={unreadCount > 0 ? t('Unread news feed items: {count}', { count: String(unreadCount) }) : false}
        >
          <TopBarIconLink
            aria-label={t('News feed')}
            to="/admin/profile/news-feed"
            active={location.pathname.startsWith('/admin/profile/news-feed')}
            icon={<CampaignOutlined fontSize="medium" />}
          />
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{t('News feed')}</TooltipContent>
    </Tooltip>
  );
};

export default TopBarNewsFeed;
