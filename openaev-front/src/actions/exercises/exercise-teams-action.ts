import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { putReferential, simplePostCall } from '../../utils/Action';
import { type ExerciseUpdateTeamsInput, type Scenario, type SearchPaginationInput, type TeamOutput } from '../../utils/api-types';
import { arrayOfTeams } from '../schemas';
import { EXERCISE_URI } from './exercise-action';

export const searchExerciseTeams = (exerciseId: Scenario['scenario_id'], paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${EXERCISE_URI}/${exerciseId}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall<Page<TeamOutput>>(uri, paginationInput);
};

export const removeExerciseTeams = (exerciseId: Scenario['scenario_id'], data: ExerciseUpdateTeamsInput) => (dispatch: Dispatch) => putReferential<TeamOutput[]>(
  arrayOfTeams,
  `${EXERCISE_URI}/${exerciseId}/teams/remove`,
  data,
)(dispatch);

export const replaceExerciseTeams = (exerciseId: Scenario['scenario_id'], data: ExerciseUpdateTeamsInput) => (dispatch: Dispatch) => putReferential<TeamOutput[]>(
  arrayOfTeams,
  `${EXERCISE_URI}/${exerciseId}/teams/replace`,
  data,
)(dispatch);
