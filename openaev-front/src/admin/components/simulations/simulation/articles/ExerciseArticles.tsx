import { useParams } from 'react-router';

import { fetchExerciseArticles } from '../../../../../actions/channels/article-action';
import { type ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchSimulationChannels } from '../../../../../actions/channels/channel-action';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { useHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import Articles from '../../../common/articles/Articles';
import { ArticleContext } from '../../../common/Context';
import articleContextForExercise from './articleContextForExercise';

const ExerciseArticles = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { articles } = useHelper((helper: ArticlesHelper) => ({ articles: helper.getExerciseArticles(exerciseId) }));
  useDataLoader(() => {
    dispatch(fetchExerciseArticles(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
    dispatch(fetchSimulationChannels(exerciseId));
  });
  const context = articleContextForExercise(exerciseId);
  return (
    <ArticleContext.Provider value={context}>
      <Articles articles={articles} />
    </ArticleContext.Provider>
  );
};

export default ExerciseArticles;
