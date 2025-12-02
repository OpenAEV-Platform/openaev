import { useEffect } from 'react';
import { useParams } from 'react-router';

import { fetchMe } from '../../../actions/Application';
import { fetchExercise, fetchPlayerExercise } from '../../../actions/Exercise';
import { addLessonsAnswers, fetchLessonsAnswers, fetchLessonsCategories, fetchLessonsQuestions, fetchPlayerLessonsAnswers, fetchPlayerLessonsCategories, fetchPlayerLessonsQuestions } from '../../../actions/exercises/exercise-action';
import {
  getExerciseLessonsCategoriesSelector,
  getExerciseLessonsQuestionsSelector,
  getExerciseSelector,
  getExerciseUserLessonsAnswersSelector,
  getMeSelector,
} from '../../../actions/selectors';
import { ViewLessonContext, type ViewLessonContextType } from '../../../admin/components/common/Context';
import { useSelectorHelper } from '../../../store';
import { type Exercise } from '../../../utils/api-types';
import { useQueryParameter } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useSimulationPermissions from '../../../utils/permissions/useSimulationPermissions';
import LessonsPlayer from './LessonsPlayer';
import LessonsPreview from './LessonsPreview';

const ExerciseViewLessons = () => {
  const dispatch = useAppDispatch();
  const [preview] = useQueryParameter(['preview']);
  const [userId] = useQueryParameter(['user']);
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const isPreview = preview === 'true';

  const processToGenericSource = (exercise: Exercise | undefined) => {
    if (!exercise) return undefined;
    return {
      id: exerciseId,
      type: 'simulation',
      name: exercise.exercise_name,
      subtitle: exercise.exercise_subtitle,
      userId,
      isUserAbsent: userId === 'null',
      isPlayerViewAvailable: true,
    };
  };

  const me = useSelectorHelper(getMeSelector);
  const exercise = useSelectorHelper(state => getExerciseSelector(exerciseId, state));
  const source = processToGenericSource(exercise);
  const lessonsCategories = useSelectorHelper(state => getExerciseLessonsCategoriesSelector(exerciseId, state));
  const lessonsQuestions = useSelectorHelper(state => getExerciseLessonsQuestionsSelector(exerciseId, state));
  const lessonsAnswers = useSelectorHelper(state => getExerciseUserLessonsAnswersSelector(
    exerciseId,
    userId && userId !== 'null' ? userId : me?.user_id,
    state,
  ));

  const finalUserId = userId && userId !== 'null' ? userId : me?.user_id;

  useEffect(() => {
    dispatch(fetchMe());
    if (isPreview) {
      dispatch(fetchExercise(exerciseId));
      dispatch(fetchLessonsCategories(exerciseId));
      dispatch(fetchLessonsQuestions(exerciseId));
      dispatch(fetchLessonsAnswers(exerciseId));
    } else {
      dispatch(fetchPlayerExercise(exerciseId, userId));
      dispatch(fetchPlayerLessonsCategories(exerciseId, finalUserId));
      dispatch(fetchPlayerLessonsQuestions(exerciseId, finalUserId));
      dispatch(fetchPlayerLessonsAnswers(exerciseId, finalUserId));
    }
  }, [dispatch, exerciseId, userId, finalUserId]);

  // Pass the full exercise because the exercise is never loaded in the store at this point
  const permissions = useSimulationPermissions(exerciseId, exercise);

  const context: ViewLessonContextType = {
    onAddLessonsAnswers: (questionCategory, lessonsQuestionId, answerData) => dispatch(
      addLessonsAnswers(
        exerciseId,
        questionCategory,
        lessonsQuestionId,
        answerData,
        finalUserId,
      ),
    ),
    onFetchPlayerLessonsAnswers: () => dispatch(fetchPlayerLessonsAnswers(exerciseId, finalUserId)),
  };

  return (
    <ViewLessonContext.Provider value={context}>
      {isPreview ? (
        <LessonsPreview
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          permissions={permissions}
        />
      ) : (
        <LessonsPlayer
          source={{
            ...source,
            finalUserId,
          }}
          lessonsCategories={lessonsCategories}
          lessonsQuestions={lessonsQuestions}
          lessonsAnswers={lessonsAnswers}
          permissions={permissions}
        />
      )}
    </ViewLessonContext.Provider>
  );
};

export default ExerciseViewLessons;
