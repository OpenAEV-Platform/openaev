import { type RawPaginationScenario, type Scenario } from '../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from './scenario/ScenarioType';

// A row of the scenario search: the scenario plus the fields it computes for the cards and rows.
export type ScenarioRow = Scenario & Pick<RawPaginationScenario,
  'scenario_workflow_id' | 'scenario_author_name' | 'scenario_domains' | 'scenario_version' | 'scenario_last_run_at'>;

// A scenario with a chaining workflow is chained.
export const scenarioTypeOf = (scenario: { scenario_workflow_id?: string }): ScenarioTypeValue => (
  scenario.scenario_workflow_id ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED
);
