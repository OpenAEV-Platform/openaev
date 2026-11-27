// Types of the exploration graph API, mirroring io.openaev.api.graph.dto.

import { type CounterpartKindPath } from '../../../actions/xtm/counterpart-actions';

export type GraphNodeType = 'ASSET' | 'ASSET_GROUP' | 'FINDING' | 'INJECT' | 'SIMULATION' | 'SCENARIO' | 'PERSON' | 'ORGANIZATION' | 'TEAM';

export type GraphEdgeType = 'MEMBER_OF' | 'FOUND_ON' | 'PRODUCED' | 'TARGETS' | 'PART_OF' | 'RUN_OF'
  | 'IDENTITY_OF' | 'WORKS_FOR' | 'IN_TEAM' | 'TEAM_OF' | 'LINKED_TO' | 'OWNED_BY' | 'MANAGED_BY' | 'ATTACK_STEP';

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
  /** When the entity happened (a simulation's start, a finding's last update), else absent: the time range filters on it. */
  node_date?: string | null;
  /** The entity's relations in the data per relation type, drawn or not; absent when not counted. */
  node_relation_counts?: Partial<Record<GraphEdgeType, number>> | null;
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

export const ALL_NODE_TYPES: GraphNodeType[] = ['ASSET', 'ASSET_GROUP', 'FINDING', 'INJECT', 'SIMULATION', 'SCENARIO', 'PERSON', 'ORGANIZATION', 'TEAM'];

export const ALL_EDGE_TYPES: GraphEdgeType[] = [
  'MEMBER_OF', 'FOUND_ON', 'PRODUCED', 'TARGETS', 'PART_OF', 'RUN_OF',
  'IDENTITY_OF', 'WORKS_FOR', 'IN_TEAM', 'TEAM_OF', 'LINKED_TO', 'OWNED_BY', 'MANAGED_BY', 'ATTACK_STEP',
];

/**
 * The relations each node type can have, as GraphExplorationService resolves them: the panel and the
 * context menu offer only these (OpenCTI offers only the relations an entity can hold).
 */
export const EDGE_TYPES_BY_NODE_TYPE: Record<GraphNodeType, GraphEdgeType[]> = {
  ASSET: ['MEMBER_OF', 'IDENTITY_OF', 'LINKED_TO', 'OWNED_BY', 'MANAGED_BY', 'ATTACK_STEP', 'FOUND_ON', 'TARGETS'],
  ASSET_GROUP: ['MEMBER_OF', 'TARGETS'],
  FINDING: ['FOUND_ON', 'PRODUCED'],
  INJECT: ['TARGETS', 'PRODUCED', 'PART_OF'],
  SIMULATION: ['PART_OF', 'RUN_OF', 'ATTACK_STEP'],
  SCENARIO: ['RUN_OF'],
  PERSON: ['IDENTITY_OF', 'WORKS_FOR', 'IN_TEAM', 'OWNED_BY', 'MANAGED_BY'],
  ORGANIZATION: ['WORKS_FOR', 'TEAM_OF'],
  TEAM: ['IN_TEAM', 'TEAM_OF', 'OWNED_BY', 'MANAGED_BY'],
};

export const ALL_RESULTS: GraphResult[] = ['FAILED', 'PENDING', 'SUCCESS'];

/** Catalog keys of the node types, singular. */
export const NODE_TYPE_LABEL: Record<GraphNodeType, string> = {
  ASSET: 'Asset',
  ASSET_GROUP: 'Asset group',
  FINDING: 'Finding',
  INJECT: 'Inject',
  SIMULATION: 'Simulation',
  SCENARIO: 'Scenario',
  PERSON: 'Person',
  ORGANIZATION: 'Organization',
  TEAM: 'Team',
};

/** Catalog keys of the node types, plural: the label of a group node ("+14 Injects"). */
export const NODE_TYPE_PLURAL: Record<GraphNodeType, string> = {
  ASSET: 'Assets',
  ASSET_GROUP: 'Asset groups',
  FINDING: 'Findings',
  INJECT: 'Injects',
  SIMULATION: 'Simulations',
  SCENARIO: 'Scenarios',
  PERSON: 'Persons',
  ORGANIZATION: 'Organizations',
  TEAM: 'Teams',
};

/** Catalog keys of the relation types. */
export const EDGE_TYPE_LABEL: Record<GraphEdgeType, string> = {
  MEMBER_OF: 'Member of',
  FOUND_ON: 'Found on',
  PRODUCED: 'Produced',
  TARGETS: 'Targets',
  PART_OF: 'Part of',
  RUN_OF: 'Run of',
  IDENTITY_OF: 'Identity of',
  WORKS_FOR: 'Works for',
  IN_TEAM: 'In team',
  TEAM_OF: 'Team of',
  LINKED_TO: 'Linked to',
  OWNED_BY: 'Owned by',
  MANAGED_BY: 'Managed by',
  ATTACK_STEP: 'Attack step',
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
    case 'PERSON':
      return `/admin/persons/${id}`;
    case 'ORGANIZATION':
      return `/admin/organizations/${id}`;
    case 'TEAM':
      return `/admin/teams/${id}`;
    default:
      return undefined;
  }
};

export interface NodeCounterpartKey {
  kind: CounterpartKindPath;
  key: string;
}

/**
 * The key a node is matched on in a connected product (OpenCTI, OpenCRQ), or null when the graph
 * does not carry a real identifier for it. A CVE finding is matched on its CVE id (its label, the
 * finding value; its subtype is the finding type, the enum name CVE), the key the list rows and the
 * finding hero use. Assets are not: the graph carries their name, not their hostname or addresses,
 * so their pivot stays on the asset screen.
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
