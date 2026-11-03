import { queryOptions } from '@tanstack/react-query';

import { type EndpointOverviewOutput } from '../../utils/api-types';
import { ENTITY_TYPES } from '../../utils/query/entityChange';
import { liveMeta } from '../../utils/query/live';
import { getAssetOverview } from './endpoint-actions';

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
    // Refetched when the asset or one of its agents changes, and on any asset group change: group
    // membership is owned by the group (static assets, dynamic filter), so joining a group only
    // streams a change of that group. A deleted asset answers 404: the page shows it as deleted.
    meta: liveMeta<EndpointOverviewOutput>({
      watch: [ENTITY_TYPES.ASSET_GROUPS],
      refs: asset => [
        {
          type: ENTITY_TYPES.ASSETS,
          id: asset.asset_id,
        },
        ...asset.asset_agents.map(agent => ({
          type: ENTITY_TYPES.AGENTS,
          id: agent.agent_id,
        })),
      ],
    }),
  }),
};
