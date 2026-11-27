import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from './scenario/ScenarioType';

export const SCENARIO_TYPES: ScenarioTypeValue[] = [SCENARIO_TYPE_TIME_BASED, SCENARIO_TYPE_CHAINED];

export const scenarioCategories = new Map([
  ['global-crisis', 'Global Crisis'],
  ['attack-scenario', 'Attack Scenario'],
  ['media-pressure', 'Media Pressure'],
  ['data-exfiltration', 'Data Exfiltration'],
  ['capture-the-flag', 'Capture The Flag'],
  ['vulnerability-exploitation', 'Vulnerability Exploitation'],
  ['lateral-movement', 'Lateral Movement'],
  ['url-filtering', 'URL Filtering'],
]);

// The English name of a category, the category itself when it has none.
export const scenarioCategoryLabel = (category: string) => scenarioCategories.get(category) ?? category;
