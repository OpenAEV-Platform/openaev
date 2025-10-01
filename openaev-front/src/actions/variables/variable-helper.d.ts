import { type Exercise, type Scenario, type Variable } from '../../utils/api-types';

export interface VariablesHelper {
  getExerciseVariables: (exerciseId: Exercise['exercise_id']) => Variable[];
  getScenarioVariables: (scenarioId: Scenario['scenario_id']) => Variable[];
}
