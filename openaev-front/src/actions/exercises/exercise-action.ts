import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type Exercise,
  type ExercisesGlobalScoresInput,
  type InjectsImportInput,
  type LessonsAnswerCreateInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type LessonsSendInput,
  type SearchPaginationInput,
  type WidgetToEntitiesInput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { scenario } from '../scenarios/scenario-schema';
import * as schema from '../Schema';

export const EXERCISE_URI = '/api/exercises';

export const fetchExerciseExpectationResult = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/results`;
  return simpleCall(uri);
};

export const fetchPlayersByExercise = (exerciseId: Exercise['exercise_id']) => (dispatch: Dispatch) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/players`;
  return getReferential(schema.arrayOfUsers, uri)(dispatch);
};

export const fetchExerciseInjectExpectationResults = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/results-by-attack-patterns`;
  return simpleCall(uri);
};

export const searchExerciseInjects = (exerciseId: Exercise['exercise_id'], searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/search`;
  return simplePostCall(uri, data);
};

export const exerciseInjectsResultOutput = (exerciseId: Exercise['exercise_id']) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/injects/results`;
  return simpleCall(uri);
};

// -- IMPORT --

export const importXlsForExercise = (exerciseId: Exercise['exercise_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/xls/${importId}/import`;
  return simplePostCall(uri, input)
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
  return simplePostCall(uri, input)
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
  return simpleCall(`${EXERCISE_URI}/findings/options`, { params });
};

export const searchExerciseByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${EXERCISE_URI}/options`, ids);
};

// -- LESSONS --

export const fetchLessonsCategories = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories`;
  return getReferential(schema.arrayOfLessonsCategories, uri)(dispatch);
};

export const updateLessonsCategory = (exerciseId: string, lessonsCategoryId: string, data: LessonsCategoryUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const updateLessonsCategoryTeams = (exerciseId: string, lessonsCategoryId: string, data: LessonsCategoryTeamsInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/teams`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const addLessonsCategory = (exerciseId: string, data: LessonsCategoryCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories`;
  return postReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const deleteLessonsCategory = (exerciseId: string, lessonsCategoryId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}`;
  return delReferential(uri, 'lessonscategorys', lessonsCategoryId)(dispatch);
};

export const applyLessonsTemplate = (exerciseId: string, lessonsTemplateId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_apply_template/${lessonsTemplateId}`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const fetchLessonsQuestions = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_questions`;
  return getReferential(schema.arrayOfLessonsQuestions, uri)(dispatch);
};

export const updateLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return putReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const addLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, data: LessonsQuestionCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions`;
  return postReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const deleteLessonsQuestion = (exerciseId: string, lessonsCategoryId: string, lessonsQuestionId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return delReferential(uri, 'lessonsquestions', lessonsQuestionId)(dispatch);
};

export const resetLessonsAnswers = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_answers_reset`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const emptyLessonsCategories = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_empty`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const sendLessons = (exerciseId: string, data: LessonsSendInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_send`;
  return postReferential(null, uri, data)(dispatch);
};

export const fetchLessonsAnswers = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/lessons_answers`;
  return getReferential(schema.arrayOfLessonsAnswers, uri)(dispatch);
};

export const fetchPlayerLessonsCategories = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_categories?userId=${userId}`;
  return getReferential(schema.arrayOfLessonsCategories, uri)(dispatch);
};

export const fetchPlayerLessonsQuestions = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_questions?userId=${userId}`;
  return getReferential(schema.arrayOfLessonsQuestions, uri)(dispatch);
};

export const fetchPlayerLessonsAnswers = (exerciseId: string, userId: string) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_answers?userId=${userId}`;
  return getReferential(schema.arrayOfLessonsAnswers, uri)(dispatch);
};

export const addLessonsAnswers = (
  exerciseId: string,
  lessonsCategoryId: string,
  lessonsQuestionId: string,
  data: LessonsAnswerCreateInput,
  userId: string,
) => (dispatch: Dispatch) => {
  const uri = `/api/player/lessons/exercise/${exerciseId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}/lessons_answers?userId=${userId}`;
  return postReferential(schema.arrayOfLessonsAnswers, uri, data)(dispatch);
};

export const fetchExercisesGlobalScores = (exercisesGlobalScoresInput: ExercisesGlobalScoresInput) => {
  const data = exercisesGlobalScoresInput;
  const uri = `${EXERCISE_URI}/global-scores`;
  return simplePostCall(uri, data);
};

export const checkExerciseTagRules = (exerciseId: string, newTagIds: string[]) => {
  const uri = `/api/exercises/${exerciseId}/check-rules`;
  const input = { new_tags: newTagIds };
  return simplePostCall(uri, input);
};

export const updateCustomDashboard = (exerciseId: string, customDashboardId: string | null) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/custom-dashboards/${customDashboardId}`;
  return putReferential(schema.lessonsQuestion, uri, {})(dispatch);
};

export const fetchScenarioFromSimulation = (simulationId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${simulationId}/scenario`;
  return getReferential(scenario, uri)(dispatch);
};

export const fetchCustomDashboardFromSimulation = (simulationId: string) => {
  return simpleCall(`${EXERCISE_URI}/${simulationId}/dashboard`);
};

export const countBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/count/${widgetId}`, parameters);
};

export const averageBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/average/${widgetId}`, parameters);
};

export const seriesBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/series/${widgetId}`, parameters);
};

export const entitiesBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesBySimulation = (simulationId: string, widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/entities-runtime/${widgetId}`, input);
};

export const attackPathsBySimulation = (simulationId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`${EXERCISE_URI}/${simulationId}/dashboard/attack-paths/${widgetId}`, parameters);
};
