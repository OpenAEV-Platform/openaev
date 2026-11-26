import { Badge, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { ArticleOutlined, CellTowerOutlined, SettingsOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

export type NewsFeedTab = 'blog_posts' | 'github_releases' | 'settings';

interface Props {
  /** The tab shown, none while the page does not know yet whether the blogposts are available. */
  current: NewsFeedTab | undefined;
  /** The blogposts tab only exists when XTM Hub provisions blogposts. */
  blogPostsAvailable: boolean;
  unreadBlogPostsCount: number;
  /** The GitHub releases tab only exists when XTM Hub provisions GitHub releases. */
  githubReleasesAvailable: boolean;
  unreadGithubReleasesCount: number;
}

export const NEWS_FEED_PATH = '/admin/profile/news-feed';
export const NEWS_FEED_GITHUB_RELEASES_PATH = `${NEWS_FEED_PATH}/github-releases`;
export const NEWS_FEED_SETTINGS_PATH = `${NEWS_FEED_PATH}/settings`;

/** Route-based tabs: the panels are the routed pages, so every tab is a real link. */
const NewsFeedTabs = ({ current, blogPostsAvailable, unreadBlogPostsCount, githubReleasesAvailable, unreadGithubReleasesCount }: Props) => {
  const { t } = useFormatter();

  return (
    <Tabs value={current ?? ''} panels="external">
      <TabsList>
        {blogPostsAvailable && (
          <TabsTrigger value="blog_posts" asChild>
            <Link to={NEWS_FEED_PATH} aria-current={current === 'blog_posts' ? 'page' : undefined}>
              <ArticleOutlined fontSize="small" />
              {t('Blogpost')}
              {/* The trigger ignores its own badge as a link: it is composed in the link instead. */}
              <Badge
                content={unreadBlogPostsCount}
                max={99}
                invisible={unreadBlogPostsCount === 0}
                accessibleText={t('Unread blogposts: {count}', { count: String(unreadBlogPostsCount) })}
              />
            </Link>
          </TabsTrigger>
        )}
        {githubReleasesAvailable && (
          <TabsTrigger value="github_releases" asChild>
            <Link to={NEWS_FEED_GITHUB_RELEASES_PATH} aria-current={current === 'github_releases' ? 'page' : undefined}>
              <CellTowerOutlined fontSize="small" />
              {t('Last releases')}
              <Badge
                content={unreadGithubReleasesCount}
                max={99}
                invisible={unreadGithubReleasesCount === 0}
                accessibleText={t('Unread GitHub releases: {count}', { count: String(unreadGithubReleasesCount) })}
              />
            </Link>
          </TabsTrigger>
        )}
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
