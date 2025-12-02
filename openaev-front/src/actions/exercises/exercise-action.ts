import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type CheckExerciseRulesOutput,
  type CustomDashboard,
  type EsAttackPath,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
  type Exercise,
  type ExercisesGlobalScoresInput,
  type ExercisesGlobalScoresOutput,
  type ExpectationResultsByType,
  type ImportTestSummary,
  type InjectExpectationResultsByAttackPattern,
  type InjectResultOutput,
  type InjectsImportInput,
  type LessonsAnswer,
  type LessonsAnswerCreateInput,
  type LessonsCategory,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestion,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type LessonsSendInput,
  type Option,
  type Scenario,
  type SearchPaginationInput,
  type User,
  type WidgetToEntitiesInput,
  type WidgetToEntitiesOutput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { arrayOfLessonsAnswers, arrayOfLessonsCategories, arrayOfLessonsQuestions, arrayOfUsers, lessonsCategory, lessonsQuestion, scenario } from '../schemas';

export const EXERCISE_URI = '/api/exercises';

export const fetchExerciseExpectationResult = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/results`;
  return simpleCall<ExpectationResultsByType[]>(uri);
};

export const fetchPlayersByExercise = (exerciseId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/players`;
  return getReferential<User[]>(arrayOfUsers, uri)(dispatch);
};

export const fetchExerciseInjectExpectationResults = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/results-by-attack-patterns`;
  return simpleCall<InjectExpectationResultsByAttackPattern[]>(uri);
};

export const searchExerciseInjects = (exerciseId: Exercise['exercise_id'], searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/search`;
  return simplePostCall<Page<InjectResultOutput>>(uri, data);
};

export const exerciseInjectsResultOutput = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/results`;
  return simpleCall<InjectResultOutput[]>(uri);
};

// -- IMPORT --

export const importXlsForExercise = (exerciseId: Exercise['exercise_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/xls/${importId}/import`;
  return simplePostCall<ImportTestSummary>(uri, input)
    .then((response) => {
      const injectCount = response.data.total_injects;
      if (injectCount === 0) {
        MESSAGING$.notifySuccess('No inject imported');
      } else {
        MESSAGING$.notifySuccess(`${injectCount} inject imported`);
      }
      return response;
    });
};

export const dryImportXlsForExercise = (exerciseId: Exercise['exercise_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/xls/${importId}/dry`;
  return simplePostCall<ImportTestSummary>(uri, input)
    .then((response) => {
      return response;
    });
};

// -- OPTION --

export const searchExerciseLinkedToFindingsAsOption = (searchText: string = '', sourceId: string = '') => {
  const params = {
    searchText,
    sourceId,
  };
  return simpleCall<Option[]>(`${EXERCISE_URI}/findings/options`, { params });
};

export const searchExerciseByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${EXERCISE_URI}/options`, ids);
};

// -- LESSONS --

export const fetchLessonsCategories = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories`;
  return getReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri)(dispatch);
};

export const updateLessonsCategory = (exerciseId: string, lessonsCategoryId: string, data: LessonsCategoryUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}`;
  return putReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const updateLessonsCategoryTeams = (exerciseId: string, lessonsCategoryId: string, data: LessonsCategoryTeamsInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/teams`;
  return putReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const addLessonsCategory = (exerciseId: string, data: LessonsCategoryCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories`;
  return postReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const deleteLessonsCategory = (exerciseId: string, lessonsCategoryId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}`;
  return delReferential(uri, 'lessonscategorys', lessonsCategoryId)(dispatch);
};

export const applyLessonsTemplate = (exerciseId: string, lessonsTemplateId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_apply_template/${lessonsTemplateId}`;
  return postReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri, {})(dispatch);
};

export const fetchLessonsQuestions = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_questions`;
  return getReferential<LessonsQuestion[]>(arrayOfLessonsQuestions, uri)(dispatch);
};

export const updateLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return putReferential<LessonsQuestion>(lessonsQuestion, uri, data)(dispatch);
};

export const addLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, data: LessonsQuestionCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions`;
  return postReferential<LessonsQuestion>(lessonsQuestion, uri, data)(dispatch);
};

export const deleteLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, lessonsQuestionId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return delReferential(uri, 'lessonsquestions', lessonsQuestionId)(dispatch);
};

export const resetLessonsAnswers = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_answers_reset`;
  return postReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri, {})(dispatch);
};

export const emptyLessonsCategories = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_empty`;
  return postReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri, {})(dispatch);
};

export const sendLessons = (exerciseId: string, data: LessonsSendInput) => {
  const uri = `/api/exercises/${exerciseId}/lessons_send`;
  return simplePostCall<void>(uri, data);
};

export const fetchLessonsAnswers = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_answers`;
  return getReferential<LessonsAnswer[]>(arrayOfLessonsAnswers, uri)(dispatch);
};

export const fetchPlayerLessonsCategories = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_categories?userId=${userId}`;
  return getReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri)(dispatch);
};

export const fetchPlayerLessonsQuestions = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_questions?userId=${userId}`;
  return getReferential<LessonsQuestion[]>(arrayOfLessonsQuestions, uri)(dispatch);
};

export const fetchPlayerLessonsAnswers = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_answers?userId=${userId}`;
  return getReferential<LessonsAnswer[]>(arrayOfLessonsAnswers, uri)(dispatch);
};

export const addLessonsAnswers = (
  exerciseId: string,
  lessonsCategoryId: string,
  lessonsQuestionId: string,
  data: LessonsAnswerCreateInput,
  userId: string,
) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}/lessons_answers?userId=${userId}`;
  return postReferential<LessonsAnswer>(arrayOfLessonsAnswers, uri, data)(dispatch);
};

export const fetchExercisesGlobalScores = (exercisesGlobalScoresInput: ExercisesGlobalScoresInput) => {
  const data = exercisesGlobalScoresInput;
  const uri = `${EXERCISE_URI}/global-scores`;
  return simplePostCall<ExercisesGlobalScoresOutput>(uri, data);
};

export const checkExerciseTagRules = (exerciseId: string, newTagIds: string[]) => {
  const uri = `/api/exercises/${exerciseId}/check-rules`;
  const input = { new_tags: newTagIds };
  return simplePostCall<CheckExerciseRulesOutput>(uri, input);
};

export const updateCustomDashboard = (exerciseId: string, customDashboardId: string | null) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/custom-dashboards/${customDashboardId}`;
  return putReferential<LessonsQuestion>(lessonsQuestion, uri, {})(dispatch);
};

export const fetchScenarioFromSimulation = (simulationId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/scenario`;
  return getReferential<Scenario>(scenario, uri)(dispatch);
};

export const fetchCustomDashboardFromSimulation = (simulationId: string) => {
  return simpleCall<CustomDashboard>(`${EXERCISE_URI}/${simulationId}/dashboard`);
};

export const countBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsCountInterval>(`${EXERCISE_URI}/${simulationId}/dashboard/count/${widgetId}`, parameters);
};

export const seriesBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsSeries[]>(`${EXERCISE_URI}/${simulationId}/dashboard/series/${widgetId}`, parameters);
};

export const entitiesBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsBase[]>(`${EXERCISE_URI}/${simulationId}/dashboard/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesBySimulation = (simulationId: string, widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall<WidgetToEntitiesOutput>(`${EXERCISE_URI}/${simulationId}/dashboard/entities-runtime/${widgetId}`, input);
};

export const attackPathsBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsAttackPath[]>(`${EXERCISE_URI}/${simulationId}/dashboard/attack-paths/${widgetId}`, parameters);
};
