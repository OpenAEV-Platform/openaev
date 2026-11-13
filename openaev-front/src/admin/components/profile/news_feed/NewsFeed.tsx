import { Text } from '@filigran/design-system';
import { useLocation } from 'react-router';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NotFound from '../../../../components/NotFound';
import useAuth from '../../../../utils/hooks/useAuth';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import NewsFeedHubBanner, { type NewsFeedHubBannerVariant } from './NewsFeedHubBanner';
import NewsFeedList from './NewsFeedList';
import NewsFeedSettings from './NewsFeedSettings';
import NewsFeedTabs, { type NewsFeedTab } from './NewsFeedTabs';
import useMarkNewsFeedItemsRead from './useMarkNewsFeedItemsRead';
import useNewsFeedSettings from './useNewsFeedSettings';

interface Props { tab: NewsFeedTab }

/**
 * The settings are loaded for both tabs: they tell whether the banner inviting to connect XTM Hub shows.
 * As on OpenCTI, a platform that lost its connectivity gets the banner too. One that cannot reach XTM Hub,
 * and a user who cannot connect it, get it without the way to connect.
 */
const NewsFeedPage = ({ tab }: Props) => {
  const { t } = useFormatter();
  const ability = useAbility();
  // Changes on every navigation, including a click on a link to the page already shown.
  const { key: visitKey } = useLocation();
  // Same permission as the connect flow: a user without it is told to ask an administrator instead.
  const canConnectXtmHub = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const { settings: platformSettings } = useAuth();
  const isXtmHubUnreachable = platformSettings.xtm_hub_reachable === false;
  const {
    settings,
    failed,
    saving,
    updateSubscribedTypes,
  } = useNewsFeedSettings();
  // Only the items view marks them read: the settings tab shows none.
  useMarkNewsFeedItemsRead(tab === 'items', visitKey);

  let bannerVariant: NewsFeedHubBannerVariant | null = null;
  if (isXtmHubUnreachable) {
    bannerVariant = 'unreachable';
  } else if (settings && !settings.news_feed_settings_hub_connected) {
    bannerVariant = canConnectXtmHub ? 'connect' : 'ask_administrator';
  }

  return (
    <>
      <Breadcrumbs
        variant="standard"
        elements={[{
          label: t('XTM Hub News Feed'),
          current: true,
        }]}
      />
      <Text variant="title-xl" as="h1" style={{ margin: '24px 0' }}>{t('XTM Hub News Feed')}</Text>
      {bannerVariant && <NewsFeedHubBanner variant={bannerVariant} />}
      <NewsFeedTabs current={tab} />
      <div style={{ marginTop: 20 }}>
        {/* The list is keyed on the visit: coming back to the page reloads it, so a new item shows up. */}
        {tab === 'items'
          ? <NewsFeedList key={visitKey} />
          : (
              <NewsFeedSettings
                settings={settings}
                failed={failed}
                saving={saving}
                onChange={updateSubscribedTypes}
              />
            )}
      </div>
    </>
  );
};

/** Route guard: the page only exists when the news feed feature is enabled. */
const NewsFeed = ({ tab }: Props) => {
  if (!isFeatureEnabled('XTM_HUB_NEWS_FEED')) {
    return <NotFound />;
  }
  return <NewsFeedPage tab={tab} />;
};

export default NewsFeed;
