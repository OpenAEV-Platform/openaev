import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type InjectsImportInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type Scenario,
  type ScenarioInput,
  type ScenarioRecurrenceInput,
  type ScenarioTeamPlayersEnableInput,
  type SearchPaginationInput,
  type Team,
  type UpdateScenarioInput,
  type WidgetToEntitiesInput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import * as schema from '../Schema';
import { arrayOfScenarios, scenario } from './scenario-schema';

export const SCENARIO_URI = '/api/scenarios';

export const addScenario = (data: ScenarioInput) => (dispatch: Dispatch) => {
  return postReferential(scenario, SCENARIO_URI, data)(dispatch);
};

export const fetchScenarios = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfScenarios, SCENARIO_URI)(dispatch);
};

export const searchScenarios = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = '/api/scenarios/search';
  return simplePostCall(uri, data);
};

export const fetchScenario = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return getReferential(scenario, uri)(dispatch);
};

export const updateScenario = (
  scenarioId: Scenario['scenario_id'],
  data: UpdateScenarioInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return putReferential(scenario, uri, data)(dispatch);
};

export const deleteScenario = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return delReferential(uri, scenario.key, scenarioId)(dispatch);
};

export const exportScenarioUri = (scenarioId: Scenario['scenario_id'], exportTeams: boolean, exportPlayers: boolean, exportVariableValues: boolean) => {
  return `${SCENARIO_URI}/${scenarioId}/export?isWithTeams=${exportTeams}&isWithPlayers=${exportPlayers}&isWithVariableValues=${exportVariableValues}`;
};

export const importScenario = (formData: FormData) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/import`;
  return postReferential(null, uri, formData)(dispatch);
};

export const duplicateScenario = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return postReferential(scenario, uri, null)(dispatch);
};

// -- SCENARIO TO EXERCISE

export const createRunningExerciseFromScenario = (scenarioId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/exercise/running`;
  return simplePostCall(uri);
};

// -- TEAMS --

export const fetchScenarioTeams = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableScenarioTeamPlayers = (scenarioId: Scenario['scenario_id'], teamId: Team['team_id'], data: ScenarioTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableScenarioTeamPlayers = (scenarioId: Scenario['scenario_id'], teamId: Team['team_id'], data: ScenarioTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addScenarioTeamPlayers = (scenarioId: Scenario['scenario_id'], teamId: Team['team_id'], data: ScenarioTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeScenarioTeamPlayers = (scenarioId: Scenario['scenario_id'], teamId: Team['team_id'], data: ScenarioTeamPlayersEnableInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

// -- EXERCISES --

export const searchScenarioExercises = (scenarioId: Scenario['scenario_id'], paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `/api/scenarios/${scenarioId}/exercises/search`;
  return simplePostCall(uri, data);
};

// -- RECURRENCE --

export const updateScenarioRecurrence = (
  scenarioId: Scenario['scenario_id'],
  data: ScenarioRecurrenceInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/recurrence`;
  return putReferential(scenario, uri, data)(dispatch);
};

// -- STATISTIC --

export const fetchScenarioStatistic = (scenarioId: Scenario['scenario_id']) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/statistics`;
  return simpleCall(uri);
};

// -- IMPORT --

export const importXlsForScenario = (scenarioId: Scenario['scenario_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/import`;
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

export const dryImportXlsForScenario = (scenarioId: Scenario['scenario_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/dry`;
  return simplePostCall(uri, input)
    .then((response) => {
      return response;
    });
};

// -- OPTION --

export const searchScenarioAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${SCENARIO_URI}/options`, { params });
};

export const searchScenarioByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${SCENARIO_URI}/options`, ids);
};

export const searchScenarioCategoryAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall(`${SCENARIO_URI}/category/options`, { params });
};

// -- LESSONS --

export const updateScenarioLessons = (scenarioId: string, data: LessonsInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/lessons`,
  data,
)(dispatch);

export const fetchLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories`;
  return getReferential(schema.arrayOfLessonsCategories, uri)(dispatch);
};

export const updateLessonsCategory = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const updateLessonsCategoryTeams = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryTeamsInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/teams`;
  return putReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const addLessonsCategory = (scenarioId: string, data: LessonsCategoryCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories`;
  return postReferential(schema.lessonsCategory, uri, data)(dispatch);
};

export const deleteLessonsCategory = (scenarioId: string, lessonsCategoryId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return delReferential(uri, 'lessonscategorys', lessonsCategoryId)(dispatch);
};

export const applyLessonsTemplate = (scenarioId: string, lessonsTemplateId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_apply_template/${lessonsTemplateId}`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const fetchLessonsQuestions = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_questions`;
  return getReferential(schema.arrayOfLessonsQuestions, uri)(dispatch);
};

export const updateLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return putReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const addLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, data: LessonsQuestionCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions`;
  return postReferential(schema.lessonsQuestion, uri, data)(dispatch);
};

export const deleteLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return delReferential(uri, 'lessonsquestions', lessonsQuestionId)(dispatch);
};

export const emptyLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_empty`;
  return postReferential(schema.arrayOfLessonsCategories, uri, {})(dispatch);
};

export const checkScenarioTagRules = (scenarioId: string, newTagIds: string[]) => {
  const uri = `/api/scenarios/${scenarioId}/check-rules`;
  const input = { new_tags: newTagIds };
  return simplePostCall(uri, input);
};

export const fetchCustomDashboardFromScenario = (scenarioId: string) => {
  return simpleCall(`/api/scenarios/${scenarioId}/dashboard`);
};

export const countByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/scenarios/${scenarioId}/dashboard/count/${widgetId}`, parameters);
};

export const seriesByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/scenarios/${scenarioId}/dashboard/series/${widgetId}`, parameters);
};

export const entitiesByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/scenarios/${scenarioId}/dashboard/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesByByScenario = (scenarioId: string, widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall(`/api/scenarios/${scenarioId}/dashboard/entities-runtime/${widgetId}`, input);
};

export const attackPathsByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall(`/api/scenarios/${scenarioId}/dashboard/attack-paths/${widgetId}`, parameters);
};
