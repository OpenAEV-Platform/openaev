import { type GraphEdgeType, type GraphNodeType, type GraphOutput, nodeTypeParam } from '../../admin/components/graph/graph-model';
import { simpleCall } from '../../utils/Action';

const GRAPH_URI = '/api/graph';

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
