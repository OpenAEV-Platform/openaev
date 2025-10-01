import {
  addScenarioTeamPlayers,
  disableScenarioTeamPlayers,
  enableScenarioTeamPlayers,
  fetchScenarioTeams,
  removeScenarioTeamPlayers,
} from '../../../../../actions/scenarios/scenario-actions';
import { removeScenarioTeams, replaceScenarioTeams, searchScenarioTeams } from '../../../../../actions/scenarios/scenario-teams-action';
import { addTeam } from '../../../../../actions/teams/team-actions';
import { type Page } from '../../../../../components/common/queryable/Page';
import { type Scenario, type ScenarioTeamUser, type SearchPaginationInput, type Team, type TeamCreateInput, type TeamOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type UserStore } from '../../../teams/players/Player';

const teamContextForScenario = (scenarioId: Scenario['scenario_id'], scenarioTeamsUsers: Scenario['scenario_teams_users'], allUsersNumber = 0, allUsersEnabledNumber = 0) => {
  const dispatch = useAppDispatch();

  return {
    async onAddUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(addScenarioTeamPlayers(scenarioId, teamId, { scenario_team_players: userIds }));
      return dispatch(fetchScenarioTeams(scenarioId));
    },
    async onRemoveUsersTeam(teamId: Team['team_id'], userIds: UserStore['user_id'][]): Promise<void> {
      await dispatch(removeScenarioTeamPlayers(scenarioId, teamId, { scenario_team_players: userIds }));
      return dispatch(fetchScenarioTeams(scenarioId));
    },
    onCreateTeam(team: TeamCreateInput): Promise<{ result: string }> {
      return dispatch(addTeam({
        ...team,
        team_scenarios: [scenarioId],
      }));
    },
    allUsersEnabledNumber: allUsersEnabledNumber,
    allUsersNumber: allUsersNumber,
    checkUserEnabled(teamId: Team['team_id'], userId: UserStore['user_id']): boolean {
      return (scenarioTeamsUsers ?? [])?.filter((o: ScenarioTeamUser) => o.scenario_id === scenarioId && o.team_id === teamId && userId === o.user_id).length > 0;
    },
    computeTeamUsersEnabled(teamId: Team['team_id']) {
      return scenarioTeamsUsers?.filter((o: ScenarioTeamUser) => o.team_id === teamId).length ?? 0;
    },
    onRemoveTeam(teamId: Team['team_id']): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(removeScenarioTeams(scenarioId, { scenario_teams: [teamId] }));
    },
    onReplaceTeam(teamIds: Team['team_id'][]): Promise<{
      result: string[];
      entities: { teams: Record<string, Team> };
    }> {
      return dispatch(replaceScenarioTeams(scenarioId, { scenario_teams: teamIds }));
    },
    onToggleUser(teamId: Team['team_id'], userId: UserStore['user_id'], userEnabled: boolean): void {
      if (userEnabled) {
        dispatch(disableScenarioTeamPlayers(scenarioId, teamId, { scenario_team_players: [userId] }));
      } else {
        dispatch(enableScenarioTeamPlayers(scenarioId, teamId, { scenario_team_players: [userId] }));
      }
    },
    searchTeams(input: SearchPaginationInput, contextualOnly?: boolean): Promise<{ data: Page<TeamOutput> }> {
      return searchScenarioTeams(scenarioId, input, contextualOnly);
    },
  };
};

export default teamContextForScenario;
