import { type AxiosResponse } from 'axios';

import { findAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { findEndpoints } from '../../../actions/assets/endpoint-actions';
import { type EndpointOutput } from '../../api-types';
import type { EndpointContextType } from './EndpointContext';

const endpointContextForAtomicTesting = (): EndpointContextType => {
  return {
    async fetchEndpointsByIds(endpointIds: string[]) {
      // cast Endpoint to EndpointOutput
      return findEndpoints(endpointIds) as Promise<AxiosResponse<EndpointOutput[]>>;
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findAssetGroups(assetGroupIds);
    },
  };
};

export default endpointContextForAtomicTesting;
