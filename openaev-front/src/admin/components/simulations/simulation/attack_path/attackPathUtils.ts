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
  node_command?: string;       // the actual command executed
  node_arguments?: string;     // input arguments/parameters
  node_executed_at?: string;
  node_expectations?: AttackPathExpectation[];
  // Extended fields for endpoint view & result dialog
  node_user_privileges?: string;
  node_accessed_files?: string[];
  node_credentials_found?: string[];
  node_inject_id?: string;
  node_asset_id?: string;
  // Chaining context
  node_chain_previous?: string;
  node_chain_next?: string;
  // Subnet/zone grouping fields for Variant-2 subnet layout
  node_zone?: string;            // "Finance LAN", "DMZ", "Corp LAN", etc.
  node_subnet?: string;          // "192.168.10.0/24"
  node_is_entry_point?: boolean; // true for the first compromised machine
  node_is_pivot?: boolean;       // true if machine was used to reach another zone
  node_untouched?: boolean;      // true if discovered but never attacked
  // Terminal output from execution
  node_terminal_output?: string;
  // Discovered artifacts
  node_ports_found?: string[];    // e.g. ['22/tcp open ssh OpenSSH 8.9p1', '80/tcp open http nginx 1.24']
  node_users_found?: string[];    // e.g. ['CORP\\Administrator', 'CORP\\jsmith']
  node_cves_found?: string[];     // e.g. ['CVE-2021-44228 (Log4Shell) - CRITICAL']
  // Agent information (Variant-7+)
  /** The specific agent that ran this ACTION node */
  node_agent?: AgentType;
  /** All agents installed on this ASSET node */
  node_agents?: AgentType[];
}

export interface AttackPathEdge {
  edge_id: string;
  edge_source: string;
  edge_target: string;
  edge_type: 'chain_flow' | 'asset_link' | 'compromise' | 'lateral_movement' | 'discovery' | 'pivot';
  edge_label?: string;
}

export interface AttackPathStats {
  stats_prevented: number;
  stats_detected: number;
  stats_undetected: number;
  stats_pending: number;
  stats_total_actions: number;
  stats_executed_actions: number;
  // Captured assets
  stats_captured_endpoints: number;
  stats_captured_files: number;
  stats_captured_credentials: number;
  stats_captured_users?: number;
  stats_captured_cves?: number;
}

export interface AttackPathDefinition {
  path_id: string;
  path_name: string;
  path_color: string;
  node_ids: string[]; // ordered sequence of ASSET node IDs
  /** 'success' = reached objective | 'failed' = blocked/contained before goal | 'partial' = partial progress */
  path_outcome?: 'success' | 'failed' | 'partial';
  /** Optional label for failed reason, e.g. "PREVENTED by EDR", "Contained by SOC" */
  path_fail_reason?: string;
  /**
   * V5: node_id from which the path segment failed.
   * Everything AFTER this node (exclusive) is rendered as dashed/gray.
   * If omitted for a 'failed' path, the entire path is considered failed.
   */
  failed_from_node_id?: string;
  /**
   * V1: per-segment reason labels explaining WHY the attack moved from one endpoint to the next.
   * Key format: "sourceNodeId->targetNodeId"
   * Value: human-readable reason, e.g. "Credentials Harvested", "Port 445 (SMB)", "Kerberoasting"
   */
  path_segment_reasons?: Record<string, string>;
  /**
   * V1: per-segment event condition details shown on badge hover.
   * Key format: "sourceNodeId->targetNodeId"
   */
  path_segment_details?: Record<string, {
    trigger_event?: string;   // e.g. "SSH Connection Established"
    condition?: string;       // e.g. "Port 22 open AND valid SSH credentials found"
    action?: string;          // e.g. "SSH Lateral Movement"
    tactic?: string;          // e.g. "Lateral Movement"
    technique?: string;       // e.g. "T1021.004 – Remote Services: SSH"
  }>;
}

