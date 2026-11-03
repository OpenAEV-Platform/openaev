import { queryOptions } from '@tanstack/react-query';

import { type Page } from '../../components/common/queryable/Page';
import { type EndpointOutput, type EndpointOverviewOutput, type SearchPaginationInput } from '../../utils/api-types';
import { ENTITY_TYPES } from '../../utils/query/entityChange';
import { liveMeta } from '../../utils/query/live';
import { getAssetOverview, searchAssets } from './endpoint-actions';

// Every parameter of a query is part of its key.
export const assetKeys = {
  all: ['assets'] as const,
  lists: () => [...assetKeys.all, 'list'] as const,
  list: (input: SearchPaginationInput) => [...assetKeys.lists(), input] as const,
  details: () => [...assetKeys.all, 'detail'] as const,
  detail: (assetId: string) => [...assetKeys.details(), assetId] as const,
};

export const assetQueries = {
  // One page of the inventory. Refetched when an asset it shows, or one of their agents, changes,
  // and when an asset is created or deleted. An asset updated into the filter from another page
  // shows at the next refetch. Every agent heartbeat (every 2 min) streams an update of the agent
  // and its endpoint: throttled so that a page of agent-backed rows is not refetched every second.
  list: (input: SearchPaginationInput) => queryOptions({
    queryKey: assetKeys.list(input),
    queryFn: ({ signal }) => searchAssets({
      input,
      signal,
    }),
    meta: liveMeta<Page<EndpointOutput>>({
      collections: [ENTITY_TYPES.ASSETS],
      throttleMs: 10_000,
      refs: page => page.content.flatMap(asset => [
        {
          type: ENTITY_TYPES.ASSETS,
          id: asset.asset_id,
        },
        ...(asset.asset_agents ?? []).map(agent => ({
          type: ENTITY_TYPES.AGENTS,
          id: agent.agent_id,
        })),
      ]),
    }),
  }),
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
