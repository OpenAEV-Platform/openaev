import { type Dispatch } from 'redux';

import { DATA_DELETE_BATCH_SUCCESS } from '../../constants/ActionTypes';
import {
  delReferential,
  getReferential,
  postReferential,
  putReferential,
  simpleCall,
  simpleDelCall,
  simplePostCall,
} from '../../utils/Action';
import { type SearchPaginationInput, type Team, type TeamBulkProcessingInput, type TeamCreateInput, type TeamUpdateInput, type User } from '../../utils/api-types';
import * as schema from '../Schema';

const TEAMS_URI = '/api/teams';

export const fetchTeams = () => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}`;
  return getReferential(schema.arrayOfTeams, uri)(dispatch);
};

export const fetchTeam = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return getReferential(schema.team, uri)(dispatch);
};
export const searchTeams = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${TEAMS_URI}/search`;
  return simplePostCall(uri, data);
};

export const findTeams = (teamIds: string[]) => {
  const data = teamIds;
  const uri = `${TEAMS_URI}/find`;
  return simplePostCall(uri, data);
};

// "Injects played" for the team detail page: every inject (atomic testing or simulation inject)
// that concerns the team - targeted directly or evidenced by the table-top expectations persisted
// at execution time. Same scope as the team expectation counters.
export const searchInjectsForTeam = (teamId: string, searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${TEAMS_URI}/${teamId}/injects/search`, searchPaginationInput);
};

export const fetchTeamPlayers = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}/players`;
  return getReferential(schema.arrayOfUsers, uri)(dispatch);
};

export const updateTeam = (teamId: Team['team_id'], data: TeamUpdateInput) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return putReferential(schema.team, uri, data)(dispatch);
};

export const updateTeamPlayers = (teamId: Team['team_id'], data: { team_users: User['user_id'][] }) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}/players`;
  return putReferential(schema.team, uri, data)(dispatch);
};

export const addTeam = (data: TeamCreateInput) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}`;
  return postReferential(schema.team, uri, data)(dispatch);
};

export const deleteTeam = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return delReferential(uri, 'teams', teamId)(dispatch);
};

export const bulkDeleteTeams = (input: TeamBulkProcessingInput) => {
  return (dispatch: Dispatch) => simpleDelCall(TEAMS_URI, { data: input })
    .then((result) => {
      const deletedIds: string[] = result.data ?? [];
      if (deletedIds.length > 0) {
        dispatch({
          type: DATA_DELETE_BATCH_SUCCESS,
          payload: deletedIds.map(id => ({
            type: 'teams',
            id,
          })),
        });
      }
      return result;
    });
};

export const searchTeamsAsOption = (searchText: string = '', sourceId: string = '', inputFilterOption: string = '') => {
  const params = {
    searchText,
    sourceId,
    inputFilterOption,
  };
  return simpleCall(`${TEAMS_URI}/options`, { params });
};

export const searchTeamByIdAsOption = (ids: string[]) => {
  return simplePostCall(`${TEAMS_URI}/options`, ids);
};
