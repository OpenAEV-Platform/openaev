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
import { type Option, type SearchPaginationInput, type Team, type TeamCreateInput, type TeamOutput, type TeamUpdateInput, type User } from '../../utils/api-types';
import { arrayOfTeams, arrayOfUsers, team } from '../schemas';

const TEAMS_URI = '/api/teams';

export const fetchTeams = () => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}`;
  return getReferential<Team[]>(arrayOfTeams, uri)(dispatch);
};

export const fetchTeam = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return getReferential<Team>(team, uri)(dispatch);
};
export const searchTeams = (searchPaginationInput: SearchPaginationInput) => {
  const data = searchPaginationInput;
  const uri = `${TEAMS_URI}/search`;
  return simplePostCall<Page<TeamOutput>>(uri, data);
};

export const findTeams = (teamIds: string[]) => {
  const data = teamIds;
  const uri = `${TEAMS_URI}/find`;
  return simplePostCall<TeamOutput[]>(uri, data);
};

export const fetchTeamPlayers = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}/players`;
  return getReferential<User[]>(arrayOfUsers, uri)(dispatch);
};

export const updateTeam = (teamId: Team['team_id'], data: TeamUpdateInput) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return putReferential<Team>(team, uri, data)(dispatch);
};

export const updateTeamPlayers = (teamId: Team['team_id'], data: { team_users: User['user_id'][] }) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}/players`;
  return putReferential<Team>(team, uri, data)(dispatch);
};

export const addTeam = (data: TeamCreateInput) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}`;
  return postReferential<Team>(team, uri, data)(dispatch);
};

export const deleteTeam = (teamId: Team['team_id']) => (dispatch: Dispatch) => {
  const uri = `${TEAMS_URI}/${teamId}`;
  return delReferential(uri, 'teams', teamId)(dispatch);
};

export const searchTeamsAsOption = (searchText: string = '', sourceId: string = '', inputFilterOption: string = '') => {
  const params = {
    searchText,
    sourceId,
    inputFilterOption,
  };
  return simpleCall<Option[]>(`${TEAMS_URI}/options`, { params });
};

export const searchTeamByIdAsOption = (ids: string[]) => {
  return simplePostCall<Option[]>(`${TEAMS_URI}/options`, ids);
};
