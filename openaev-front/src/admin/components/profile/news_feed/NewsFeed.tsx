import { Text } from '@filigran/design-system';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NotFound from '../../../../components/NotFound';
import useAuth from '../../../../utils/hooks/useAuth';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import useNewsFeedUnreadCount from '../../nav/useNewsFeedUnreadCount';
import NewsFeedBlogPosts from './NewsFeedBlogPosts';
import NewsFeedHubBanner, { type NewsFeedHubBannerVariant } from './NewsFeedHubBanner';
import NewsFeedSettings from './NewsFeedSettings';
import NewsFeedTabs, { type NewsFeedTab } from './NewsFeedTabs';
import { NEWS_FEED_BLOG_POST_TYPE } from './newsFeedUtils';
import useMarkNewsFeedItemsRead, { ALL_NEWS_FEED_ITEMS, type NewsFeedItemsToMark } from './useMarkNewsFeedItemsRead';
import useNewsFeedSettings from './useNewsFeedSettings';
import useNewsFeedVisitKey from './useNewsFeedVisitKey';

interface Props { tab: NewsFeedTab }

/**
 * The settings are loaded for both tabs: they tell whether XTM Hub provisions blogposts, and whether the banner
 * inviting to connect XTM Hub shows. As on OpenCTI, a platform that lost its connectivity gets the banner too. One
 * that cannot reach XTM Hub, and a user who cannot connect it, get it without the way to connect.
 *
 * The page of the news feed itself shows the blogposts. A visit marks the blogposts read, and the resources too, since
 * no tab shows them. When XTM Hub provisions no blogpost, it falls back to the settings, and a visit marks every item
 * read.
 */
const NewsFeedPage = ({ tab }: Props) => {
  const { t } = useFormatter();
  const ability = useAbility();
  // Changes on every navigation to the page, including a click on a link to the page already shown, but
  // not when the list writes its own search to the URL.
  const visitKey = useNewsFeedVisitKey();
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
  const unreadBlogPostsCount = useNewsFeedUnreadCount('BLOG_POST');

  const isBlogPostAvailable = settings?.news_feed_settings_available_types.includes(NEWS_FEED_BLOG_POST_TYPE) ?? false;
  let displayedTab: NewsFeedTab | undefined = tab;
  if (tab === 'blog_posts' && !isBlogPostAvailable) {
    // Until the settings tell, no tab is shown as current; without blogposts, the page is the settings.
    displayedTab = settings || failed ? 'settings' : undefined;
  }
  // Only the visit of the page of the news feed marks the items read, once it knows what it shows: the blogposts it
  // shows and the resources, which no tab shows, each category on its own as on OpenCTI, so that the top bar counter
  // goes back to 0. Without blogposts, no tab shows any item: every item at once.
  const itemsToMark: NewsFeedItemsToMark = isBlogPostAvailable ? ['BLOG_POST', 'RESOURCE'] : ALL_NEWS_FEED_ITEMS;
  useMarkNewsFeedItemsRead(tab === 'blog_posts' && settings !== null, visitKey, itemsToMark);

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
      <NewsFeedTabs current={displayedTab} blogPostsAvailable={isBlogPostAvailable} unreadBlogPostsCount={unreadBlogPostsCount} />
      <div style={{ marginTop: 20 }}>
        {/* The blogposts are keyed on the visit: coming back to the page reloads them, so a new one shows up. */}
        {displayedTab === 'blog_posts' && <NewsFeedBlogPosts key={visitKey} />}
        {displayedTab === 'settings' && (
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
