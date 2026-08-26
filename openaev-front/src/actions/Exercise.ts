import { type Dispatch } from 'redux';

import { delReferential, getReferential, postReferential, putReferential, simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../utils/Action';
import type {
  CreateExerciseInput,
  Exercise,
  ExerciseBulkProcessingInput,
  ExerciseTeamPlayersEnableInput,
  ExerciseUpdateStartDateInput,
  ExerciseUpdateStatusInput,
  ExerciseUpdateTagsInput,
  ExpectationUpdateInput,
  GetExercisesInput,
  LessonsInput,
  SearchPaginationInput,
  UpdateExerciseInput,
} from '../utils/api-types';
import * as schema from './Schema';

type AppDispatch = Dispatch;

export const fetchExercises = () => (dispatch: AppDispatch) => getReferential(schema.arrayOfExercises, '/api/exercises')(dispatch);

// The endpoint consumes a GetExercisesInput body ({ exercise_ids: [...] }), not a bare id array.
export const fetchExercisesById = (getExercisesInput: GetExercisesInput) => (dispatch: AppDispatch) => postReferential(schema.arrayOfExercises, '/api/exercises/search-by-id', getExercisesInput, undefined, false)(dispatch);

export const searchExercises = (paginationInput: SearchPaginationInput) => simplePostCall('/api/exercises/search', paginationInput);

// Resolve simulations by id to their full search projection (name + start date...),
// as a plain promise (no redux). Used where an option label needs the start date
// to disambiguate same-named simulations.
export const searchExercisesByIds = (exerciseIds: string[]) => simplePostCall('/api/exercises/search-by-id', { exercise_ids: exerciseIds }, undefined, false);

export const bulkDeleteExercises = (input: ExerciseBulkProcessingInput) => simpleDelCall('/api/exercises', { data: input });

export const fetchExercise = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(schema.exercise, `/api/exercises/${exerciseId}`)(dispatch);

export const fetchExerciseInjectExpectations = (exerciseId: string) => (dispatch: AppDispatch) => getReferential(
  schema.arrayOfInjectexpectations,
  `/api/exercises/${exerciseId}/expectations`,
)(dispatch);

export const addExercise = (data: CreateExerciseInput) => (dispatch: AppDispatch) => postReferential(schema.exercise, '/api/exercises', data)(dispatch);

export const duplicateExercise = (exerciseId: string) => (dispatch: AppDispatch) => postReferential(schema.exercise, `/api/exercises/${exerciseId}`, null)(dispatch);

export const updateExercise = (exerciseId: string, data: UpdateExerciseInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}`,
  data,
)(dispatch);

export const updateExerciseStartDate = (exerciseId: string, data: ExerciseUpdateStartDateInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/start-date`,
  data,
)(dispatch);

export const updateExerciseLessons = (exerciseId: string, data: LessonsInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/lessons`,
  data,
)(dispatch);

export const fetchExerciseTeams = (exerciseId: string) => (dispatch: AppDispatch) => {
  const uri = `/api/exercises/${exerciseId}/teams`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const enableExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/enable`,
  data,
)(dispatch);

export const disableExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/disable`,
  data,
)(dispatch);

export const addExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/add`,
  data,
)(dispatch);

export const removeExerciseTeamPlayers = (exerciseId: string, teamId: string, data: ExerciseTeamPlayersEnableInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/teams/${teamId}/players/remove`,
  data,
)(dispatch);

export const updateExerciseTags = (exerciseId: string, data: ExerciseUpdateTagsInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/tags`,
  data,
)(dispatch);

export const updateExerciseStatus = (exerciseId: string, status: ExerciseUpdateStatusInput) => (dispatch: AppDispatch) => putReferential(
  schema.exercise,
  `/api/exercises/${exerciseId}/status`,
  status,
)(dispatch);

export const updateInjectExpectation = (injectExpectationId: string, data: ExpectationUpdateInput) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}`,
  data,
)(dispatch);

export const deleteInjectExpectationResult = (injectExpectationId: string, sourceId: string) => (dispatch: AppDispatch) => putReferential(
  schema.injectexpectation,
  `/api/expectations/${injectExpectationId}/${sourceId}/delete`,
  {},
)(dispatch);

export const deleteExercise = (exerciseId: string) => (dispatch: AppDispatch) => delReferential(
  `/api/exercises/${exerciseId}`,
  'exercises',
  exerciseId,
)(dispatch);

export const importingExercise = (data: FormData) => (dispatch: AppDispatch) => {
  const uri = '/api/exercises/import';
  return postReferential(null, uri, data)(dispatch);
};

export const fetchPlayerExercise = (exerciseId: string, userId: string | null) => (dispatch: AppDispatch) => {
  const uri = `/api/player/exercises/${exerciseId}${userId ? `?userId=${userId}` : ''}`;
  return getReferential(schema.exercise, uri)(dispatch);
};

// -- HEALTHCHECKS --

export const searchExerciseHealthchecks = (exerciseId: Exercise['exercise_id']) => {
  const uri = `/api/exercises/${exerciseId}/healthchecks`;
  return simpleCall(uri);
};

// -- EXPECTATIONS DRIFT --

export const fetchExerciseExpectationsDrift = (exerciseId: Exercise['exercise_id']) => {
  const uri = `/api/exercises/${exerciseId}/expectations-drift`;
  return simpleCall(uri);
};

export const realignExerciseExpectations = (exerciseId: Exercise['exercise_id']) => {
  const uri = `/api/exercises/${exerciseId}/expectations-drift/realign`;
  return simplePostCall(uri);
};

// Dismissal is stored in database (not local storage) so it is shared between
// users. The generic success toast is disabled: the caller notifies with a
// dismissal-specific message.
export const dismissExerciseExpectationsDrift = (exerciseId: Exercise['exercise_id'], dismissed: boolean) => {
  const uri = `/api/exercises/${exerciseId}/expectations-drift/dismiss`;
  return simplePutCall(uri, { dismissed }, undefined, true, false);
};
