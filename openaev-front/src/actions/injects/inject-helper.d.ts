import { type Exercise, type Inject, type InjectExpectation, type Scenario, type Team } from '../../utils/api-types';

export interface InjectHelper {
  getInject: (injectId: Inject['inject_id']) => Inject;
  getInjectsMap: () => Record<string, Inject>;

  getExerciseInjects: (exerciseId: Exercise['exercise_id']) => Inject[];
  getExerciseInjectExpectations: (scenarioId: Scenario['scenario_id']) => InjectExpectation[];
  getTeamExerciseInjects: (teamId: Team['team_id']) => Inject[];

  getScenarioInjects: (scenarioId: Scenario['scenario_id']) => Inject[];
  getTeamScenarioInjects: (teamId: Team['team_id']) => Inject[];
}
