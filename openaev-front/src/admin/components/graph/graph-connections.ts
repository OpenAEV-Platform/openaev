// Connection counts of a node (#9572): what the data holds against what the screen draws, so each
// node tells, OpenCTI style, how much an expansion would add.

import { EDGE_TYPES_BY_NODE_TYPE, type GraphEdgeOutput, type GraphEdgeType, type GraphNodeOutput } from './graph-model';

export interface RelationConnections {
  type: GraphEdgeType;
  /** Relations of this type in the data (never below what is drawn). */
  total: number;
  /** Relations of this type drawn on screen. */
  shown: number;
}

export interface NodeConnections {
  total: number;
  shown: number;
  /** Relations in the data not drawn yet: what an expansion would add. */
  hidden: number;
  byType: RelationConnections[];
}

/**
 * The edges drawn per node and per relation type, in one pass over the edges of the view (never
 * one scan per node).
 */
export const drawnRelations = (edges: GraphEdgeOutput[]): Map<string, Map<GraphEdgeType, number>> => {
  const drawn = new Map<string, Map<GraphEdgeType, number>>();
  const add = (nodeId: string, type: GraphEdgeType) => {
    const perType = drawn.get(nodeId) ?? new Map<GraphEdgeType, number>();
    perType.set(type, (perType.get(type) ?? 0) + 1);
    drawn.set(nodeId, perType);
  };
  edges.forEach((e) => {
    add(e.edge_source, e.edge_type);
    if (e.edge_target !== e.edge_source) {
      add(e.edge_target, e.edge_type);
    }
  });
  return drawn;
};

/**
 * A node's connections, or null when the API did not count it. A relation type the data holds
 * fewer of than the screen draws (a dynamic group's members, resolved on expansion only) counts
 * what is drawn, so "not shown" is never negative.
 */
export const nodeConnections = (
  node: Pick<GraphNodeOutput, 'node_id' | 'node_type' | 'node_relation_counts'>,
  drawn: Map<string, Map<GraphEdgeType, number>>,
): NodeConnections | null => {
  const counts = node.node_relation_counts;
  if (!counts) {
    return null;
  }
  const drawnHere = drawn.get(node.node_id);
  const byType = EDGE_TYPES_BY_NODE_TYPE[node.node_type].map((type) => {
    const shown = drawnHere?.get(type) ?? 0;
    return {
      type,
      shown,
      total: Math.max(counts[type] ?? 0, shown),
    };
  });
  const total = byType.reduce((sum, r) => sum + r.total, 0);
  const shown = byType.reduce((sum, r) => sum + r.shown, 0);
  return {
    total,
    shown,
    hidden: total - shown,
    byType,
  };
};
