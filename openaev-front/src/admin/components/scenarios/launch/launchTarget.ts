import { type ScenarioTypeValue } from '../scenario/ScenarioType';

// The scenario a launch starts from.
export interface LaunchTarget {
  scenarioId: string;
  name: string;
  type: ScenarioTypeValue;
}