export interface AttackPathData {
  attack_path_nodes: AttackPathNode[];
  attack_path_edges: AttackPathEdge[];
  attack_path_stats: AttackPathStats;
  attack_path_definitions?: AttackPathDefinition[];
}

// -- Status resolution --

export type AttackStepStatus = 'prevented' | 'detected' | 'undetected' | 'pending';

/** Human-readable display label for a status value. 'undetected' is shown as 'Unprevented'. */
export function statusDisplayLabel(status: AttackStepStatus | string): string {
  if (status === 'undetected') return 'Unprevented';
  return status.charAt(0).toUpperCase() + status.slice(1);
}

export const STATUS_COLORS: Record<AttackStepStatus, { fill: string; stroke: string; bg: string }> = {
  prevented: { fill: '#4caf50', stroke: '#388e3c', bg: 'rgba(76, 175, 80, 0.12)' },
  detected: { fill: '#ff9800', stroke: '#f57c00', bg: 'rgba(255, 152, 0, 0.12)' },
  undetected: { fill: '#f44336', stroke: '#d32f2f', bg: 'rgba(244, 67, 54, 0.12)' },
  pending: { fill: '#9e9e9e', stroke: '#757575', bg: 'rgba(158, 158, 158, 0.12)' },
};

// -- Agent types (Variant-7+) --

export type AgentType = 'palo_alto' | 'sentinel_one' | 'openaev';

export const AGENT_META: Record<AgentType, { name: string; abbr: string; color: string; bg: string }> = {
  palo_alto:    { name: 'Palo Alto Agent',   abbr: 'PA', color: '#fa6400', bg: 'rgba(250,100,0,0.15)' },
  sentinel_one: { name: 'SentinelOne Agent', abbr: 'S1', color: '#7c3aed', bg: 'rgba(124,58,237,0.15)' },
  openaev:      { name: 'OpenAEV Agent',     abbr: 'OA', color: '#14b8a6', bg: 'rgba(20,184,166,0.15)' },
};

export function getNodeStatus(node: AttackPathNode): AttackStepStatus {
  return (node.node_status as AttackStepStatus) ?? 'pending';
}

/**
 * Returns the semantic color for an attack path line based on outcome.
 *
 * Color logic (defender's perspective):
 *  - 'success'  → RED family   — attack fully succeeded (not prevented, not detected)
 *  - 'partial'  → ORANGE family — attack detected but not fully prevented
 *  - 'failed'   → GREEN family  — attack was prevented
 *
 * When multiple paths share the same outcome, use `index` to pick a distinct shade.
 */
const PATH_OUTCOME_PALETTE: Record<string, string[]> = {
  success: ['#f44336', '#c62828', '#e53935', '#ff5252'],
  partial: ['#ff9800', '#f57c00', '#fb8c00', '#ffa726'],
  failed:  ['#4caf50', '#388e3c', '#43a047', '#66bb6a'],
};

