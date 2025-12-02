import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simplePostCall,
} from '../../utils/Action';
import {
  type CheckScenarioRulesOutput,
  type CustomDashboard,
  type EsAttackPath,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
  type Exercise,
  type ExerciseSimple,
  type GetScenariosInput,
  type HealthCheck,
  type ImportTestSummary,
  type InjectsImportInput,
  type LessonsCategory,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsInput,
  type LessonsQuestion,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type Option,
  type Scenario,
  type ScenarioInput,
  type ScenarioRecurrenceInput,
  type ScenarioStatistic,
  type ScenarioTeamPlayersEnableInput,
  type SearchPaginationInput,
  type Team,
  type UpdateScenarioInput,
  type WidgetToEntitiesInput,
  type WidgetToEntitiesOutput,
} from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { arrayOfLessonsCategories, arrayOfLessonsQuestions, arrayOfScenarios, arrayOfTeams, arrayOfUsers, lessonsCategory, lessonsQuestion, scenario } from '../schemas';

export const SCENARIO_URI = '/api/scenarios';

export const addScenario = (data: ScenarioInput) => (dispatch: Dispatch) => {
  return postReferential<Scenario>(scenario, SCENARIO_URI, data)(dispatch);
};

export const fetchScenarios = () => (dispatch: Dispatch) => {
  return getReferential<Scenario[]>(arrayOfScenarios, SCENARIO_URI)(dispatch);
};

export const fetchScenariosById = (exerciseIds: GetScenariosInput) => (dispatch: Dispatch) => {
  return postReferential(arrayOfScenarios, SCENARIO_URI + '/search-by-id', exerciseIds, undefined, false)(dispatch);
};

export const searchScenarios = (paginationInput: SearchPaginationInput) => {
  const data = paginationInput;
  const uri = `${SCENARIO_URI}/search`;
  return simplePostCall<Page<Scenario>>(uri, data);
};

export const fetchScenario = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return getReferential<Scenario>(scenario, uri)(dispatch);
};

export const updateScenario = (
  scenarioId: Scenario['scenario_id'],
  data: UpdateScenarioInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return putReferential<Scenario>(scenario, uri, data)(dispatch);
};

export const deleteScenario = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return delReferential(uri, scenario.key, scenarioId)(dispatch);
};

export const exportScenarioUri = (scenarioId: Scenario['scenario_id'], exportTeams: boolean, exportPlayers: boolean, exportVariableValues: boolean) => {
  return `${SCENARIO_URI}/${scenarioId}/export?isWithTeams=${exportTeams}&isWithPlayers=${exportPlayers}&isWithVariableValues=${exportVariableValues}`;
};

export const importScenario = (formData: FormData) => {
  const uri = `${SCENARIO_URI}/import`;
  return simplePostCall<void>(uri, formData);
};

export const duplicateScenario = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}`;
  return postReferential<Scenario>(scenario, uri, null)(dispatch);
};

// -- SCENARIO TO EXERCISE

export const createRunningExerciseFromScenario = (scenarioId: string) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/exercise/running`;
  return simplePostCall<Exercise>(uri);
};

// -- TEAMS --

export const fetchPlayersByScenario = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/players`;
  return getReferential(arrayOfUsers, uri)(dispatch);
};

export const fetchScenarioTeams = (scenarioId: Scenario['scenario_id']) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/teams`;
  return getReferential<Team[]>(arrayOfTeams, uri)(dispatch);
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
  return simplePostCall<ExerciseSimple[]>(uri, data);
};

// -- HEALTHCHEKS --

export const searchScenarioHealthcheks = (scenarioId: Scenario['scenario_id']) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/healthchecks`;
  return simpleCall<HealthCheck[]>(uri);
};

// -- RECURRENCE --

export const updateScenarioRecurrence = (
  scenarioId: Scenario['scenario_id'],
  data: ScenarioRecurrenceInput,
) => (dispatch: Dispatch) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/recurrence`;
  return putReferential<Scenario>(scenario, uri, data)(dispatch);
};

// -- STATISTIC --

export const fetchScenarioStatistic = (scenarioId: Scenario['scenario_id']) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/statistics`;
  return simpleCall<ScenarioStatistic>(uri);
};

// -- IMPORT --

export const importXlsForScenario = (scenarioId: Scenario['scenario_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/import`;
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

