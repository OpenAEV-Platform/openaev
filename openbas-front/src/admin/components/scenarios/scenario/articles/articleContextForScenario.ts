import { type FullArticleStore } from '../../../../../actions/channels/Article';
import { addScenarioArticle, deleteScenarioArticle, updateScenarioArticle } from '../../../../../actions/channels/article-action';
import { type Article, type ArticleCreateInput, type ArticleUpdateInput, type Scenario } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';

const articleContextForScenario = (scenarioId: Scenario['scenario_id']) => {
  const dispatch = useAppDispatch();
  return {
    previewArticleUrl: (article: FullArticleStore) => `/channels/${scenarioId}/${article.article_fullchannel?.channel_id}?preview=true`,
    onAddArticle: (data: ArticleCreateInput) => dispatch(addScenarioArticle(scenarioId, data)),
    onUpdateArticle: (article: Article, data: ArticleUpdateInput) => dispatch(
      updateScenarioArticle(scenarioId, article.article_id, data),
    ),
    onDeleteArticle: (article: Article) => dispatch(
      deleteScenarioArticle(scenarioId, article.article_id),
    ),
  };
};

export default articleContextForScenario;
