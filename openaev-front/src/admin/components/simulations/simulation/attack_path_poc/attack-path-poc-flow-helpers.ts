import { type Edge, type Node } from '@xyflow/react';

import type { AttackPathDTO, AttackPathEdges, AttackPathNodeDTO } from '../../../../../utils/api-types';

// Attack-path execution-store POC (issue 6647). Pure mapping of the backend AttackPathDTO onto
// React Flow nodes and edges, with a manual column layout (no layout lib, mirroring AttackPath.tsx).
// Executions are carried on the edges (design O2), never as flow nodes, so the graph stays a handful
// of node kinds regardless of how many executions a simulation ran.

export const AP_FLOW_NODE_TYPE = {
  injector: 'apInjector',
  asset: 'apAsset',
  findingType: 'apFindingType',
  finding: 'apFinding',
} as const;

export const AP_FLOW_EDGE_TYPE = 'apGrouped';

// DTO node type -> React Flow node-type key. EXECUTION is intentionally absent: the feed lists
// executions, they are not nodes on the map.
const DTO_TYPE_TO_FLOW: Record<string, string> = {
  INJECTOR: AP_FLOW_NODE_TYPE.injector,
  ASSET: AP_FLOW_NODE_TYPE.asset,
  FINDING_TYPE: AP_FLOW_NODE_TYPE.findingType,
  FINDING: AP_FLOW_NODE_TYPE.finding,
};

// Left-to-right column per node kind: injector -> endpoint -> finding type -> finding.
const COLUMN: Record<string, number> = {
  [AP_FLOW_NODE_TYPE.injector]: 0,
  [AP_FLOW_NODE_TYPE.asset]: 1,
  [AP_FLOW_NODE_TYPE.findingType]: 2,
  [AP_FLOW_NODE_TYPE.finding]: 3,
};

const PADDING = 40;
const X_GAP = 340;
const Y_GAP = 90;

export interface AttackPathFlowNodeData {
  label?: string;
  status?: string;
  ref?: string;
  typeFindings?: string;
  findingCounts?: Record<string, number>;
  hostname?: string;
  ip?: string;
  platform?: string;
  agents?: string[];
  [key: string]: unknown;
}

export interface AttackPathFlowEdgeData {
  count: number;
  edgeType?: string;
  [key: string]: unknown;
}

export type AttackPathFlowNode = Node<AttackPathFlowNodeData>;
export type AttackPathFlowEdge = Edge<AttackPathFlowEdgeData>;

const nodeData = (n: AttackPathNodeDTO): AttackPathFlowNodeData => ({
  label: n.label,
  status: n.status,
  ref: n.ref,
  typeFindings: n.typeFindings,
  findingCounts: n.findingCounts,
  hostname: n.hostname,
  ip: n.ip,
  platform: n.platform,
  agents: n.agents,
});

const toEdge = (e: AttackPathEdges): AttackPathFlowEdge => ({
  id: e.edgeId ?? `${e.edgeSourceId}-${e.edgeTargetId}`,
  source: e.edgeSourceId ?? '',
  target: e.edgeTargetId ?? '',
  type: AP_FLOW_EDGE_TYPE,
  data: {
    count: e.count ?? 1,
    edgeType: e.type,
  },
});

/**
 * Map an AttackPathDTO onto React Flow nodes and edges. Nodes are laid out in columns by kind;
 * edges keep only those whose endpoints are both present (so an edge into a not-yet-expanded
 * finding level is dropped rather than dangling).
 */
export const buildAttackPathFlow = (
  dto: AttackPathDTO,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const rowByColumn: Record<string, number> = {};
  const presentIds = new Set<string>();
  const nodes: AttackPathFlowNode[] = [];

  for (const n of dto.attackPathNodes ?? []) {
    const flowType = DTO_TYPE_TO_FLOW[n.type ?? ''];
    if (!flowType || !n.id) {
      continue;
    }
    const column = COLUMN[flowType];
    const row = rowByColumn[flowType] ?? 0;
    rowByColumn[flowType] = row + 1;
    presentIds.add(n.id);
    nodes.push({
      id: n.id,
      type: flowType,
      position: {
        x: PADDING + column * X_GAP,
        y: PADDING + row * Y_GAP,
      },
      data: nodeData(n),
    });
  }

  const edges = (dto.attackPathEdges ?? [])
    .filter(e => presentIds.has(e.edgeSourceId ?? '') && presentIds.has(e.edgeTargetId ?? ''))
    .map(toEdge);

  return {
    nodes,
    edges,
  };
};
