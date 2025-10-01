import { useParams } from 'react-router';

import { fetchScenarioArticles } from '../../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchScenarioChannels } from '../../../../../actions/channels/channel-action';
import { fetchScenarioDocuments } from '../../../../../actions/documents/documents-actions';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import Articles from '../../../common/articles/Articles';
import { ArticleContext } from '../../../common/Context';
import articleContextForScenario from './articleContextForScenario';

const ScenarioArticles = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { articles } = useHelper((helper: ArticlesHelper) => ({ articles: helper.getScenarioArticles(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchScenarioArticles(scenarioId));
    dispatch(fetchScenarioDocuments(scenarioId));
    dispatch(fetchScenarioChannels(scenarioId));
  });
  const context = articleContextForScenario(scenarioId);
  return (
    <ArticleContext.Provider value={context}>
      <Articles articles={articles} />
    </ArticleContext.Provider>
  );
};

export default ScenarioArticles;
