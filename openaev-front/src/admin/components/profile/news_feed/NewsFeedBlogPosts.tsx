import { Alert, Button } from '@filigran/design-system';
import { ArticleOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { useEffect, useRef, useState } from 'react';

import { searchMyNewsFeedItems } from '../../../../actions/news_feed/news-feed-actions';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import SimpleError from '../../../../components/SimpleError';
import type { NewsFeedItemOutput } from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import NewsFeedBlogPostCard from './NewsFeedBlogPostCard';
import { buildNewsFeedBlogSeeAllUrl } from './newsFeedUtils';
import useNewsFeedStreamRefresh from './useNewsFeedStreamRefresh';

// XTM Hub provisions the latest blogposts only: a safety net, not a pagination.
const MAX_BLOG_POSTS = 50;

const BLOG_POSTS_SEARCH = buildSearchPagination({
  size: MAX_BLOG_POSTS,
  sorts: initSorting('news_feed_item_creation_date', 'DESC'),
});

/** The blogposts XTM Hub provisioned for the user, newest first, with the link to the Filigran blog. */
const NewsFeedBlogPosts = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const [blogPosts, setBlogPosts] = useState<NewsFeedItemOutput[] | null>(null);
  const [failed, setFailed] = useState(false);
  // Only the latest request counts: a slower older one must not put back the blogposts a newer one removed.
  const latestRequest = useRef(0);
  const mounted = useRef(true);

  const loadBlogPosts = () => {
    const requestId = ++latestRequest.current;
    searchMyNewsFeedItems(BLOG_POSTS_SEARCH, false, 'BLOG_POST')
      .then(({ data }) => {
        if (mounted.current && requestId === latestRequest.current) {
          setBlogPosts(data.content);
          setFailed(false);
        }
      })
      .catch(() => {
        // A failed refresh keeps the blogposts shown; only a first load without any shows the error.
        if (mounted.current && requestId === latestRequest.current) {
          setFailed(true);
        }
      });
  };

  useEffect(() => {
    mounted.current = true;
    loadBlogPosts();
    return () => {
      mounted.current = false;
    };
  }, []);

  // A blogpost the collector created, changed or deleted is shown without a reload.
  useNewsFeedStreamRefresh(loadBlogPosts);

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
          <a href={buildNewsFeedBlogSeeAllUrl(settings.platform_id, settings.xtm_hub_url)} target="_blank" rel="noopener noreferrer">
            {t('See all')}
            <OpenInNewOutlined fontSize="small" />
          </a>
        </Button>
      </div>
    </div>
  );
};

export default NewsFeedBlogPosts;
