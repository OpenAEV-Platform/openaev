import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CampaignOutlined } from '@mui/icons-material';
import { useLocation } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { isFeatureEnabled } from '../../../utils/utils';
import TopBarIconLink from './TopBarIconLink';

/** Top bar megaphone: entry point of the XTM Hub news feed, only shown when the feature is enabled. */
const TopBarNewsFeed = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const enabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');

  if (!enabled) {
    return null;
  }

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <TopBarIconLink
          aria-label={t('News feed')}
          to="/admin/profile/news-feed"
          active={location.pathname.startsWith('/admin/profile/news-feed')}
          icon={<CampaignOutlined fontSize="medium" />}
        />
      </TooltipTrigger>
      <TooltipContent>{t('News feed')}</TooltipContent>
    </Tooltip>
  );
};

export default TopBarNewsFeed;
