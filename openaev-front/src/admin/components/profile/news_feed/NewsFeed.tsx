import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NotFound from '../../../../components/NotFound';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import NewsFeedHubBanner from './NewsFeedHubBanner';
import NewsFeedList from './NewsFeedList';
import NewsFeedSettings from './NewsFeedSettings';
import NewsFeedTabs, { type NewsFeedTab } from './NewsFeedTabs';
import useNewsFeedSettings from './useNewsFeedSettings';

interface Props { tab: NewsFeedTab }

/** The settings are loaded for both tabs: they tell whether the banner inviting to connect XTM Hub shows. */
const NewsFeedPage = ({ tab }: Props) => {
  const { t } = useFormatter();
  const ability = useAbility();
  // Same permission as the connect flow: the banner is pointless for a user who cannot follow it.
  const canConnectXtmHub = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const {
    settings,
    failed,
    saving,
    updateSubscribedTypes,
  } = useNewsFeedSettings();

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('News feed'),
          current: true,
        }]}
      />
      {canConnectXtmHub && settings && !settings.news_feed_settings_hub_registered && <NewsFeedHubBanner />}
      <NewsFeedTabs current={tab} />
      {tab === 'items'
        ? <NewsFeedList />
        : (
            <NewsFeedSettings
              settings={settings}
              failed={failed}
              saving={saving}
              onChange={updateSubscribedTypes}
            />
          )}
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
