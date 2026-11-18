// Types of the exploration graph API (#9388), mirroring io.openaev.api.graph.dto.

import { type CounterpartKindPath } from '../../../actions/xtm/counterpart-actions';

export type GraphNodeType = 'ASSET' | 'ASSET_GROUP' | 'FINDING' | 'INJECT' | 'SIMULATION' | 'SCENARIO';

export type GraphEdgeType = 'MEMBER_OF' | 'FOUND_ON' | 'PRODUCED' | 'TARGETS' | 'PART_OF' | 'RUN_OF';

export type GraphResult = 'SUCCESS' | 'PENDING' | 'FAILED';

export interface GraphNodeOutput {
  node_id: string;
  node_type: GraphNodeType;
  node_entity_id: string;
  node_label: string;
  node_subtype?: string | null;
  node_result?: GraphResult | null;
  node_failed_count: number;
  node_finding_count: number;
}

export interface GraphEdgeOutput {
  edge_id: string;
  edge_type: GraphEdgeType;
  edge_source: string;
  edge_target: string;
  edge_result?: GraphResult | null;
}

export interface GraphOutput {
  graph_focus?: string | null;
  graph_nodes: GraphNodeOutput[];
  graph_edges: GraphEdgeOutput[];
  graph_truncated: boolean;
  graph_path: string[];
}

export const ALL_NODE_TYPES: GraphNodeType[] = ['ASSET', 'ASSET_GROUP', 'FINDING', 'INJECT', 'SIMULATION', 'SCENARIO'];

export const ALL_EDGE_TYPES: GraphEdgeType[] = ['MEMBER_OF', 'FOUND_ON', 'PRODUCED', 'TARGETS', 'PART_OF', 'RUN_OF'];

export const ALL_RESULTS: GraphResult[] = ['FAILED', 'PENDING', 'SUCCESS'];

/** Catalog keys of the node types, singular. */
export const NODE_TYPE_LABEL: Record<GraphNodeType, string> = {
  ASSET: 'Asset',
  ASSET_GROUP: 'Asset group',
  FINDING: 'Finding',
  INJECT: 'Inject',
  SIMULATION: 'Simulation',
  SCENARIO: 'Scenario',
};

/** Catalog keys of the relation types. */
export const EDGE_TYPE_LABEL: Record<GraphEdgeType, string> = {
  MEMBER_OF: 'Member of',
  FOUND_ON: 'Found on',
  PRODUCED: 'Produced',
  TARGETS: 'Targets',
  PART_OF: 'Part of',
  RUN_OF: 'Run of',
};

export const RESULT_LABEL: Record<GraphResult, string> = {
  SUCCESS: 'Success',
  PENDING: 'Pending',
  FAILED: 'Failed',
};

/** Path segment of the API and of the shareable URL for a node type. */
export const nodeTypeParam = (type: GraphNodeType) => type.toLowerCase();

export const parseNodeType = (value: string | null | undefined): GraphNodeType | undefined => {
  if (!value) {
    return undefined;
  }
  const upper = value.toUpperCase() as GraphNodeType;
  return ALL_NODE_TYPES.includes(upper) ? upper : undefined;
};

/** The platform screen a node opens, for the detail panel's deep link. */
export const nodeHref = (node: Pick<GraphNodeOutput, 'node_type' | 'node_entity_id'>): string | undefined => {
  const id = node.node_entity_id;
  switch (node.node_type) {
    case 'ASSET':
      return `/admin/assets/${id}`;
    case 'ASSET_GROUP':
      return `/admin/asset_groups/${id}`;
    case 'FINDING':
      return `/admin/findings/${id}`;
    case 'SIMULATION':
      return `/admin/simulations/${id}`;
    case 'SCENARIO':
      return `/admin/scenarios/${id}`;
    default:
      return undefined;
  }
};

interface NodeCounterpartKey {
  kind: CounterpartKindPath;
  key: string;
}

/**
 * The key a node is matched on in a connected product (OpenCTI), or null when the graph does not
 * carry a real identifier for it. A CVE finding is matched on its CVE id (its label, the finding
 * value; its subtype is the finding type, the enum name CVE). Assets are not: the graph carries
 * their name, not their hostname or addresses, so their pivot stays on the asset screen.
 */
export const nodeCounterpart = (
  node: Pick<GraphNodeOutput, 'node_type' | 'node_subtype' | 'node_label'>,
): NodeCounterpartKey | null => {
  if (node.node_type === 'FINDING' && node.node_subtype?.toLowerCase() === 'cve' && node.node_label) {
    return {
      kind: 'vulnerabilities',
      key: node.node_label,
    };
  }
  return null;
};

/** The graph page focused on one entity: the target of every "Explore in graph" button. */
export const graphHref = (type: GraphNodeType, entityId: string) => `/admin/graph?focus=${nodeTypeParam(type)}:${encodeURIComponent(entityId)}`;
