import { createContext } from 'react';

import type { AssetGroupOutput, EndpointOutput } from '../../api-types';

export type EndpointContextType = {
  fetchEndpointsByIds: (ids: string[]) => Promise<{ data: EndpointOutput[] }>;
  fetchAssetGroupsByIds: (ids: string[]) => Promise<{ data: AssetGroupOutput[] }>;
};

export const EndpointContext = createContext<EndpointContextType>({
  fetchEndpointsByIds(_ids: string[]): Promise<{ data: EndpointOutput[] }> {
    return new Promise<{ data: EndpointOutput[] }>(() => {
    });
  },
  fetchAssetGroupsByIds(_ids: string[]): Promise<{ data: AssetGroupOutput[] }> {
    return new Promise<{ data: AssetGroupOutput[] }>(() => {
    });
  },
});
