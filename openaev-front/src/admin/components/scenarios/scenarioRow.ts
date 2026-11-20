import { type RawPaginationScenario, type Scenario } from '../../../utils/api-types';

// A row of the scenario search: the scenario plus the fields it computes for the cards and rows.
export type ScenarioRow = Scenario & Pick<RawPaginationScenario,
  'scenario_workflow_id' | 'scenario_author_name' | 'scenario_domains' | 'scenario_version' | 'scenario_last_run_at'>;
