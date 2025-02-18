import { type FullArticleStore } from '../../../../../actions/channels/Article';
import { addExerciseArticle, deleteExerciseArticle, updateExerciseArticle } from '../../../../../actions/channels/article-action';
import { type Article, type ArticleCreateInput, type ArticleUpdateInput, type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';

const articleContextForExercise = (exerciseId: Exercise['exercise_id']) => {
  const dispatch = useAppDispatch();
  return {
    previewArticleUrl: (article: FullArticleStore) => `/channels/${exerciseId}/${article.article_fullchannel?.channel_id}?preview=true`,
    onAddArticle: (data: ArticleCreateInput) => dispatch(addExerciseArticle(exerciseId, data)),
    onUpdateArticle: (article: Article, data: ArticleUpdateInput) => dispatch(
      updateExerciseArticle(exerciseId, article.article_id, data),
    ),
    onDeleteArticle: (article: Article) => dispatch(
      deleteExerciseArticle(exerciseId, article.article_id),
    ),
  };
};

export default articleContextForExercise;
