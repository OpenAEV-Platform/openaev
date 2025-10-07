import { findAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { findEndpoints } from '../../../actions/assets/endpoint-actions';
import type { EndpointContextType } from './EndpointContext';

const endpointContextForAtomicTesting = (): EndpointContextType => {
  return {
    async fetchEndpointsByIds(endpointIds: string[]) {
      return findEndpoints(endpointIds);
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findAssetGroups(assetGroupIds);
    },
  };
};

export default endpointContextForAtomicTesting;
