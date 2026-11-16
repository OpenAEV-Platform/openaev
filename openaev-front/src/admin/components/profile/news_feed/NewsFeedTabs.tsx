import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { CampaignOutlined, SettingsOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

export type NewsFeedTab = 'items' | 'settings';

interface Props { current: NewsFeedTab }

export const NEWS_FEED_PATH = '/admin/profile/news-feed';
export const NEWS_FEED_SETTINGS_PATH = `${NEWS_FEED_PATH}/settings`;

/** Route-based tabs: the panels are the routed pages, so every tab is a real link. */
const NewsFeedTabs = ({ current }: Props) => {
  const { t } = useFormatter();

  return (
    <Tabs value={current} panels="external">
      <TabsList>
        <TabsTrigger value="items" asChild>
          <Link to={NEWS_FEED_PATH} aria-current={current === 'items' ? 'page' : undefined}>
            <CampaignOutlined fontSize="small" />
            {t('News feed')}
          </Link>
        </TabsTrigger>
        <TabsTrigger value="settings" asChild>
          <Link to={NEWS_FEED_SETTINGS_PATH} aria-current={current === 'settings' ? 'page' : undefined}>
            <SettingsOutlined fontSize="small" />
            {t('Settings')}
          </Link>
        </TabsTrigger>
      </TabsList>
    </Tabs>
  );
};

export default NewsFeedTabs;
