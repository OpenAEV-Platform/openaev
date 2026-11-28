import { Text } from '@filigran/design-system';

import { type NewsFeedCategory } from '../../../../actions/news_feed/news-feed-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import NotFound from '../../../../components/NotFound';
import useAuth from '../../../../utils/hooks/useAuth';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import useNewsFeedUnreadCount from '../../nav/useNewsFeedUnreadCount';
import NewsFeedBlogPosts from './NewsFeedBlogPosts';
import NewsFeedGithubReleases from './NewsFeedGithubReleases';
import NewsFeedHubBanner, { type NewsFeedHubBannerVariant } from './NewsFeedHubBanner';
import NewsFeedSettings from './NewsFeedSettings';
import NewsFeedTabs, { type NewsFeedTab } from './NewsFeedTabs';
import { NEWS_FEED_BLOG_POST_TYPE, NEWS_FEED_CATEGORIES, NEWS_FEED_GITHUB_RELEASE_TYPE } from './newsFeedUtils';
import useMarkNewsFeedItemsRead, { ALL_NEWS_FEED_ITEMS, type NewsFeedItemsToMark } from './useMarkNewsFeedItemsRead';
import useNewsFeedSettings from './useNewsFeedSettings';
import useNewsFeedVisitKey from './useNewsFeedVisitKey';

interface Props { tab: NewsFeedTab }

/** The categories a visit marks read: those of the tab displayed, and those no tab shows. */
const buildItemsToMark = (displayedTab: NewsFeedTab | undefined, isBlogPostAvailable: boolean, isGithubReleaseAvailable: boolean): NewsFeedItemsToMark => {
  const categories = new Set<NewsFeedCategory>();
  if (displayedTab === 'blog_posts' || !isBlogPostAvailable) {
    // The resources are shown by no tab.
    categories.add(NEWS_FEED_CATEGORIES.BLOG_POST);
    categories.add(NEWS_FEED_CATEGORIES.RESOURCE);
  }
  if (displayedTab === 'github_releases' || !isGithubReleaseAvailable) {
    categories.add(NEWS_FEED_CATEGORIES.GITHUB_RELEASE);
  }
  // Every category at once is a single request.
  return categories.size === Object.keys(NEWS_FEED_CATEGORIES).length ? ALL_NEWS_FEED_ITEMS : [...categories];
};

/**
 * The settings are loaded for every tab: they tell whether XTM Hub provisions blogposts and GitHub releases, and whether
 * the banner inviting to connect XTM Hub shows. As on OpenCTI, a platform that lost its connectivity gets the banner
 * too. One that cannot reach XTM Hub, and a user who cannot connect it, get it without the way to connect.
 *
 * A tab only exists when XTM Hub provisions its type. The page of a missing tab shows the first tab that exists,
 * the settings when there is none.
 *
 * A visit of a tab with items marks the items it shows read, each category on its own as on OpenCTI, and the
 * categories no tab shows: the resources, and the types XTM Hub does not provision, so that the top bar counter goes
 * back to 0. Without any tab with items, a visit marks every item read.
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
  const unreadBlogPostsCount = useNewsFeedUnreadCount(NEWS_FEED_CATEGORIES.BLOG_POST);
  const unreadGithubReleasesCount = useNewsFeedUnreadCount(NEWS_FEED_CATEGORIES.GITHUB_RELEASE);

  const availableTypes = settings?.news_feed_settings_available_types ?? [];
  const isBlogPostAvailable = availableTypes.includes(NEWS_FEED_BLOG_POST_TYPE);
  const isGithubReleaseAvailable = availableTypes.includes(NEWS_FEED_GITHUB_RELEASE_TYPE);
  let displayedTab: NewsFeedTab | undefined = tab;
  const isTabMissing = (tab === 'blog_posts' && !isBlogPostAvailable) || (tab === 'github_releases' && !isGithubReleaseAvailable);
  if (isTabMissing) {
    // Until the settings tell, no tab is shown as current.
    if (!settings && !failed) {
      displayedTab = undefined;
    } else if (isBlogPostAvailable) {
      displayedTab = 'blog_posts';
    } else {
      displayedTab = isGithubReleaseAvailable ? 'github_releases' : 'settings';
    }
  }
  // Only the visit of a tab with items marks them read, once the settings tell what the page shows. It follows the tab
  // displayed, not the route: a missing tab falls back to another one.
  const itemsToMark = buildItemsToMark(displayedTab, isBlogPostAvailable, isGithubReleaseAvailable);
  useMarkNewsFeedItemsRead(tab !== 'settings' && settings !== null, visitKey, itemsToMark);

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
      <NewsFeedTabs
        current={displayedTab}
        blogPostsAvailable={isBlogPostAvailable}
        unreadBlogPostsCount={unreadBlogPostsCount}
        githubReleasesAvailable={isGithubReleaseAvailable}
        unreadGithubReleasesCount={unreadGithubReleasesCount}
      />
      <div style={{ marginTop: 20 }}>
        {/* The blogposts are keyed on the visit: coming back to the page reloads them, so a new one shows up. */}
        {displayedTab === 'blog_posts' && <NewsFeedBlogPosts key={visitKey} />}
        {displayedTab === 'github_releases' && <NewsFeedGithubReleases key={visitKey} />}
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
