import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from '../scenario/ScenarioType';

const CHAINED = 'CHAINED';

// The types the catalogue gives its scenarios.
export const CATALOG_SCENARIO_TYPES = ['TIME_BASED', CHAINED];

// A catalogue type as the scenario pages name it.
export const scenarioTypeOf = (type: string): ScenarioTypeValue => (type === CHAINED ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED);

export const catalogScenarioType = (scenario: CatalogScenarioOutput): ScenarioTypeValue => scenarioTypeOf(scenario.catalog_scenario_type);
