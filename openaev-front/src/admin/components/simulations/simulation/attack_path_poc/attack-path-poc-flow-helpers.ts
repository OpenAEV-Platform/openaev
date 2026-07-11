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

const PADDING = 40;
// Each node kind is a left-to-right band; within a band nodes wrap into a grid (MAX_ROWS tall) so a
// large simulation's hundreds of endpoints read as a compact block, not a single 40,000px column.
const COL_W = 300;
const ROW_H = 88;
const MAX_ROWS = 22;
const BAND_GAP = 160;

// Left-to-right order of the type bands.
const BAND_ORDER = [
  AP_FLOW_NODE_TYPE.injector,
  AP_FLOW_NODE_TYPE.asset,
  AP_FLOW_NODE_TYPE.findingType,
  AP_FLOW_NODE_TYPE.finding,
];

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
 * Map an AttackPathDTO onto React Flow nodes and edges. Nodes are laid out in grid bands by kind;
 * edges keep only those whose endpoints are both present (so an edge into a not-yet-expanded
 * finding level is dropped rather than dangling).
 */
export const buildAttackPathFlow = (
  dto: AttackPathDTO,
): {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
} => {
  const presentIds = new Set<string>();
  const byType: Record<string, AttackPathNodeDTO[]> = {};

  for (const n of dto.attackPathNodes ?? []) {
    const flowType = DTO_TYPE_TO_FLOW[n.type ?? ''];
    if (!flowType || !n.id) {
      continue;
    }
    (byType[flowType] ??= []).push(n);
    presentIds.add(n.id);
  }

  // Lay each kind out as its own band, left to right; within a band, wrap into a grid so a large
  // endpoint set is a compact block rather than one very tall column.
  const nodes: AttackPathFlowNode[] = [];
  let bandStartX = PADDING;
  for (const flowType of BAND_ORDER) {
    const list = byType[flowType] ?? [];
    if (list.length === 0) {
      continue;
    }
    const subColumns = Math.ceil(list.length / MAX_ROWS);
    list.forEach((n, i) => {
      nodes.push({
        id: n.id as string,
        type: flowType,
        position: {
          x: bandStartX + Math.floor(i / MAX_ROWS) * COL_W,
          y: PADDING + (i % MAX_ROWS) * ROW_H,
        },
        data: nodeData(n),
      });
    });
    bandStartX += subColumns * COL_W + BAND_GAP;
  }

  const edges = (dto.attackPathEdges ?? [])
    .filter(e => presentIds.has(e.edgeSourceId ?? '') && presentIds.has(e.edgeTargetId ?? ''))
    .map(toEdge);

  return {
    nodes,
    edges,
  };
};
