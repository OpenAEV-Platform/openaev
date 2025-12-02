import { useParams } from 'react-router';

import { fetchScenarioArticles } from '../../../../../actions/channels/article-action';
import { fetchScenarioChannels } from '../../../../../actions/channels/channel-action';
import { fetchScenarioDocuments } from '../../../../../actions/documents/documents-actions';
import { getScenarioArticlesSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store';
import { type Article, type Scenario } from '../../../../../utils/api-types';
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
  const articles = useSelectorHelper(state => getScenarioArticlesSelector(scenarioId, state));
  useDataLoader(() => {
    dispatch(fetchScenarioArticles(scenarioId));
    dispatch(fetchScenarioDocuments(scenarioId));
    dispatch(fetchScenarioChannels(scenarioId));
  });
  const context = articleContextForScenario(scenarioId);
  return (
    <ArticleContext.Provider value={context}>
      <Articles articles={articles as Article[]} />
    </ArticleContext.Provider>
  );
};

export default ScenarioArticles;
