import { queryOptions } from '@tanstack/react-query';

import { ENTITY_TYPES } from '../../utils/query/entityChange';
import { liveMeta } from '../../utils/query/live';
import { type AssetOverview, getAssetOverview } from './endpoint-actions';

// Every parameter of a query is part of its key.
export const assetKeys = {
  all: ['assets'] as const,
  details: () => [...assetKeys.all, 'detail'] as const,
  detail: (assetId: string) => [...assetKeys.details(), assetId] as const,
};

export const assetQueries = {
  detail: (assetId: string) => queryOptions({
    queryKey: assetKeys.detail(assetId),
    queryFn: ({ signal }) => getAssetOverview({
      assetId,
      signal,
    }),
    // Refetched when the asset, one of its agents or one of its groups changes. A deleted asset
    // answers 404: the page shows it as deleted.
    meta: liveMeta<AssetOverview>({
      refs: asset => [
        {
          type: ENTITY_TYPES.ASSETS,
          id: asset.asset_id,
        },
        ...asset.asset_agents.map(agent => ({
          type: ENTITY_TYPES.AGENTS,
          id: agent.agent_id,
        })),
        ...(asset.asset_asset_groups ?? []).map(group => ({
          type: ENTITY_TYPES.ASSET_GROUPS,
          id: group.asset_group_id,
        })),
      ],
    }),
  }),
};
