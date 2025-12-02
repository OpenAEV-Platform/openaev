import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential } from '../utils/Action';
import { type Objective, type ObjectiveInput } from '../utils/api-types';
import { arrayOfObjectives, objective } from './schemas';

export const fetchExerciseObjectives = (exerciseId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives`;
  return getReferential<Objective[]>(arrayOfObjectives, uri)(dispatch);
};

export const updateExerciseObjective = (exerciseId: string, objectiveId: string, data: ObjectiveInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}`;
  return putReferential<Objective>(objective, uri, data)(dispatch);
};

export const addExerciseObjective = (exerciseId: string, data: ObjectiveInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives`;
  return postReferential<Objective>(objective, uri, data)(dispatch);
};

export const deleteExerciseObjective = (exerciseId: string, objectiveId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};

export const fetchScenarioObjectives = (scenarioId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives`;
  return getReferential<Objective[]>(arrayOfObjectives, uri)(dispatch);
};

export const updateScenarioObjective = (scenarioId: string, objectiveId: string, data: ObjectiveInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}`;
  return putReferential<Objective>(objective, uri, data)(dispatch);
};

export const addScenarioObjective = (scenarioId: string, data: ObjectiveInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives`;
  return postReferential<Objective>(objective, uri, data)(dispatch);
};

export const deleteScenarioObjective = (scenarioId: string, objectiveId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}`;
  return delReferential(uri, 'objectives', objectiveId)(dispatch);
};
