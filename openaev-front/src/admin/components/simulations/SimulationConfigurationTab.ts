// Tab indices are surfaced as a named enum (rather than magic numbers) so deep-link callers
// (e.g. the "manage custom variables" link in AvailableVariablesDialog) can target a specific
// tab without duplicating/guessing its position, and stay correct if tabs are ever reordered.
enum SimulationConfigurationTab {
  TEAMS = 0,
  VARIABLES = 1,
  MEDIA_PRESSURE = 2,
}

export const SIMULATION_CONFIGURATION_QUERY_PARAM = 'config';
export const SIMULATION_CONFIGURATION_VARIABLES_QUERY_VALUE = 'variables';
export const buildSimulationVariablesConfigurationUrl = (exerciseId: string, returnPath: string = `/admin/simulations/${exerciseId}/injects`) => (
  `${returnPath}${returnPath.includes('?') ? '&' : '?'}${SIMULATION_CONFIGURATION_QUERY_PARAM}=${SIMULATION_CONFIGURATION_VARIABLES_QUERY_VALUE}`
);

export default SimulationConfigurationTab;
