import { type Dispatch } from 'redux';

import { type Page } from '../../components/common/queryable/Page';
import { putReferential, simplePostCall } from '../../utils/Action';
import { type Scenario, type ScenarioUpdateTeamsInput, type SearchPaginationInput, type TeamOutput } from '../../utils/api-types';
import { arrayOfTeams } from '../schemas';
import { SCENARIO_URI } from './scenario-actions';

export const searchScenarioTeams = (scenarioId: Scenario['scenario_id'], paginationInput: SearchPaginationInput, contextualOnly: boolean = false) => {
  const uri = `${SCENARIO_URI}/${scenarioId}/teams/search?contextualOnly=${contextualOnly}`;
  return simplePostCall<Page<TeamOutput>>(uri, paginationInput);
};

export const removeScenarioTeams = (scenarioId: Scenario['scenario_id'], data: ScenarioUpdateTeamsInput) => (dispatch: Dispatch) => putReferential<TeamOutput[]>(
  arrayOfTeams,
  `${SCENARIO_URI}/${scenarioId}/teams/remove`,
  data,
)(dispatch);

export const replaceScenarioTeams = (scenarioId: Scenario['scenario_id'], data: ScenarioUpdateTeamsInput) => (dispatch: Dispatch) => putReferential<TeamOutput[]>(
  arrayOfTeams,
  `${SCENARIO_URI}/${scenarioId}/teams/replace`,
  data,
)(dispatch);
