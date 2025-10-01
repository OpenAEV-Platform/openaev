import { type Exercise, type Scenario, type Team, type User } from '../../utils/api-types';

export interface TeamsHelper {
  getExerciseTeams: (exerciseId: Exercise['exercise_id']) => Team[];
  getScenarioTeams: (scenarioId: Scenario['scenario_id']) => Team[];
  getTeam: (teamId: Team['team_id']) => Team;
  getTeams: () => Team[];
  getTeamsMap: () => Record<string, Team>;
  getTeamUsers: (teamId: Team['team_id']) => User[];
}
