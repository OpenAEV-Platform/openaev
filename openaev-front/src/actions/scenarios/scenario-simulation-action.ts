import { simpleCall } from '../../utils/Action';
import type { Scenario } from '../../utils/api-types';
import { SCENARIO_URI } from './scenario-actions';

// -- OPTION --

// eslint-disable-next-line import/prefer-default-export
export const searchScenarioSimulationsAsOption = (scenarioId: Scenario['scenario_id'], searchText: string = '') => {
  const params = { searchText };
  const uri = `${SCENARIO_URI}/${scenarioId}/simulations/options`;
  return simpleCall(uri, { params });
};
