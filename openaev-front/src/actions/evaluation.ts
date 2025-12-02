import { type Dispatch } from 'redux';

import { getReferential, postReferential, putReferential } from '../utils/Action';
import { type Evaluation, type EvaluationInput } from '../utils/api-types';
import { arrayOfEvaluations, evaluation } from './schemas';

export const fetchExerciseEvaluations = (exerciseId: string, objectiveId: string) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return getReferential<Evaluation[]>(arrayOfEvaluations, uri)(dispatch);
};

export const updateExerciseEvaluation = (exerciseId: string, objectiveId: string, evaluationId: string, data: EvaluationInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential<Evaluation>(evaluation, uri, data)(dispatch);
};

export const addExerciseEvaluation = (exerciseId: string, objectiveId: string, data: EvaluationInput) => (dispatch: Dispatch) => {
  const uri = `/api/exercises/${exerciseId}/objectives/${objectiveId}/evaluations`;
  return postReferential<Evaluation>(evaluation, uri, data)(dispatch);
};

export const fetchScenarioEvaluations = (scenarioId: string, objectiveId: string) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return getReferential<Evaluation[]>(arrayOfEvaluations, uri)(dispatch);
};

export const updateScenarioEvaluation = (scenarioId: string, objectiveId: string, evaluationId: string, data: EvaluationInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations/${evaluationId}`;
  return putReferential<Evaluation>(evaluation, uri, data)(dispatch);
};

export const addScenarioEvaluation = (scenarioId: string, objectiveId: string, data: EvaluationInput) => (dispatch: Dispatch) => {
  const uri = `/api/scenarios/${scenarioId}/objectives/${objectiveId}/evaluations`;
  return postReferential<Evaluation>(evaluation, uri, data)(dispatch);
};
