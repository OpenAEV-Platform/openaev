import { type GraphEdgeType, type GraphNodeType, type GraphOutput, nodeTypeParam } from '../../admin/components/graph/graph-model';
import { simpleCall } from '../../utils/Action';
import { type GraphOverviewOutput, type GraphStartingPointsOutput } from '../../utils/api-types';

const GRAPH_URI = '/api/graph';

/** The landing story: one connected subgraph of about 30 nodes, empty when nothing failed. */
export const fetchGraphStory = () => simpleCall(`${GRAPH_URI}/story`)
  .then(({ data }) => data as GraphOutput);

/** The estate overview: asset groups as counted bubbles, whatever the estate size. */
export const fetchGraphOverview = () => simpleCall(`${GRAPH_URI}/overview`)
  .then(({ data }) => data as GraphOverviewOutput);

/** The starting-points rail: top entities to recentre the graph on. */
export const fetchGraphStartingPoints = () => simpleCall(`${GRAPH_URI}/starting-points`)
  .then(({ data }) => data as GraphStartingPointsOutput);

export const fetchGraphHotPaths = (limit = 15) => simpleCall(`${GRAPH_URI}/hot-paths`, { params: { limit } })
  .then(({ data }) => data as GraphOutput);

export const fetchGraphNeighbourhood = (type: GraphNodeType, entityId: string, edges: GraphEdgeType[] = [], limit = 50) => simpleCall(
  `${GRAPH_URI}/${nodeTypeParam(type)}/${encodeURIComponent(entityId)}/neighbourhood`,
  {
    params: {
      limit,
      edges: edges.length > 0 ? edges.join(',') : undefined,
    },
  },
).then(({ data }) => data as GraphOutput);

export const fetchGraphPath = (fromType: GraphNodeType, fromId: string, toType: GraphNodeType, toId: string) => simpleCall(
  `${GRAPH_URI}/path`,
  {
    params: {
      fromType: nodeTypeParam(fromType),
      fromId,
      toType: nodeTypeParam(toType),
      toId,
    },
  },
).then(({ data }) => data as GraphOutput);
