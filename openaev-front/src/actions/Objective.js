import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchExerciseObjectives = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives`;
  return getReferential(schema.arrayOfObjectives, uri)(dispatch);
};

export const updateExerciseObjective = (exerciseId, objectiveId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}`;
  return putReferential(schema.objective, uri, data)(dispatch);
};

export const addExerciseObjective = (exerciseId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives`;
  return postReferential(schema.objective, uri, data)(dispatch);
};

export const deleteExerciseObjective = (exerciseId, objectiveId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};

export const fetchScenarioObjectives = scenarioId => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives`;
  return getReferential(schema.arrayOfObjectives, uri)(dispatch);
};

export const updateScenarioObjective = (scenarioId, objectiveId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}`;
  return putReferential(schema.objective, uri, data)(dispatch);
};

export const addScenarioObjective = (scenarioId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives`;
  return postReferential(schema.objective, uri, data)(dispatch);
};

export const deleteScenarioObjective = (scenarioId, objectiveId) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};
