// -- Types matching backend DTOs --

export interface AttackPathExpectation {
  expectation_id: string;
  expectation_type: string;
  expectation_status: string;
  expectation_score: number | null;
  expectation_expected_score: number | null;
}

export interface AttackPathNode {
  node_id: string;
  node_type: 'ACTION' | 'ASSET';
  node_label: string;
  node_status?: string;
  node_hostname?: string;
  node_ip?: string;
  node_platform?: string;
  node_payload_name?: string;
  node_executed_at?: string;
  node_expectations?: AttackPathExpectation[];
}

export interface AttackPathEdge {
  edge_id: string;
  edge_source: string;
  edge_target: string;
  edge_type: 'chain_flow' | 'asset_link';
  edge_label?: string;
}

export interface AttackPathStats {
  stats_prevented: number;
  stats_detected: number;
  stats_undetected: number;
  stats_pending: number;
  stats_total_actions: number;
  stats_executed_actions: number;
}

export interface AttackPathData {
  attack_path_nodes: AttackPathNode[];
  attack_path_edges: AttackPathEdge[];
  attack_path_stats: AttackPathStats;
}

// -- Status resolution --

export type AttackStepStatus = 'prevented' | 'detected' | 'undetected' | 'pending';

export const STATUS_COLORS: Record<AttackStepStatus, { fill: string; stroke: string; bg: string }> = {
  prevented: { fill: '#4caf50', stroke: '#388e3c', bg: 'rgba(76, 175, 80, 0.12)' },
  detected: { fill: '#ff9800', stroke: '#f57c00', bg: 'rgba(255, 152, 0, 0.12)' },
  undetected: { fill: '#f44336', stroke: '#d32f2f', bg: 'rgba(244, 67, 54, 0.12)' },
  pending: { fill: '#9e9e9e', stroke: '#757575', bg: 'rgba(158, 158, 158, 0.12)' },
};

export function getNodeStatus(node: AttackPathNode): AttackStepStatus {
  return (node.node_status as AttackStepStatus) ?? 'pending';
}

// -- Graph layout --

interface Position {
  x: number;
  y: number;
}

export interface LayoutNode extends AttackPathNode {
  x: number;
  y: number;
  width: number;
  height: number;
}

const ACTION_W = 140;
const ACTION_H = 140;
const ASSET_W = 180;
const ASSET_H = 80;
const H_GAP = 260;
const V_GAP = 120;

export function computeLayout(
  nodes: AttackPathNode[],
  edges: AttackPathEdge[],
): LayoutNode[] {
  const actionNodes = nodes.filter((n) => n.node_type === 'ACTION');
  const assetNodes = nodes.filter((n) => n.node_type === 'ASSET');

  // Build adjacency for topological sort (chain_flow edges only)
  const chainEdges = edges.filter((e) => e.edge_type === 'chain_flow');
  const inDegree = new Map<string, number>();
  const adjList = new Map<string, string[]>();

  for (const node of actionNodes) {
    inDegree.set(node.node_id, 0);
    adjList.set(node.node_id, []);
  }
  for (const edge of chainEdges) {
    const targets = adjList.get(edge.edge_source) ?? [];
    targets.push(edge.edge_target);
    adjList.set(edge.edge_source, targets);
    inDegree.set(edge.edge_target, (inDegree.get(edge.edge_target) ?? 0) + 1);
  }

  // Topological sort → assign columns (left-to-right)
  const queue: string[] = [];
  for (const [id, deg] of inDegree) {
    if (deg === 0) queue.push(id);
  }

  const columns = new Map<string, number>();
  while (queue.length > 0) {
    const current = queue.shift()!;
    const col = columns.get(current) ?? 0;
    for (const next of adjList.get(current) ?? []) {
      columns.set(next, Math.max(columns.get(next) ?? 0, col + 1));
      inDegree.set(next, (inDegree.get(next) ?? 0) - 1);
      if (inDegree.get(next) === 0) queue.push(next);
    }
    if (!columns.has(current)) columns.set(current, 0);
  }

  // Group actions by column
  const columnGroups = new Map<number, string[]>();
  for (const node of actionNodes) {
    const col = columns.get(node.node_id) ?? 0;
    const group = columnGroups.get(col) ?? [];
    group.push(node.node_id);
    columnGroups.set(col, group);
  }

  // Assign positions to action nodes
  const positions = new Map<string, Position>();
  for (const [col, ids] of columnGroups) {
    ids.forEach((id, row) => {
      positions.set(id, {
        x: 100 + col * H_GAP,
        y: 100 + row * V_GAP,
      });
    });
  }

  // Position asset nodes below their connected actions
  const assetLinkEdges = edges.filter((e) => e.edge_type === 'asset_link');
  const assetPositions = new Map<string, Position>();

  for (const asset of assetNodes) {
    const connectedActions = assetLinkEdges
      .filter((e) => e.edge_target === asset.node_id)
      .map((e) => positions.get(e.edge_source))
      .filter(Boolean) as Position[];

    if (connectedActions.length > 0) {
      const avgX = connectedActions.reduce((sum, p) => sum + p.x, 0) / connectedActions.length;
      const maxY = Math.max(...connectedActions.map((p) => p.y));
      assetPositions.set(asset.node_id, { x: avgX, y: maxY + V_GAP + 40 });
    } else {
      assetPositions.set(asset.node_id, { x: 100, y: 100 });
    }
  }

  // Combine into LayoutNodes
  const result: LayoutNode[] = [];

  for (const node of actionNodes) {
    const pos = positions.get(node.node_id) ?? { x: 0, y: 0 };
    result.push({ ...node, x: pos.x, y: pos.y, width: ACTION_W, height: ACTION_H });
  }

  for (const node of assetNodes) {
    const pos = assetPositions.get(node.node_id) ?? { x: 0, y: 0 };
    result.push({ ...node, x: pos.x, y: pos.y, width: ASSET_W, height: ASSET_H });
  }

  return result;
}

// -- Path highlighting --

export function getUpstreamNodes(nodeId: string, edges: AttackPathEdge[]): Set<string> {
  const result = new Set<string>();
  const queue = [nodeId];
  while (queue.length > 0) {
    const current = queue.shift()!;
    for (const edge of edges) {
      if (edge.edge_target === current && !result.has(edge.edge_source)) {
        result.add(edge.edge_source);
        queue.push(edge.edge_source);
      }
    }
  }
  return result;
}

export function getDownstreamNodes(nodeId: string, edges: AttackPathEdge[]): Set<string> {
  const result = new Set<string>();
  const queue = [nodeId];
  while (queue.length > 0) {
    const current = queue.shift()!;
    for (const edge of edges) {
      if (edge.edge_source === current && !result.has(edge.edge_target)) {
        result.add(edge.edge_target);
        queue.push(edge.edge_target);
      }
    }
  }
  return result;
}

export function getConnectedNodes(nodeId: string, edges: AttackPathEdge[]): Set<string> {
  const upstream = getUpstreamNodes(nodeId, edges);
  const downstream = getDownstreamNodes(nodeId, edges);
  return new Set([nodeId, ...upstream, ...downstream]);
}