export function getPathOutcomeColor(outcome: string, index: number = 0): string {
  const palette = PATH_OUTCOME_PALETTE[outcome] ?? PATH_OUTCOME_PALETTE.success;
  return palette[index % palette.length];
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

// -- Endpoint-centric layout (Variant-2) --
// Each endpoint (ASSET node) is a circle. Compromise edges link them left-to-right.

const ENDPOINT_SIZE = 120; // bounding box for circles
const ENDPOINT_H_GAP = 240;
const ENDPOINT_V_GAP = 160;

export function computeEndpointLayout(
  nodes: AttackPathNode[],
  edges: AttackPathEdge[],
): LayoutNode[] {
  // In endpoint view, we only show ASSET nodes (or all nodes if no ASSET nodes)
  const endpointNodes = nodes.filter((n) => n.node_type === 'ASSET');
  const renderNodes = endpointNodes.length > 0 ? endpointNodes : nodes;

  // Build adjacency using compromise/chain_flow edges (or asset_link edges as fallback)
  const relevantEdges = edges.filter(
    (e) => e.edge_type === 'compromise' || e.edge_type === 'chain_flow',
  );

  const inDegree = new Map<string, number>();
  const adjList = new Map<string, string[]>();

  for (const node of renderNodes) {
    inDegree.set(node.node_id, 0);
    adjList.set(node.node_id, []);
  }
  for (const edge of relevantEdges) {
    if (!adjList.has(edge.edge_source) || !adjList.has(edge.edge_target)) continue;
    adjList.get(edge.edge_source)!.push(edge.edge_target);
    inDegree.set(edge.edge_target, (inDegree.get(edge.edge_target) ?? 0) + 1);
  }

  // Topological sort → columns
  const queue: string[] = [];
  for (const [id, deg] of inDegree) {
    if (deg === 0) queue.push(id);
  }

  const columns = new Map<string, number>();
  while (queue.length > 0) {
    const current = queue.shift()!;
    if (!columns.has(current)) columns.set(current, 0);
    const col = columns.get(current)!;
    for (const next of adjList.get(current) ?? []) {
      columns.set(next, Math.max(columns.get(next) ?? 0, col + 1));
      inDegree.set(next, (inDegree.get(next) ?? 0) - 1);
      if (inDegree.get(next) === 0) queue.push(next);
    }
  }

  // Group by column
  const columnGroups = new Map<number, string[]>();
  for (const node of renderNodes) {
    const col = columns.get(node.node_id) ?? 0;
    const group = columnGroups.get(col) ?? [];
    group.push(node.node_id);
    columnGroups.set(col, group);
  }

  const positions = new Map<string, Position>();
  for (const [col, ids] of columnGroups) {
    ids.forEach((id, row) => {
      positions.set(id, {
        x: 100 + col * ENDPOINT_H_GAP,
        y: 100 + row * ENDPOINT_V_GAP,
      });
    });
  }

  return renderNodes.map((node) => {
    const pos = positions.get(node.node_id) ?? { x: 0, y: 0 };
    return { ...node, x: pos.x, y: pos.y, width: ENDPOINT_SIZE, height: ENDPOINT_SIZE };
  });
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

// -- Variant-2 helpers: derive endpoint-to-endpoint edges from action chain data --

/**
 * Derives compromise edges between ASSET nodes by inspecting the action chain.
 * If action A1 → action A2 (chain_flow), and A1 targets asset X (asset_link) while
 * A2 targets asset Y (asset_link), and X ≠ Y → that's a lateral movement X → Y.
 */
export function deriveEndpointEdges(
  edges: AttackPathEdge[],
): AttackPathEdge[] {
  // Build: action_id → asset_id
  const actionToAsset = new Map<string, string>();
  for (const edge of edges) {
    if (edge.edge_type === 'asset_link') {
      actionToAsset.set(edge.edge_source, edge.edge_target);
    }
  }

  const derived: AttackPathEdge[] = [];
  const seen = new Set<string>();

  for (const edge of edges) {
    if (edge.edge_type !== 'chain_flow') continue;
    const sourceAsset = actionToAsset.get(edge.edge_source);
    const targetAsset = actionToAsset.get(edge.edge_target);

    if (sourceAsset && targetAsset && sourceAsset !== targetAsset) {
      const key = `${sourceAsset}→${targetAsset}`;
      if (!seen.has(key)) {
        seen.add(key);
        derived.push({
          edge_id: `ep-${sourceAsset}-${targetAsset}`,
          edge_source: sourceAsset,
          edge_target: targetAsset,
          edge_type: 'compromise',
          edge_label: 'lateral movement',
        });
      }
    }
  }

  return derived;
}

/**
 * Enriches ASSET nodes with information gathered from their linked ACTION nodes:
 * worst status, IP/hostname/platform, user privileges, accessed files, credentials.
 */
export function enrichEndpointNodes(
  nodes: AttackPathNode[],
  edges: AttackPathEdge[],
): AttackPathNode[] {
  const assetLinkEdges = edges.filter((e) => e.edge_type === 'asset_link');

  // asset_id → list of action_ids
  const assetToActions = new Map<string, string[]>();
  for (const edge of assetLinkEdges) {
    const existing = assetToActions.get(edge.edge_target) ?? [];
    existing.push(edge.edge_source);
    assetToActions.set(edge.edge_target, existing);
  }

  const actionMap = new Map<string, AttackPathNode>(
    nodes.filter((n) => n.node_type === 'ACTION').map((n) => [n.node_id, n]),
  );

  const statusPriority: AttackStepStatus[] = ['undetected', 'detected', 'prevented', 'pending'];

  return nodes.map((node) => {
    if (node.node_type !== 'ASSET') return node;

    const actionIds = assetToActions.get(node.node_id) ?? [];
    const actions = actionIds.map((id) => actionMap.get(id)).filter(Boolean) as AttackPathNode[];

    if (actions.length === 0) return node;

    const statuses = actions.map((a) => getNodeStatus(a));
    const worstStatus = statusPriority.find((s) => statuses.includes(s)) ?? 'pending';

    const allFiles = [...new Set(actions.flatMap((a) => a.node_accessed_files ?? []))];
    const allCreds = [...new Set(actions.flatMap((a) => a.node_credentials_found ?? []))];

    // Ports: dedup by port number (keep most descriptive string per port)
    const portMap = new Map<string, string>();
    for (const p of actions.flatMap((a) => a.node_ports_found ?? [])) {
      const portNum = p.match(/^(\d+\/(?:tcp|udp))/i)?.[1] ?? p.split(' ')[0];
      const existing = portMap.get(portNum);
      if (!existing || p.length > existing.length) portMap.set(portNum, p);
    }
    const allPorts = [...portMap.values()];

    // Users: exact dedup
    const allUsers = [...new Set(actions.flatMap((a) => a.node_users_found ?? []))];

    // CVEs: dedup by CVE ID (keep most descriptive string per CVE)
    const cveIdMap = new Map<string, string>();
    for (const c of actions.flatMap((a) => a.node_cves_found ?? [])) {
      const cveId = c.match(/CVE-\d{4}-\d+/i)?.[0]?.toUpperCase() ?? c;
      const existing = cveIdMap.get(cveId);
      if (!existing || c.length > existing.length) cveIdMap.set(cveId, c);
    }
    const allCves = [...cveIdMap.values()];

    const withIp = actions.find((a) => a.node_ip);
    const withHostname = actions.find((a) => a.node_hostname);
    const withPlatform = actions.find((a) => a.node_platform);
    const withPrivs = actions.find((a) => a.node_user_privileges);

    return {
      ...node,
      node_status: worstStatus,
      node_ip: node.node_ip ?? withIp?.node_ip,
      node_hostname: node.node_hostname ?? withHostname?.node_hostname,
      node_platform: node.node_platform ?? withPlatform?.node_platform,
      node_user_privileges: node.node_user_privileges ?? withPrivs?.node_user_privileges,
      node_accessed_files: allFiles.length > 0 ? allFiles : node.node_accessed_files,
      node_credentials_found: allCreds.length > 0 ? allCreds : node.node_credentials_found,
      node_ports_found: allPorts.length > 0 ? allPorts : node.node_ports_found,
      node_users_found: allUsers.length > 0 ? allUsers : node.node_users_found,
      node_cves_found: allCves.length > 0 ? allCves : node.node_cves_found,
    };
  });
}

/**
 * Computes accurate finding counts directly from the (enriched) node list.
 * Use this to override hardcoded stats in mock/backend data.
 */
export function computeStatsFromNodes(
  enriched: AttackPathNode[],
  baseStats: AttackPathStats,
): AttackPathStats {
  const assets = enriched.filter((n) => n.node_type === 'ASSET');

  // Files: total unique files across all endpoints
  const allFiles = new Set<string>();
  for (const a of assets) for (const f of a.node_accessed_files ?? []) allFiles.add(f);

  // Credentials: total unique credentials across all endpoints
  const allCreds = new Set<string>();
  for (const a of assets) for (const c of a.node_credentials_found ?? []) allCreds.add(c);

  // Users: total unique users across all endpoints
  const allUsers = new Set<string>();
  for (const a of assets) for (const u of a.node_users_found ?? []) allUsers.add(u);

  // CVEs: dedup by CVE ID across all endpoints
  const allCveIds = new Set<string>();
  for (const a of assets) {
    for (const c of a.node_cves_found ?? []) {
      const id = c.match(/CVE-\d{4}-\d+/i)?.[0]?.toUpperCase() ?? c;
      allCveIds.add(id);
    }
  }

  return {
    ...baseStats,
    stats_captured_endpoints: assets.length,
    stats_captured_files: allFiles.size,
    stats_captured_credentials: allCreds.size,
    stats_captured_users: allUsers.size,
    stats_captured_cves: allCveIds.size,
  };
}

/**
 * Returns action node IDs linked to the given asset via asset_link edges.
 */
function getActionsForAsset(assetId: string, edges: AttackPathEdge[]): string[] {
  return edges
    .filter((e) => e.edge_type === 'asset_link' && e.edge_target === assetId)
    .map((e) => e.edge_source);
}

/**
 * Extended version: finds all ACTION nodes that ran on a given ASSET node.
 * Uses asset_link edges first, then falls back to hostname/IP matching.
 * Returns deduplicated action node IDs.
 */
export function getActionsForAssetFull(
  assetNode: AttackPathNode,
  allNodes: AttackPathNode[],
  edges: AttackPathEdge[],
): AttackPathNode[] {
  // 1. via asset_link edges
  const byEdge = new Set(getActionsForAsset(assetNode.node_id, edges));
  // 2. via hostname or IP match (fallback for actions without explicit asset_link)
  const byMatch = allNodes
    .filter((n) => {
      if (n.node_type !== 'ACTION') return false;
      if (byEdge.has(n.node_id)) return false; // already found
      if (assetNode.node_hostname && n.node_hostname === assetNode.node_hostname) return true;
      if (assetNode.node_ip && n.node_ip === assetNode.node_ip) return true;
      return false;
    })
    .map((n) => n.node_id);

  const allIds = [...byEdge, ...byMatch];
  return allIds
    .map((id) => allNodes.find((n) => n.node_id === id))
    .filter(Boolean) as AttackPathNode[];
}

// -- Subnet/Zone layout for Variant-2 endpoint view --

export interface ZoneLayout {
  zone_id: string;
  zone_name: string;
  zone_subnet: string;
  zone_x: number;
  zone_y: number;
  zone_width: number;
  zone_height: number;
  zone_color: string;
}

const ZONE_COLORS = [
  'rgba(100,181,246,0.10)',  // blue
  'rgba(129,199,132,0.10)',  // green
  'rgba(186,104,200,0.10)',  // purple
  'rgba(255,183,77,0.10)',   // orange
  'rgba(77,208,225,0.10)',   // cyan
  'rgba(255,138,101,0.10)',  // deep orange
  'rgba(240,98,146,0.10)',   // pink
  'rgba(174,213,129,0.10)',  // light green
  'rgba(159,168,218,0.10)',  // indigo
  'rgba(128,203,196,0.10)',  // teal
];

const ZONE_STROKE_COLORS = [
  'rgba(100,181,246,0.35)',
  'rgba(129,199,132,0.35)',
  'rgba(186,104,200,0.35)',
  'rgba(255,183,77,0.35)',
  'rgba(77,208,225,0.35)',
  'rgba(255,138,101,0.35)',
  'rgba(240,98,146,0.35)',
  'rgba(174,213,129,0.35)',
  'rgba(159,168,218,0.35)',
  'rgba(128,203,196,0.35)',
];

const SUBNET_NODE_SIZE = 100; // diameter of endpoint circle bounding box
const SUBNET_H_GAP = 160;    // horizontal gap between nodes within zone
const SUBNET_ZONE_PAD_TOP = 50;
const SUBNET_ZONE_PAD_X = 30;
const SUBNET_ZONE_PAD_BOTTOM = 30;
const SUBNET_ZONE_V_GAP = 60; // vertical gap between zones

export function computeSubnetLayout(
  nodes: AttackPathNode[],
  edges: AttackPathEdge[],
): { zoneLayouts: ZoneLayout[]; nodeLayouts: LayoutNode[] } {
  // Only ASSET nodes are relevant
  const assetNodes = nodes.filter((n) => n.node_type === 'ASSET');

  // Group by zone
  const zoneMap = new Map<string, AttackPathNode[]>();
  for (const node of assetNodes) {
    const zone = node.node_zone ?? 'Unknown Zone';
    const existing = zoneMap.get(zone) ?? [];
    existing.push(node);
    zoneMap.set(zone, existing);
  }

  // Collect unique zones in order of first appearance
  const zoneNames: string[] = [];
  for (const node of assetNodes) {
    const zone = node.node_zone ?? 'Unknown Zone';
    if (!zoneNames.includes(zone)) zoneNames.push(zone);
  }

  const zoneLayouts: ZoneLayout[] = [];
  const nodeLayouts: LayoutNode[] = [];

  let currentY = 40;

  zoneNames.forEach((zoneName, zoneIdx) => {
    const zoneNodes = zoneMap.get(zoneName) ?? [];
    const zoneSubnet = zoneNodes[0]?.node_subnet ?? '';

    const nodesInRow = zoneNodes.length;
    const zoneWidth = Math.max(
      300,
      SUBNET_ZONE_PAD_X * 2 + nodesInRow * SUBNET_NODE_SIZE + (nodesInRow - 1) * (SUBNET_H_GAP - SUBNET_NODE_SIZE),
    );
    const zoneHeight = SUBNET_ZONE_PAD_TOP + SUBNET_NODE_SIZE + SUBNET_ZONE_PAD_BOTTOM;

    zoneLayouts.push({
      zone_id: `zone-${zoneIdx}`,
      zone_name: zoneName,
      zone_subnet: zoneSubnet,
      zone_x: 40,
      zone_y: currentY,
      zone_width: zoneWidth,
      zone_height: zoneHeight,
      zone_color: ZONE_COLORS[zoneIdx % ZONE_COLORS.length],
    });

    // Position nodes within zone
    zoneNodes.forEach((node, nodeIdx) => {
      const nodeX = 40 + SUBNET_ZONE_PAD_X + nodeIdx * SUBNET_H_GAP;
      const nodeY = currentY + SUBNET_ZONE_PAD_TOP;
      nodeLayouts.push({
        ...node,
        x: nodeX,
        y: nodeY,
        width: SUBNET_NODE_SIZE,
        height: SUBNET_NODE_SIZE,
      });
    });

    currentY += zoneHeight + SUBNET_ZONE_V_GAP;
  });

  // Add ACTION nodes (hidden in subnet view — but include them at a large offset so edge lookups work)
  const actionNodes = nodes.filter((n) => n.node_type === 'ACTION');
  for (const node of actionNodes) {
    nodeLayouts.push({
      ...node,
      x: -9999,
      y: -9999,
      width: 0,
      height: 0,
    });
  }

  return { zoneLayouts, nodeLayouts };
}

export function getZoneStrokeColor(zoneIdx: number): string {
  return ZONE_STROKE_COLORS[zoneIdx % ZONE_STROKE_COLORS.length];
}