export const dryImportXlsForScenario = (scenarioId: Scenario['scenario_id'], importId: string, input: InjectsImportInput) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/xls/${importId}/dry`;
  return simplePostCall<ImportTestSummary>(uri, input)
    .then((response) => {
      return response;
    });
};

// -- OPTION --

export const searchScenarioAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${SCENARIO_URI}/options`, { params });
};

export const searchScenarioByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${SCENARIO_URI}/options`, ids);
};

export const searchScenarioCategoryAsOption = (searchText: string = '') => {
  const params = { searchText };
  return simpleCall<Option[]>(`${SCENARIO_URI}/category/options`, { params });
};

// -- LESSONS --

export const updateScenarioLessons = (scenarioId: string, data: LessonsInput) => (dispatch: Dispatch) => putReferential(
  scenario,
  `/api/scenarios/${scenarioId}/lessons`,
  data,
)(dispatch);

export const fetchLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories`;
  return getReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri)(dispatch);
};

export const updateLessonsCategory = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return putReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const updateLessonsCategoryTeams = (scenarioId: string, lessonsCategoryId: string, data: LessonsCategoryTeamsInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/teams`;
  return putReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const addLessonsCategory = (scenarioId: string, data: LessonsCategoryCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories`;
  return postReferential<LessonsCategory>(lessonsCategory, uri, data)(dispatch);
};

export const deleteLessonsCategory = (scenarioId: string, lessonsCategoryId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}`;
  return delReferential(uri, 'lessonscategorys', lessonsCategoryId)(dispatch);
};

export const applyLessonsTemplate = (scenarioId: string, lessonsTemplateId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_apply_template/${lessonsTemplateId}`;
  return postReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri, {})(dispatch);
};

export const fetchLessonsQuestions = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_questions`;
  return getReferential<LessonsQuestion[]>(arrayOfLessonsQuestions, uri)(dispatch);
};

export const updateLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return putReferential<LessonsQuestion>(lessonsQuestion, uri, data)(dispatch);
};

export const addLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, data: LessonsQuestionCreateInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions`;
  return postReferential<LessonsQuestion>(lessonsQuestion, uri, data)(dispatch);
};

export const deleteLessonsQuestion = (scenarioId: string, lessonsCategoryId: string, lessonsQuestionId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_categories/${lessonsCategoryId}/lessons_questions/${lessonsQuestionId}`;
  return delReferential(uri, 'lessonsquestions', lessonsQuestionId)(dispatch);
};

export const emptyLessonsCategories = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/lessons_empty`;
  return postReferential<LessonsCategory[]>(arrayOfLessonsCategories, uri, {})(dispatch);
};

export const checkScenarioTagRules = (scenarioId: string, newTagIds: string[]) => {
  const uri = `/api/scenarios/${scenarioId}/check-rules`;
  const input = { new_tags: newTagIds };
  return simplePostCall<CheckScenarioRulesOutput>(uri, input);
};

export const fetchCustomDashboardFromScenario = (scenarioId: string) => {
  return simpleCall<CustomDashboard>(`/api/scenarios/${scenarioId}/dashboard`);
};

export const countByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsCountInterval>(`/api/scenarios/${scenarioId}/dashboard/count/${widgetId}`, parameters);
};

export const seriesByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsSeries[]>(`/api/scenarios/${scenarioId}/dashboard/series/${widgetId}`, parameters);
};

export const entitiesByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsBase[]>(`/api/scenarios/${scenarioId}/dashboard/entities/${widgetId}`, parameters);
};

export const widgetToEntitiesByByScenario = (scenarioId: string, widgetId: string, input: WidgetToEntitiesInput) => {
  return simplePostCall<WidgetToEntitiesOutput>(`/api/scenarios/${scenarioId}/dashboard/entities-runtime/${widgetId}`, input);
};

export const attackPathsByScenario = (scenarioId: string, widgetId: string, parameters: Record<string, string | undefined>) => {
  return simplePostCall<EsAttackPath[]>(`/api/scenarios/${scenarioId}/dashboard/attack-paths/${widgetId}`, parameters);
};
