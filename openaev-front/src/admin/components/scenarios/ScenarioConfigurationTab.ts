// The scenario authoring context (teams, variables, media pressure) surfaced
// from the hero "Configuration" action, one section per tab, so the Injects
// tab stays focused on the inject list alone.
// Challenges are authored inside injects, so they are not configured here -
// the hero exposes a "Preview challenges page" action instead.
export enum ScenarioConfigurationTab {
  TEAMS = 0,
  VARIABLES = 1,
  MEDIA_PRESSURE = 2,
}
export const SCENARIO_CONFIGURATION_QUERY_PARAM = 'config';
export const SCENARIO_CONFIGURATION_VARIABLES_QUERY_VALUE = 'variables';
export const buildScenarioVariablesConfigurationUrl = (scenarioId: string, returnPath: string = `/admin/scenarios/${scenarioId}/injects`) => (
  `${returnPath}${returnPath.includes('?') ? '&' : '?'}${SCENARIO_CONFIGURATION_QUERY_PARAM}=${SCENARIO_CONFIGURATION_VARIABLES_QUERY_VALUE}`
);
