import { getReferential, postReferential, putReferential } from '../utils/Action';
import * as schema from './Schema';

export const fetchExerciseEvaluations = (exerciseId, objectiveId) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return getReferential(schema.arrayOfEvaluations, uri)(dispatch);
};

export const updateExerciseEvaluation = (exerciseId, objectiveId, evaluationId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential(schema.evaluation, uri, data)(dispatch);
};

export const addExerciseEvaluation = (exerciseId, objectiveId, data) => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return postReferential(schema.evaluation, uri, data)(dispatch);
};

export const fetchScenarioEvaluations = (scenarioId, objectiveId) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return getReferential(schema.arrayOfEvaluations, uri)(dispatch);
};

export const updateScenarioEvaluation = (scenarioId, objectiveId, evaluationId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential(schema.evaluation, uri, data)(dispatch);
};

export const addScenarioEvaluation = (scenarioId, objectiveId, data) => (dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return postReferential(schema.evaluation, uri, data)(dispatch);
};
