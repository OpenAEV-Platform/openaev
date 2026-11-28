import { Alert, Button } from '@filigran/design-system';
import { ArticleOutlined, OpenInNewOutlined } from '@mui/icons-material';

import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import SimpleError from '../../../../components/SimpleError';
import useAuth from '../../../../utils/hooks/useAuth';
import NewsFeedBlogPostCard from './NewsFeedBlogPostCard';
import { buildNewsFeedBlogSeeAllUrl, NEWS_FEED_CATEGORIES } from './newsFeedUtils';
import useNewsFeedItems from './useNewsFeedItems';

/** The blogposts XTM Hub provisioned for the user, newest first, with the link to the Filigran blog. */
const NewsFeedBlogPosts = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { items: blogPosts, failed } = useNewsFeedItems(NEWS_FEED_CATEGORIES.BLOG_POST);

  let content;
  if (blogPosts === null) {
    content = failed ? <SimpleError /> : <Loader variant="inElement" />;
  } else if (blogPosts.length === 0) {
    content = <Empty message={t('No news feed items')} icon={ArticleOutlined} />;
  } else {
    content = (
      // 3 cards per row at most, fewer when the page is too narrow to keep the cards readable
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(max(320px, calc((100% - 48px) / 3)), 1fr))',
        gap: 24,
      }}
      >
        {blogPosts.map(blogPost => (
          <NewsFeedBlogPostCard key={blogPost.news_feed_item_id} item={blogPost} />
        ))}
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
      paddingBottom: 24,
    }}
    >
      <Alert severity="info" title={t('The news feed shows the latest six blogposts')} />
      {content}
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
      }}
      >
        <Button asChild priority="secondary">
          <a href={buildNewsFeedBlogSeeAllUrl(settings.xtm_hub_url)} target="_blank" rel="noopener noreferrer">
            {t('See all')}
            <OpenInNewOutlined fontSize="small" />
          </a>
        </Button>
      </div>
    </div>
  );
};

export default NewsFeedBlogPosts;
