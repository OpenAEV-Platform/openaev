import { Badge, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { ArticleOutlined, SettingsOutlined } from '@mui/icons-material';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

export type NewsFeedTab = 'blog_posts' | 'settings';

interface Props {
  /** The tab shown, none while the page does not know yet whether the blogposts are available. */
  current: NewsFeedTab | undefined;
  /** The blogposts tab only exists when XTM Hub provisions blogposts. */
  blogPostsAvailable: boolean;
  unreadBlogPostsCount: number;
}

export const NEWS_FEED_PATH = '/admin/profile/news-feed';
export const NEWS_FEED_SETTINGS_PATH = `${NEWS_FEED_PATH}/settings`;

/** Route-based tabs: the panels are the routed pages, so every tab is a real link. */
const NewsFeedTabs = ({ current, blogPostsAvailable, unreadBlogPostsCount }: Props) => {
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
