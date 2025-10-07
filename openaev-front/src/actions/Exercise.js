import { delReferential, getReferential, postReferential, putReferential, simplePostCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchExercises = () => dispatch => getReferential(schema.arrayOfExercises, '/api/exercises')(dispatch);

export const fetchExercisesById = exerciseIds => dispatch => postReferential(schema.arrayOfExercises, '/api/exercises/search-by-id', exerciseIds, undefined, false)(dispatch);

export const searchExercises = paginationInput => simplePostCall('/api/exercises/search', paginationInput);

export const fetchExercise = exerciseId => dispatch => getReferential(schema.exercise, `/api/exercises/${exerciseId}`)(dispatch);

export const fetchExerciseInjectExpectations = exerciseId => dispatch => getReferential(
  schema.arrayOfInjectexpectations,
  `/api/exercises/${exerciseId}/expectations`,
)(dispatch);

export const addExercise = data => dispatch => postReferential(schema.exercise, '/api/exercises', data)(dispatch);

export const duplicateExercise = exerciseId => dispatch => postReferential(schema.exercise, `/api/exercises/${exerciseId}`, null)(dispatch);

export const updateExercise = (exerciseId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}`,
  data,
)(dispatch);

export const updateExerciseStartDate = (exerciseId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/start-date`,
  data,
)(dispatch);

export const updateExerciseLessons = (exerciseId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/lessons`,
  data,
)(dispatch);

export const fetchExerciseTeams = exerciseId => (dispatch) => {
  const uri = `/api/exercises/${exerciseId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableExerciseTeamPlayers = (exerciseId, teamId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableExerciseTeamPlayers = (exerciseId, teamId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addExerciseTeamPlayers = (exerciseId, teamId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeExerciseTeamPlayers = (exerciseId, teamId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

export const updateExerciseTags = (exerciseId, data) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/tags`,
  data,
)(dispatch);

export const updateExerciseStatus = (exerciseId, status) => dispatch => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/status`,
  status,
)(dispatch);

export const updateInjectExpectation = (injectExpectationId, data) => dispatch => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}`,
  data,
)(dispatch);

export const deleteInjectExpectationResult = (injectExpectationId, sourceId) => dispatch => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}/${sourceId}/delete`,
)(dispatch);

export const deleteExercise = exerciseId => dispatch => delReferential(
  `/api/exercises/${exerciseId}`,
  'exercises',
  exerciseId,
)(dispatch);

export const importingExercise = data => (dispatch) => {
  const uri = '/api/exercises/import';
  return postReferential(null, uri, data)(dispatch);
};

export const fetchPlayerExercise = (exerciseId, userId) => (dispatch) => {
  const uri = `/api/player/exercises/${exerciseId}?userId=${userId}`;
  return getReferential(schema.exercise, uri)(dispatch);
};
