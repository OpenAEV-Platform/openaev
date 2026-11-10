import NotFound from '../../../../components/NotFound';
import { useIsFeatureEnabled } from '../../../../utils/utils';
import NewsFeedList from './NewsFeedList';

/** Route guard: the page only exists when the news feed feature is enabled. */
const NewsFeed = () => {
  const enabled = useIsFeatureEnabled('XTM_HUB_NEWS_FEED');
  if (!enabled) {
    return <NotFound />;
  }
  return <NewsFeedList />;
};

export default NewsFeed;
