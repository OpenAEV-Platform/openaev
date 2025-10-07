import { findScenarioAssetGroupsByIds } from '../../../actions/asset_groups/assetgroup-action';
import { findScenarioEndpointsByIds } from '../../../actions/assets/endpoint-actions';
import type { Scenario } from '../../api-types';
import type { EndpointContextType } from './EndpointContext';

const endpointContextForScenario = (scenarioId: Scenario['scenario_id']): EndpointContextType => {
  return {
    async fetchEndpointsByIds(endpointIds: string[]) {
      return findScenarioEndpointsByIds(scenarioId, endpointIds);
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findScenarioAssetGroupsByIds(scenarioId, assetGroupIds);
    },
  };
};

export default endpointContextForScenario;
