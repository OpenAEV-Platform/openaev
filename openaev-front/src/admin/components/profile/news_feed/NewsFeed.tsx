import NotFound from '../../../../components/NotFound';
import { isFeatureEnabled } from '../../../../utils/utils';
import NewsFeedList from './NewsFeedList';

/** Route guard: the page only exists when the news feed feature is enabled. */
const NewsFeed = () => {
  if (!isFeatureEnabled('XTM_HUB_NEWS_FEED')) {
    return <NotFound />;
  }
  return <NewsFeedList />;
};

export default NewsFeed;
