/**
 * AttackPathGraphV7 — Finding Explorer with Multi-Agent Support
 *
 * Extends V6 with:
 *  • Each endpoint circle shows installed agent badges (Palo Alto, SentinelOne, OpenAEV)
 *  • Finding item nodes display the agent logo that discovered/ran the action
 *  • Endpoint tooltip shows which agents are installed
 */

import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import {
  type FunctionComponent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  type AttackPathNode,
  type AttackPathEdge,
  type AttackPathDefinition,
  getActionsForAssetFull,
  STATUS_COLORS,
  AGENT_META,
  type AgentType,
} from './attackPathUtils';

// ── Props ─────────────────────────────────────────────────────────────────────

interface Props {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  paths: AttackPathDefinition[];
  selectedActionNodeId: string | null;
  onNodeClick?: (assetId: string) => void;
  onDetailClick?: (assetId: string) => void;
  onPathClick?: (assetNodeIds: string[]) => void;
  onLegendPathSelect?: (path: AttackPathDefinition | null) => void;
  selectedPathId?: string | null;
  height?: string;
  /**
   * When set (and `seq` changes), the graph will expand the cluster
   * containing `endpointId` and focus that endpoint (dimming its siblings).
   * Use an incrementing `seq` so re-focusing the same endpoint still fires.
   */
  externalFocusRequest?: { endpointId: string; seq: number; findingId?: string } | null;
}

// ── Finding types ─────────────────────────────────────────────────────────────

type FindingType = 'credential' | 'file' | 'port' | 'cve' | 'session';

interface FindingItem {
  id: string;
  type: FindingType;
  label: string;
  edgeLabel: string;
  actionId?: string; // which action produced this finding
  agentType?: AgentType; // which agent ran the action
}

const FINDING_META: Record<FindingType, { icon: string; groupLabel: string }> = {
  credential: { icon: '🔑', groupLabel: 'Credential Found' },
  file:       { icon: '📄', groupLabel: 'File Found' },
  port:       { icon: '🔌', groupLabel: 'Port Open' },
  cve:        { icon: '⚠️', groupLabel: 'CVE Detected' },
  session:    { icon: '👤', groupLabel: 'User Session' },
};

const FINDING_TYPE_ORDER: FindingType[] = ['credential', 'file', 'port', 'cve', 'session'];

// ── Status color helpers ──────────────────────────────────────────────────────

const INJECTOR_COLOR = '#64748b'; // single neutral color for all injectors

function getStatusFill(status: string | undefined): string {
  if (status === 'prevented')  return STATUS_COLORS.prevented.fill;
  if (status === 'detected')   return STATUS_COLORS.detected.fill;
  if (status === 'undetected') return STATUS_COLORS.undetected.fill;
  return '#64748b'; // pending/unknown
}

function getStatusStroke(status: string | undefined): string {
  if (status === 'prevented')  return STATUS_COLORS.prevented.stroke;
  if (status === 'detected')   return STATUS_COLORS.detected.stroke;
  if (status === 'undetected') return STATUS_COLORS.undetected.stroke;
  return '#475569';
}

function getStatusTextColor(status: string | undefined): string {
  return '#fff';
}

function worstStatus(statuses: (string | undefined)[]): string {
  if (statuses.some(s => s === 'undetected')) return 'undetected';
  if (statuses.some(s => s === 'detected'))   return 'detected';
  if (statuses.some(s => s === 'prevented'))  return 'prevented';
  return 'pending';
}

// ── Injector helpers ──────────────────────────────────────────────────────────

function hexPoints(cx: number, cy: number, r: number): string {
  return Array.from({ length: 6 }, (_, i) => {
    const angle = (i * 60 - 30) * Math.PI / 180;
    return `${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`;
  }).join(' ');
}

function isInjectorAction(action: AttackPathNode): boolean {
  const pn = (action.node_payload_name ?? '').toLowerCase();
  const lb = action.node_label.toLowerCase();
  return (
    pn.startsWith('nmap') || pn.startsWith('netexec') || pn.startsWith('nuclei') ||
    lb.startsWith('nmap') || lb.startsWith('netexec') || lb.startsWith('nuclei')
  );
}

function injectorTool(action: AttackPathNode): string {
  const pn = (action.node_payload_name ?? '').toLowerCase();
  if (pn.startsWith('nmap'))    return 'nmap';
  if (pn.startsWith('netexec')) return 'netexec';
  if (pn.startsWith('nuclei'))  return 'nuclei';
  return 'injector';
}

interface InjectorGroup {
  tool: string;
  contracts: Array<{ action: AttackPathNode; targetAssetId: string }>;
}

// ── Extract findings for a single ASSET ──────────────────────────────────────

function extractFindings(
  assetId: string,
  allNodes: AttackPathNode[],
  edges: AttackPathEdge[],
): FindingItem[] {
  const assetNode = allNodes.find((n) => n.node_id === assetId);
  const actionEdges = edges.filter((e) => e.edge_type === 'asset_link' && e.edge_target === assetId);
  const actionNodes = actionEdges
    .map((e) => allNodes.find((n) => n.node_id === e.edge_source))
    .filter(Boolean) as AttackPathNode[];

  const findings: FindingItem[] = [];

  // Helper: resolve agentType from the action that produced a finding
  const agentFor = (actionId: string) => allNodes.find(n => n.node_id === actionId)?.node_agent;

  // Credentials
  const credSet = new Map<string, string>(); // cred → actionId
  for (const c of assetNode?.node_credentials_found ?? []) credSet.set(c, '');
  for (const a of actionNodes) {
    for (const c of a.node_credentials_found ?? []) credSet.set(c, a.node_id);
  }
  for (const [cred, actionId] of Array.from(credSet)) {
    const label = cred.length > 22 ? cred.slice(0, 20) + '…' : cred;
    findings.push({ id: `${assetId}::cred::${cred}`, type: 'credential', label, edgeLabel: 'Credential Found', actionId, agentType: agentFor(actionId) });
  }

  // Files
  const fileSet = new Map<string, string>();
  for (const f of assetNode?.node_accessed_files ?? []) fileSet.set(f, '');
  for (const a of actionNodes) {
    for (const f of a.node_accessed_files ?? []) fileSet.set(f, a.node_id);
  }
  for (const [f, actionId] of Array.from(fileSet)) {
    const name = f.split(/[\\/]/).pop() ?? f;
    const label = name.length > 22 ? name.slice(0, 20) + '…' : name;
    findings.push({ id: `${assetId}::file::${f}`, type: 'file', label, edgeLabel: 'File Found', actionId, agentType: agentFor(actionId) });
  }

  // Open ports
  const portMap = new Map<string, { label: string; actionId: string }>();
  for (const a of actionNodes) {
    for (const p of a.node_ports_found ?? []) {
      const portNum = p.match(/^(\d+\/(?:tcp|udp))/i)?.[1] ?? p.split(' ')[0];
      if (!portMap.has(portNum)) portMap.set(portNum, { label: p, actionId: a.node_id });
    }
    const out = a.node_terminal_output ?? '';
    for (const m of Array.from(out.matchAll(/(\d+)\/tcp\s+open\s*(\S*)?/gi))) {
      const portNum = `${m[1]}/tcp`;
      if (!portMap.has(portNum)) {
        const label = m[2] ? `${m[1]}/tcp ${m[2]}` : `${m[1]}/tcp`;
        portMap.set(portNum, { label, actionId: a.node_id });
      }
    }
  }
  let portCount = 0;
  for (const [, { label, actionId }] of Array.from(portMap)) {
    if (portCount++ >= 6) break;
    findings.push({ id: `${assetId}::port::${label}`, type: 'port', label, edgeLabel: 'Port Open', actionId, agentType: agentFor(actionId) });
  }

  // CVEs
  const cveMap = new Map<string, { label: string; actionId: string }>();
  for (const a of actionNodes) {
    for (const c of a.node_cves_found ?? []) {
      const cveId = c.match(/CVE-\d{4}-\d+/i)?.[0]?.toUpperCase() ?? c;
      if (!cveMap.has(cveId)) cveMap.set(cveId, { label: c.slice(0, 30), actionId: a.node_id });
    }
    for (const m of Array.from((a.node_terminal_output ?? '').matchAll(/CVE-\d{4}-\d+/gi))) {
      const cveId = m[0].toUpperCase();
      if (!cveMap.has(cveId)) cveMap.set(cveId, { label: cveId, actionId: a.node_id });
    }
  }
  for (const [, { label, actionId }] of Array.from(cveMap)) {
    findings.push({ id: `${assetId}::cve::${label}`, type: 'cve', label, edgeLabel: 'CVE Detected', actionId, agentType: agentFor(actionId) });
  }

  // User sessions
  const sessionSet = new Map<string, string>();
  if (assetNode?.node_user_privileges) sessionSet.set(assetNode.node_user_privileges, '');
  for (const a of actionNodes) {
    if (a.node_user_privileges) sessionSet.set(a.node_user_privileges, a.node_id);
  }
  for (const [priv, actionId] of Array.from(sessionSet)) {
    const label = priv.length > 22 ? priv.slice(0, 20) + '…' : priv;
    findings.push({ id: `${assetId}::session::${priv}`, type: 'session', label, edgeLabel: 'User Session', actionId, agentType: agentFor(actionId) });
  }

  return findings;
}

// ── Layout helpers ────────────────────────────────────────────────────────────

function seededFloat(seed: string): number {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) h = ((h << 5) + h) ^ seed.charCodeAt(i);
  return (h >>> 0) / 0xffffffff;
}

function computeBaseLayout(
  assetNodes: AttackPathNode[],
  paths: AttackPathDefinition[],
  startX: number,
): Array<{ nodeId: string; cx: number; cy: number }> {
  if (assetNodes.length === 0) return [];
  const n = assetNodes.length;
  const adjacent = new Map<string, Set<string>>();
  for (const node of assetNodes) adjacent.set(node.node_id, new Set());
  for (const path of paths) {
    for (let i = 0; i < path.node_ids.length - 1; i++) {
      adjacent.get(path.node_ids[i])?.add(path.node_ids[i + 1]);
      adjacent.get(path.node_ids[i + 1])?.add(path.node_ids[i]);
    }
  }
  const ordered: string[] = [];
  const visited = new Set<string>();
  const entryIds = assetNodes.filter((nd) => nd.node_is_entry_point).map((nd) => nd.node_id);
  const queue: string[] = entryIds.length > 0 ? [...entryIds] : [assetNodes[0].node_id];
  for (const id of queue) visited.add(id);
  while (queue.length > 0) {
    const id = queue.shift()!;
    ordered.push(id);
    for (const nb of Array.from(adjacent.get(id) ?? [])) {
      if (!visited.has(nb)) { visited.add(nb); queue.push(nb); }
    }
  }
  for (const node of assetNodes) if (!visited.has(node.node_id)) ordered.push(node.node_id);

  const COLS   = Math.max(3, Math.ceil(Math.sqrt(n * 0.75)));
  const STEP_X = 270;
  const STEP_Y = 230;
  const STAGGER = STEP_X * 0.5;
  const JX_MAX = 55; const JY_MAX = 40;
  // Start Y lower when few nodes so contracts don't go off-canvas
  const START_Y = n <= 5 ? 280 : 140;

  return ordered.map((nodeId, i) => {
    const col = i % COLS; const row = Math.floor(i / COLS);
    const rowNudge = (seededFloat(`row${row}nudge`) - 0.5) * 16;
    const jx = (seededFloat(nodeId + 'jx') - 0.5) * JX_MAX * 2;
    const jy = (seededFloat(nodeId + 'jy') - 0.5) * JY_MAX * 2;
    return {
      nodeId,
      cx: startX + col * STEP_X + (row % 2) * STAGGER + jx,
      cy: START_Y + row * STEP_Y + rowNudge + jy,
    };
  });
}

// Position N type-group nodes around an endpoint in a 270° arc on the right side
function computeGroupPositions(
  cx: number, cy: number, count: number, radius: number,
): Array<{ fx: number; fy: number; angle: number }> {
  if (count === 0) return [];
  if (count === 1) return [{ fx: cx + radius, fy: cy, angle: 0 }];
  const spread = Math.PI * 1.5; // 270°
  const startAngle = -Math.PI * 0.75; // start top-right (-135°)
  return Array.from({ length: count }, (_, i) => {
    const angle = count === 1 ? 0 : startAngle + (i / (count - 1)) * spread;
    return { fx: cx + Math.cos(angle) * radius, fy: cy + Math.sin(angle) * radius, angle };
  });
}

// Position M item nodes at a fixed radius from endpoint center, in the angular direction of their group
function computeItemPositions(
  cx: number, cy: number,
  groupAngle: number,
  count: number,
  itemRadius: number,
): Array<{ fx: number; fy: number }> {
  if (count === 0) return [];
  const sectorWidth = Math.min(Math.PI * 0.45, count * 0.28);
  const startAngle = groupAngle - sectorWidth / 2;
  return Array.from({ length: count }, (_, i) => {
    const a = count === 1 ? groupAngle : startAngle + (i / (count - 1)) * sectorWidth;
    return { fx: cx + Math.cos(a) * itemRadius, fy: cy + Math.sin(a) * itemRadius };
  });
}

// ── Bezier helper ─────────────────────────────────────────────────────────────

function bezierPoint(t: number, x1: number, y1: number, cpx: number, cpy: number, x2: number, y2: number) {
  const mt = 1 - t;
  return { x: mt*mt*x1 + 2*mt*t*cpx + t*t*x2, y: mt*mt*y1 + 2*mt*t*cpy + t*t*y2 };
}

// ── Constants ─────────────────────────────────────────────────────────────────

const NODE_R         = 34;
const GROUP_R        = 26;   // finding TYPE group node radius
const FINDING_R      = 20;   // individual finding item radius
const GROUP_ORBIT    = 130;  // orbit radius for finding type groups
const ITEM_ORBIT_R   = 220;  // item radius from ENDPOINT center (not group center)
const INJECTOR_X     = 90;
const CONTRACT_X     = 240;
const CONTRACT_SPACING = 54;
const CONTRACT_GROUP_GAP = 32;
const INJECTOR_R     = 36;
const ENDPOINT_START_X = 340; // give room for injector hexagons on the left

const CLUSTER_R            = 55;   // radius of collapsed cluster circle
const CLUSTER_EXPAND_ORBIT = 200;  // push radius when cluster expands

// ── Component ─────────────────────────────────────────────────────────────────

const AttackPathGraphV7: FunctionComponent<Props> = ({
  nodes, edges, paths,
  selectedActionNodeId,
  onNodeClick, onDetailClick,
  onPathClick, onLegendPathSelect,
  selectedPathId,
  externalFocusRequest,
  height = '100%',
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 1600, h: 900 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  // Drag state
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const draggingIdRef = useRef<string | null>(null);
  const hasDraggedRef = useRef(false);
  const dragStartClientRef = useRef<{ x: number; y: number } | null>(null);
  const DRAG_THRESHOLD = 6; // pixels before a mousedown+move is treated as drag (not click)
  const [dragOverrides, setDragOverrides] = useState<Map<string, { cx: number; cy: number }>>(new Map());

  // Expansion state
  const [expandedEndpoints, setExpandedEndpoints] = useState<Set<string>>(new Set());
  // Per-endpoint: which finding TYPE groups are expanded
  const [expandedGroups, setExpandedGroups] = useState<Map<string, Set<FindingType>>>(new Map());
  // Selected finding item (for action-to-finding link)
  const [selectedFindingId, setSelectedFindingId] = useState<string | null>(null);

  // Tooltip
  const [tooltip, setTooltip] = useState<{ node: AttackPathNode; x: number; y: number } | null>(null);
  const tooltipHideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const showTooltip = (node: AttackPathNode, x: number, y: number) => {
    if (tooltipHideTimer.current) clearTimeout(tooltipHideTimer.current);
    setTooltip({ node, x, y });
  };
  const hideTooltip = (delay = 150) => {
    tooltipHideTimer.current = setTimeout(() => setTooltip(null), delay);
  };

  // Expanded connection badges (unified injector→EP and EP→EP)
  const [expandedConnections, setExpandedConnections] = useState<Set<string>>(new Set());
  const [expandedClusters, setExpandedClusters] = useState<Set<string>>(new Set());
  // Which endpoint is focused within an expanded cluster (dims its siblings)
  const [focusedEndpointId, setFocusedEndpointId] = useState<string | null>(null);

  // Animated node coordinates — smoothly interpolated toward finalNodeCoords
  const [displayCoords, setDisplayCoords] = useState<Array<{ nodeId: string; cx: number; cy: number }>>([]);
  const animNodeFrameRef = useRef<number>(0);
  const animFromCoordsRef = useRef<Array<{ nodeId: string; cx: number; cy: number }>>([]);
  const isFirstCoordRenderRef = useRef(true);

  // ViewBox animation
  const animViewFrameRef = useRef<number>(0);
  const viewBoxAnimFromRef = useRef({ x: 0, y: 0, w: 1600, h: 900 });

  const toggleConnection = useCallback((key: string) => {
    setExpandedConnections(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }, []);

  // ── Zoom / Pan ──────────────────────────────────────────────────────────────
  const zoomFn = useCallback((factor: number) => {
    setViewBox((vb) => {
      const cx = vb.x + vb.w / 2; const cy = vb.y + vb.h / 2;
      const nw = Math.min(Math.max(vb.w * factor, 300), 6000);
      const nh = Math.min(Math.max(vb.h * factor, 200), 6000);
      return { x: cx - nw / 2, y: cy - nh / 2, w: nw, h: nh };
    });
  }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault(); zoomFn(e.deltaY > 0 ? 1.1 : 0.9);
  }, [zoomFn]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button === 0 && !draggingId) { setIsPanning(true); setPanStart({ x: e.clientX, y: e.clientY }); }
  }, [draggingId]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (draggingIdRef.current) {
      // Only treat as drag after moving DRAG_THRESHOLD pixels from mousedown position
      if (dragStartClientRef.current) {
        const dx = e.clientX - dragStartClientRef.current.x;
        const dy = e.clientY - dragStartClientRef.current.y;
        if (Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD) hasDraggedRef.current = true;
      }
      if (!hasDraggedRef.current) return; // haven't crossed threshold yet — no position update
      const svg = svgRef.current;
      if (!svg) return;
      const rect = svg.getBoundingClientRect();
      const svgX = (e.clientX - rect.left) / rect.width  * viewBox.w + viewBox.x;
      const svgY = (e.clientY - rect.top)  / rect.height * viewBox.h + viewBox.y;
      setDragOverrides((prev) => {
        const next = new Map(prev);
        next.set(draggingIdRef.current!, { cx: svgX, cy: svgY });
        return next;
      });
      return;
    }
    if (!isPanning) return;
    const dx = (e.clientX - panStart.x) * (viewBox.w / (svgRef.current?.clientWidth ?? 1));
    const dy = (e.clientY - panStart.y) * (viewBox.h / (svgRef.current?.clientHeight ?? 1));
    setViewBox((vb) => ({ ...vb, x: vb.x - dx, y: vb.y - dy }));
    setPanStart({ x: e.clientX, y: e.clientY });
  }, [isPanning, panStart, viewBox]);

  const handleMouseUp = useCallback(() => {
    setIsPanning(false);
    draggingIdRef.current = null;
    dragStartClientRef.current = null;
    setDraggingId(null);
  }, []);

  // ── Data ────────────────────────────────────────────────────────────────────
  const assetNodes = useMemo(() => nodes.filter((n) => n.node_type === 'ASSET'), [nodes]);

  // When a path is selected, only show path endpoints (re-layout compactly)
  const visibleAssetNodes = useMemo(() => {
    if (!selectedPathId) return assetNodes;
    const path = paths.find(p => p.path_id === selectedPathId);
    if (!path) return assetNodes;
    const pathSet = new Set(path.node_ids);
    return assetNodes.filter(n => pathSet.has(n.node_id));
  }, [assetNodes, paths, selectedPathId]);

  // Group endpoints by attack path — each cluster = all endpoints in one path.
  // "Others" cluster for endpoints not in any path.
  // Only activates when no specific path is selected and there are multiple paths.
  const endpointClusters = useMemo(() => {
    if (selectedPathId) return null; // individual endpoints shown when path selected
    if (paths.length < 1) return null; // no paths, nothing to cluster

    const pathToEndpoints = new Map<string, AttackPathNode[]>();
    const inAnyPath = new Set<string>();

    for (const path of paths) {
      const members: AttackPathNode[] = [];
      for (const nodeId of path.node_ids) {
        const asset = visibleAssetNodes.find(n => n.node_id === nodeId);
        if (asset) { members.push(asset); inAnyPath.add(nodeId); }
      }
      if (members.length > 0) pathToEndpoints.set(path.path_id, members);
    }

    const clusters: { id: string; pathName: string; pathColor: string; nodes: AttackPathNode[] }[] = [];
    for (const [pathId, members] of Array.from(pathToEndpoints.entries())) {
      const path = paths.find(p => p.path_id === pathId)!;
      clusters.push({ id: `pathcluster-${pathId}`, pathName: path.path_name, pathColor: path.path_color, nodes: members });
    }

    // Endpoints not in any path → split into "Untouched" (node_untouched) vs "Others"
    const notInPath = visibleAssetNodes.filter(n => !inAnyPath.has(n.node_id));
    const untouched = notInPath.filter(n => n.node_untouched);
    const others = notInPath.filter(n => !n.node_untouched);
    if (untouched.length > 0) {
      clusters.push({ id: 'pathcluster-untouched', pathName: 'Untouched', pathColor: '#607d8b', nodes: untouched });
    }
    if (others.length > 0) {
      clusters.push({ id: 'pathcluster-others', pathName: 'Others', pathColor: '#888888', nodes: others });
    }

    return clusters.length >= 1 ? clusters : null;
  }, [visibleAssetNodes, paths, selectedPathId]);

  const baseCoords = useMemo(
    () => computeBaseLayout(visibleAssetNodes, paths, ENDPOINT_START_X),
    [visibleAssetNodes, paths],
  );

  const baseCoordMap = useMemo(() => {
    const m = new Map<string, { cx: number; cy: number }>();
    for (const c of baseCoords) m.set(c.nodeId, c);
    return m;
  }, [baseCoords]);

  // Cluster center positions — well-spaced layout with guaranteed minimum separation.
  const clusterBaseCenters = useMemo(() => {
    if (!endpointClusters) return new Map<string, { cx: number; cy: number }>();
    const m = new Map<string, { cx: number; cy: number }>();
    const n = endpointClusters.length;
    // Minimum cluster-to-cluster distance to avoid overlap when expanded
    const MIN_SEP = Math.max(500, CLUSTER_R * 8);
    const SVG_W = 1600;
    const AVAIL_START = ENDPOINT_START_X + 100;
    const AVAIL_W = SVG_W - AVAIL_START - 80;
    // Fit into rows ensuring min separation
    const COLS = n <= 3 ? n : n <= 6 ? Math.ceil(n / 2) : Math.ceil(n / 3);
    const ROWS = Math.ceil(n / COLS);
    const colStep = Math.max(MIN_SEP, AVAIL_W / COLS);
    const rowStep = 420;
    // Center the grid vertically
    const totalH = (ROWS - 1) * rowStep;
    const startY = 420 - totalH / 2;
    // Center the columns in available width
    const totalW = (COLS - 1) * colStep;
    const startX = AVAIL_START + (AVAIL_W - totalW) / 2;
    endpointClusters.forEach((cluster, i) => {
      const override = dragOverrides.get(cluster.id);
      if (override) { m.set(cluster.id, override); return; }
      const col = i % COLS;
      const row = Math.floor(i / COLS);
      // Last row: center any orphan clusters
      const rowN = row === ROWS - 1 ? n - row * COLS : COLS;
      const rowOffset = rowN < COLS ? (COLS - rowN) * colStep / 2 : 0;
      m.set(cluster.id, { cx: startX + col * colStep + rowOffset, cy: startY + row * rowStep });
    });
    return m;
  }, [endpointClusters, dragOverrides]);

  // When a connection badge is expanded, automatically push connected nodes apart
  // with collision resolution to prevent endpoint overlap
  const autoSpacings = useMemo(() => {
    if (expandedConnections.size === 0 && expandedClusters.size === 0) return new Map<string, { dx: number; dy: number }>();
    const spacings = new Map<string, { dx: number; dy: number }>();
    const push = (id: string, ddx: number, ddy: number) => {
      const ex = spacings.get(id) ?? { dx: 0, dy: 0 };
      spacings.set(id, { dx: ex.dx + ddx, dy: ex.dy + ddy });
    };
    const PUSH = 200;
    for (const badgeKey of Array.from(expandedConnections)) {
      if (badgeKey.startsWith('inj:')) {
        const parts = badgeKey.split(':');
        const toId = parts[2];
        push(toId, PUSH, 0);
      }
    }
    // Push nodes away from expanded cluster centers
    if (endpointClusters) {
      for (const clusterId of Array.from(expandedClusters)) {
        const clusterCenter = clusterBaseCenters.get(clusterId);
        const cluster = endpointClusters.find(c => c.id === clusterId);
        if (!clusterCenter || !cluster) continue;
        const MIN_DIST = CLUSTER_EXPAND_ORBIT + NODE_R + 30;
        for (const c of baseCoords) {
          if (cluster.nodes.some(n => n.node_id === c.nodeId)) continue; // own members stay
          if (dragOverrides.has(c.nodeId)) continue;
          const ddx = c.cx - clusterCenter.cx;
          const ddy = c.cy - clusterCenter.cy;
          const dist = Math.sqrt(ddx * ddx + ddy * ddy) || 1;
          if (dist < MIN_DIST) {
            const amt = (MIN_DIST - dist) * 1.2;
            push(c.nodeId, (ddx / dist) * amt, (ddy / dist) * amt);
          }
        }
      }
    }
    // Collision resolution: iteratively separate any overlapping endpoints
    // Only auto-space nodes not already manually positioned
    const MIN_SEP = NODE_R * 2 + 50;
    const positions = new Map<string, { x: number; y: number }>();
    for (const c of baseCoords) {
      if (!dragOverrides.has(c.nodeId)) {
        const auto = spacings.get(c.nodeId) ?? { dx: 0, dy: 0 };
        positions.set(c.nodeId, { x: c.cx + auto.dx, y: c.cy + auto.dy });
      }
    }
    const ids = Array.from(positions.keys());
    for (let iter = 0; iter < 6; iter++) {
      let settled = true;
      for (let i = 0; i < ids.length; i++) {
        for (let j = i + 1; j < ids.length; j++) {
          const a = positions.get(ids[i])!;
          const b = positions.get(ids[j])!;
          const dx = b.x - a.x; const dy = b.y - a.y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          if (dist < MIN_SEP) {
            settled = false;
            const overlap = (MIN_SEP - dist) / 2 + 2;
            const nx = dx / dist; const ny = dy / dist;
            a.x -= nx * overlap; a.y -= ny * overlap;
            b.x += nx * overlap; b.y += ny * overlap;
            positions.set(ids[i], a);
            positions.set(ids[j], b);
          }
        }
      }
      if (settled) break;
    }
    const result = new Map<string, { dx: number; dy: number }>();
    for (const c of baseCoords) {
      if (dragOverrides.has(c.nodeId)) continue;
      const final = positions.get(c.nodeId);
      if (!final) continue;
      const dx = final.x - c.cx; const dy = final.y - c.cy;
      if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) result.set(c.nodeId, { dx, dy });
    }
    return result;
  }, [expandedConnections, baseCoordMap, baseCoords, dragOverrides, expandedClusters, endpointClusters, clusterBaseCenters]);

  const nodeCoords = useMemo(() => {
    const coords = baseCoords.map((c) => {
      if (dragOverrides.has(c.nodeId)) return { ...c, ...dragOverrides.get(c.nodeId)! };
      const auto = autoSpacings.get(c.nodeId);
      if (auto) return { ...c, cx: c.cx + auto.dx, cy: c.cy + auto.dy };
      return c;
    });
    if (!endpointClusters) return coords;
    return coords.map(c => {
      // Preserve drag overrides — never remap a node being dragged
      if (dragOverrides.has(c.nodeId)) return c;
      // If ANY cluster containing this endpoint is expanded, use base position
      const anyExpanded = endpointClusters.some(
        cl => expandedClusters.has(cl.id) && cl.nodes.some(n => n.node_id === c.nodeId)
      );
      if (anyExpanded) {
        // Grid layout centred on the expanded cluster's base center
        const expandedCluster = endpointClusters.find(
          cl => expandedClusters.has(cl.id) && cl.nodes.some(n => n.node_id === c.nodeId)
        );
        if (expandedCluster) {
          const center = clusterBaseCenters.get(expandedCluster.id);
          if (center) {
            const idx = expandedCluster.nodes.findIndex(n => n.node_id === c.nodeId);
            const total = expandedCluster.nodes.length;
            const GCOLS = Math.ceil(Math.sqrt(total));
            const STEP = NODE_R * 2 + 70; // generous spacing to avoid overlap
            const totalW = (GCOLS - 1) * STEP;
            const totalH = (Math.ceil(total / GCOLS) - 1) * STEP;
            return {
              ...c,
              cx: center.cx - totalW / 2 + (idx % GCOLS) * STEP,
              cy: center.cy - totalH / 2 + Math.floor(idx / GCOLS) * STEP,
            };
          }
        }
        return c;
      }
      // Otherwise remap to the first collapsed cluster that contains it
      const cluster = endpointClusters.find(
        cl => !expandedClusters.has(cl.id) && cl.nodes.some(n => n.node_id === c.nodeId)
      );
      if (!cluster) return c;
      const center = clusterBaseCenters.get(cluster.id);
      if (!center) return c;
      return { ...c, cx: center.cx, cy: center.cy };
    });
  }, [baseCoords, dragOverrides, autoSpacings, endpointClusters, expandedClusters, clusterBaseCenters]);

  // Post-grid collision resolution: push nodes apart when clusters expand,
  // and also when finding groups are expanded (push ALL nearby nodes away).
  const finalNodeCoords = useMemo(() => {
    const hasExpandedGroups = expandedGroups.size > 0 && Array.from(expandedGroups.values()).some(s => s.size > 0);
    const hasExpandedClusters = endpointClusters && expandedClusters.size > 0;

    if (!hasExpandedClusters && !hasExpandedGroups) return nodeCoords;

    // Radius used for collision: larger when this endpoint has finding groups open
    const nodeRadius = (nodeId: string) => {
      const groups = expandedGroups.get(nodeId);
      if (groups && groups.size > 0) return ITEM_ORBIT_R + FINDING_R + 30; // ~270 — full finding expansion space
      return NODE_R + 10; // 44 — basic node radius
    };

    // Collect ALL node positions (not just cluster members) so finding expansion
    // can push any nearby endpoint out of the way.
    const positions = new Map<string, { x: number; y: number }>();
    for (const c of nodeCoords) {
      if (!dragOverrides.has(c.nodeId)) {
        positions.set(c.nodeId, { x: c.cx, y: c.cy });
      }
    }

    // For cluster expansion: restrict movement to expanded cluster members only
    const expandedMemberIds = new Set<string>();
    if (hasExpandedClusters && endpointClusters) {
      for (const cluster of endpointClusters) {
        if (expandedClusters.has(cluster.id)) {
          for (const node of cluster.nodes) expandedMemberIds.add(node.node_id);
        }
      }
    }

    const ids = Array.from(positions.keys());
    for (let iter = 0; iter < 30; iter++) {
      let settled = true;
      for (let i = 0; i < ids.length; i++) {
        for (let j = i + 1; j < ids.length; j++) {
          const idA = ids[i]; const idB = ids[j];
          const a = positions.get(idA)!;
          const b = positions.get(idB)!;
          const minSep = nodeRadius(idA) + nodeRadius(idB) + 20;
          const dx = b.x - a.x; const dy = b.y - a.y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          if (dist < minSep) {
            settled = false;
            const overlap = (minSep - dist) / 2 + 2;
            const nx = dx / dist; const ny = dy / dist;
            // For cluster-only expansion: only move expanded members
            // For finding expansion: move any non-dragged node
            const aHasFindings = (expandedGroups.get(idA)?.size ?? 0) > 0;
            const bHasFindings = (expandedGroups.get(idB)?.size ?? 0) > 0;
            const aCanMove = aHasFindings || expandedMemberIds.has(idA);
            const bCanMove = bHasFindings || expandedMemberIds.has(idB);
            if (aCanMove && !bHasFindings) { a.x -= nx * overlap; a.y -= ny * overlap; positions.set(idA, a); }
            if (bCanMove && !aHasFindings) { b.x += nx * overlap; b.y += ny * overlap; positions.set(idB, b); }
            if (!aCanMove && bHasFindings) { a.x -= nx * overlap; a.y -= ny * overlap; positions.set(idA, a); }
            if (!bCanMove && aHasFindings) { b.x += nx * overlap; b.y += ny * overlap; positions.set(idB, b); }
          }
        }
      }
      if (settled) break;
    }
    return nodeCoords.map(c => {
      if (dragOverrides.has(c.nodeId)) return c;
      const final = positions.get(c.nodeId);
      if (!final) return c;
      return { ...c, cx: final.x, cy: final.y };
    });
  }, [nodeCoords, expandedClusters, endpointClusters, dragOverrides, expandedGroups]);

  // Collapsed cluster circles pushed away from any expanded cluster (visual only)
  const clusterDisplayCenters = useMemo(() => {
    if (!endpointClusters || expandedClusters.size === 0) return clusterBaseCenters;
    const m = new Map(clusterBaseCenters);
    const PUSH_MIN = 600;
    for (const expandedId of Array.from(expandedClusters)) {
      const expandedCenter = clusterBaseCenters.get(expandedId);
      if (!expandedCenter) continue;
      for (const cluster of endpointClusters) {
        if (cluster.id === expandedId || dragOverrides.has(cluster.id)) continue;
        const base = clusterBaseCenters.get(cluster.id);
        if (!base) continue;
        const dx = base.cx - expandedCenter.cx;
        const dy = base.cy - expandedCenter.cy;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        if (dist < PUSH_MIN) {
          const amt = PUSH_MIN - dist;
          m.set(cluster.id, { cx: base.cx + (dx / dist) * amt, cy: base.cy + (dy / dist) * amt });
        }
      }
    }
    return m;
  }, [endpointClusters, clusterBaseCenters, expandedClusters, dragOverrides]);

  // Node IDs that are "in focus" when a cluster is expanded (for opacity dimming)
  const focusedNodeIds = useMemo((): Set<string> | null => {
    if (expandedClusters.size === 0 || !endpointClusters) return null;
    const ids = new Set<string>();
    for (const cluster of endpointClusters) {
      if (expandedClusters.has(cluster.id)) {
        for (const node of cluster.nodes) ids.add(node.node_id);
      }
    }
    return ids;
  }, [expandedClusters, endpointClusters]);

  const coordMap = useMemo(() => {
    // Use animated display coords if available; fall back to final positions before first animation tick
    const source = displayCoords.length > 0 ? displayCoords : finalNodeCoords;
    const m = new Map<string, { cx: number; cy: number }>();
    for (const c of source) m.set(c.nodeId, c);
    if (endpointClusters) {
      for (const cluster of endpointClusters) {
        // Use display center so lines connect to where cluster circle is visually
        const center = clusterDisplayCenters.get(cluster.id);
        if (center) m.set(cluster.id, center);
      }
    }
    return m;
  }, [displayCoords, finalNodeCoords, endpointClusters, clusterDisplayCenters]);

  // ── Findings ────────────────────────────────────────────────────────────────
  const allFindings = useMemo(() => {
    const m = new Map<string, FindingItem[]>();
    for (const asset of assetNodes) m.set(asset.node_id, extractFindings(asset.node_id, nodes, edges));
    return m;
  }, [assetNodes, nodes, edges]);

  // Group findings by type per endpoint
  const findingsByType = useMemo(() => {
    const m = new Map<string, Map<FindingType, FindingItem[]>>();
    for (const [assetId, findings] of Array.from(allFindings)) {
      const byType = new Map<FindingType, FindingItem[]>();
      for (const f of findings) {
        if (!byType.has(f.type)) byType.set(f.type, []);
        byType.get(f.type)!.push(f);
      }
      m.set(assetId, byType);
    }
    return m;
  }, [allFindings]);

  // ── Injectors ───────────────────────────────────────────────────────────────
  const injectorGroups = useMemo<InjectorGroup[]>(() => {
    const groupMap = new Map<string, InjectorGroup>();
    for (const assetNode of assetNodes) {
      const acts = getActionsForAssetFull(assetNode, nodes, edges).filter(isInjectorAction);
      for (const act of acts) {
        const tool = injectorTool(act);
        if (!groupMap.has(tool)) groupMap.set(tool, { tool, contracts: [] });
        groupMap.get(tool)!.contracts.push({ action: act, targetAssetId: assetNode.node_id });
      }
    }
    for (const group of Array.from(groupMap.values())) {
      group.contracts.sort((a: InjectorGroup['contracts'][number], b: InjectorGroup['contracts'][number]) => (a.action.node_executed_at ?? '').localeCompare(b.action.node_executed_at ?? ''));
    }
    return Array.from(groupMap.values());
  }, [assetNodes, nodes, edges]);

  // ── Path-aware action ids ───────────────────────────────────────────────────
  const pathActionIds = useMemo(() => {
    if (!selectedPathId) return new Set<string>();
    const path = paths.find((p) => p.path_id === selectedPathId);
    if (!path) return new Set<string>();
    const pathEndpointIds = new Set(path.node_ids);
    const set = new Set<string>();
    for (const group of injectorGroups) {
      for (const { action, targetAssetId } of group.contracts) {
        if (pathEndpointIds.has(targetAssetId)) set.add(action.node_id);
      }
    }
    return set;
  }, [selectedPathId, paths, injectorGroups]);

  // When path is selected, only keep contracts relevant to path endpoints
  const visibleInjectorGroups = useMemo<InjectorGroup[]>(() => {
    if (!selectedPathId) return injectorGroups;
    return injectorGroups
      .map(g => ({ ...g, contracts: g.contracts.filter(c => pathActionIds.has(c.action.node_id)) }))
      .filter(g => g.contracts.length > 0);
  }, [injectorGroups, selectedPathId, pathActionIds]);

  const contractPositions = useMemo(() => {
    const cMap = new Map<string, { cx: number; cy: number }>();
    for (const group of visibleInjectorGroups) {
      const toolIdx = visibleInjectorGroups.findIndex(g => g.tool === group.tool);
      const totalTools = visibleInjectorGroups.length;
      const toolYOffset = totalTools <= 1 ? 0 : (toolIdx - (totalTools - 1) / 2) * 50;
      for (const { action, targetAssetId } of group.contracts) {
        const epCoord = coordMap.get(targetAssetId);
        if (epCoord) {
          const injDrag = dragOverrides.get(`inj:${group.tool}`);
          // In cluster mode keep the action anchor visible: stay at most 150px left of endpoint
          const injX = injDrag?.cx ?? (endpointClusters
            ? Math.max(INJECTOR_X, epCoord.cx - 150)
            : INJECTOR_X);
          const injY = injDrag?.cy ?? (epCoord.cy + toolYOffset);
          cMap.set(action.node_id, { cx: (injX + epCoord.cx) / 2, cy: (injY + epCoord.cy) / 2 });
        }
      }
    }
    return cMap;
  }, [visibleInjectorGroups, coordMap, dragOverrides, endpointClusters]);

  // ── Selected finding → source action link (bezier arc to injector diamond) ──
  const selectedFindingActionLink = useMemo(() => {
    if (!selectedFindingId) return null;
    for (const [assetId, findings] of Array.from(allFindings)) {
      const f = findings.find((x: FindingItem) => x.id === selectedFindingId);
      if (!f || !f.actionId) continue;
      const epCoord = coordMap.get(assetId);
      if (!epCoord) continue;
      const byType = findingsByType.get(assetId);
      if (!byType) continue;
      const typeKeys = FINDING_TYPE_ORDER.filter((t) => (byType.get(t)?.length ?? 0) > 0);
      const groupPositions = computeGroupPositions(epCoord.cx, epCoord.cy, typeKeys.length, GROUP_ORBIT);
      const groupIdx = typeKeys.indexOf(f.type);
      const gPos = groupPositions[groupIdx];
      if (!gPos) continue;
      const typeItems = byType.get(f.type) ?? [];
      const itemIdx = typeItems.findIndex((x) => x.id === f.id);
      const itemPositions = computeItemPositions(epCoord.cx, epCoord.cy, gPos.angle, typeItems.length, ITEM_ORBIT_R);
      const iPos = itemPositions[itemIdx];
      if (!iPos) return null;
      const itemOverride = dragOverrides.get(`item:${f.id}`);
      const actionNode = nodes.find(n => n.node_id === f.actionId);
      const actionLabel = actionNode?.node_label ?? 'Action';
      const actionStatus = actionNode?.node_status;

      // Find which injector group runs this action and compute its diamond position.
      // We mirror the injectorPositions formula here since that memo comes after us in the chain.
      const tIdx = visibleInjectorGroups.findIndex(g => g.contracts.some(c => c.action.node_id === f.actionId));
      const totalTools = visibleInjectorGroups.length;

      let ax: number, ay: number;
      if (tIdx >= 0) {
        // Action is from a known injector — point to its hexagon right edge
        const injKey = `inj:${visibleInjectorGroups[tIdx].tool}`;
        const injOverride = dragOverrides.get(injKey);
        const svgH = Math.max(900, ...finalNodeCoords.map(c => c.cy + 250));
        const baseInjY = totalTools <= 1
          ? svgH / 2
          : 150 + (tIdx * (svgH - 300)) / Math.max(totalTools - 1, 1);
        ax = (injOverride?.cx ?? INJECTOR_X) + INJECTOR_R;
        ay = injOverride?.cy ?? baseInjY;
      } else {
        // Action is not an injector (e.g. http-query, custom) — point to the endpoint left edge
        // so the arc clearly shows "this finding came from an action on this endpoint"
        ax = epCoord.cx - NODE_R - 8;
        ay = epCoord.cy;
      }

      // Source: finding item
      const ix = itemOverride?.cx ?? iPos.fx;
      const iy = itemOverride?.cy ?? iPos.fy;

      // Bezier control point: arc around the endpoint circle.
      // Push perpendicular to item→injector line, on the endpoint's side,
      // by enough so the arc clears the endpoint circle.
      const ldx = ax - ix; const ldy = ay - iy;
      const lineLen = Math.sqrt(ldx * ldx + ldy * ldy) || 1;
      const perpX = ldy / lineLen; const perpY = -ldx / lineLen;
      const midX = (ix + ax) / 2; const midY = (iy + ay) / 2;
      const epOnPerp = (epCoord.cx - midX) * perpX + (epCoord.cy - midY) * perpY;
      const arcDir = epOnPerp >= 0 ? 1 : -1;
      // arcDist/2 - |epOnPerp| > NODE_R + margin  →  arcDist > 2*|epOnPerp| + 2*NODE_R + 40
      const arcDist = Math.max(NODE_R * 3, 2 * Math.abs(epOnPerp) + 2 * NODE_R + 40);
      const cpx = midX + perpX * arcDir * arcDist;
      const cpy = midY + perpY * arcDir * arcDist;

      return {
        ix, iy,      // start: finding item
        ax, ay,      // end: injector diamond right edge (arrow points here)
        cpx, cpy,    // bezier control point arcing around endpoint
        findingColor: getStatusFill(actionStatus),
        actionLabel,
        actionId: f.actionId,
      };
    }
    return null;
  }, [selectedFindingId, allFindings, coordMap, findingsByType, nodes, dragOverrides,
    visibleInjectorGroups, finalNodeCoords]);

  const activePathEndpointIds = useMemo(() => {
    if (!selectedPathId) return null;
    const path = paths.find(p => p.path_id === selectedPathId);
    return path ? new Set(path.node_ids) : null;
  }, [selectedPathId, paths]);

  // ── Feed focus info (when action clicked in execution feed) ─────────────────
  const feedFocusInfo = useMemo(() => {
    if (!selectedActionNodeId) return null;
    for (const group of injectorGroups) {
      const contract = group.contracts.find(c => c.action.node_id === selectedActionNodeId);
      if (contract) {
        return {
          actionId: selectedActionNodeId,
          targetAssetId: contract.targetAssetId,
          tool: group.tool,
          groupColor: getStatusFill(contract.action.node_status),
        };
      }
    }
    return null;
  }, [selectedActionNodeId, injectorGroups]);

  useEffect(() => {
    if (feedFocusInfo?.targetAssetId) {
      setExpandedEndpoints(prev => new Set([...Array.from(prev), feedFocusInfo.targetAssetId]));
    }
  }, [feedFocusInfo?.targetAssetId]);

  useEffect(() => {
    setExpandedConnections(new Set());
  }, [selectedPathId]);

  // Clear focused endpoint when its cluster is collapsed or path changes
  useEffect(() => {
    if (!focusedEndpointId) return;
    if (!endpointClusters || expandedClusters.size === 0) {
      setFocusedEndpointId(null);
      return;
    }
    const stillInExpandedCluster = endpointClusters.some(
      c => expandedClusters.has(c.id) && c.nodes.some(n => n.node_id === focusedEndpointId),
    );
    if (!stillInExpandedCluster) setFocusedEndpointId(null);
  }, [expandedClusters, endpointClusters, focusedEndpointId]);

  useEffect(() => { setFocusedEndpointId(null); }, [selectedPathId]);

  // External focus request from parent (e.g. FindingsDrawer item click):
  // expand the cluster that contains the endpoint and set it as focused.
  useEffect(() => {
    if (!externalFocusRequest) return;
    const { endpointId, findingId } = externalFocusRequest;
    // Expand the containing cluster (if any)
    if (endpointClusters) {
      const cluster = endpointClusters.find((c) =>
        c.nodes.some((n) => n.node_id === endpointId),
      );
      if (cluster) {
        setExpandedClusters((prev) => {
          const next = new Set(prev);
          next.add(cluster.id);
          return next;
        });
      }
    }
    // Expand the endpoint's finding rings so it's visually "open"
    setExpandedEndpoints((prev) => {
      const next = new Set(prev);
      next.add(endpointId);
      return next;
    });
    // Focus endpoint (dims siblings in its cluster)
    setFocusedEndpointId(endpointId);
    // If a specific finding was requested, expand its type group and select it
    if (findingId) {
      // Finding ID format: ${assetId}::${typeKey}::${label}
      // typeKey: 'cred' | 'file' | 'port' | 'cve' | 'session'
      const typeKey = findingId.split('::')[1];
      const typeMap: Record<string, FindingType> = {
        cred: 'credential',
        file: 'file',
        port: 'port',
        cve: 'cve',
        session: 'session',
      };
      const findingType = typeMap[typeKey];
      if (findingType) {
        // Expand the type group for this endpoint
        setExpandedGroups((prev) => {
          const next = new Map(prev);
          const types = new Set(next.get(endpointId) ?? []);
          types.add(findingType);
          next.set(endpointId, types);
          return next;
        });
      }
      // Select the finding item (shows bezier arc to the producing action)
      setSelectedFindingId(findingId);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalFocusRequest?.seq]);
  const toggleEndpoint = useCallback((nodeId: string) => {
    setExpandedEndpoints((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
        // Also clear group expansions for this endpoint
        setExpandedGroups((g) => { const ng = new Map(g); ng.delete(nodeId); return ng; });
      } else {
        next.add(nodeId);
      }
      return next;
    });
    setSelectedFindingId(null);
    // Toggle focus: clicking same endpoint again clears focus, clicking a new one sets it
    setFocusedEndpointId(prev => prev === nodeId ? null : nodeId);
    onNodeClick?.(nodeId);
  }, [onNodeClick]);

  const toggleFindingGroup = useCallback((assetId: string, type: FindingType) => {
    setExpandedGroups((prev) => {
      const next = new Map(prev);
      const existing = next.get(assetId) ?? new Set<FindingType>();
      const updated = new Set(existing);
      if (updated.has(type)) updated.delete(type); else updated.add(type);
      next.set(assetId, updated);
      return next;
    });
    setSelectedFindingId(null);
  }, []);

  const selectFinding = useCallback((findingId: string, actionId?: string) => {
    setSelectedFindingId((prev) => {
      const next = prev === findingId ? null : findingId;
      // Focus the producing action in the left execution feed
      if (next && actionId) onNodeClick?.(actionId);
      return next;
    });
  }, [onNodeClick]);

  const toggleCluster = useCallback((clusterId: string) => {
    setExpandedClusters(prev => {
      const next = new Set(prev);
      if (next.has(clusterId)) next.delete(clusterId); else next.add(clusterId);
      return next;
    });
  }, []);

  const svgWidth  = useMemo(() => Math.max(1600, ...finalNodeCoords.map((c) => c.cx + 250)), [finalNodeCoords]);
  const svgHeight = useMemo(() => Math.max(900,  ...finalNodeCoords.map((c) => c.cy + 250)), [finalNodeCoords]);

  // Injector hexagon positions — one per unique tool, stacked on the left; draggable
  const injectorPositions = useMemo(() => {
    const posMap = new Map<string, { cx: number; cy: number }>();
    const tools = visibleInjectorGroups.map(g => g.tool);
    const total = tools.length;
    tools.forEach((tool, i) => {
      const key = `inj:${tool}`;
      const override = dragOverrides.get(key);
      if (override) {
        posMap.set(key, override);
      } else {
        const baseY = total === 1
          ? svgHeight / 2
          : 150 + (i * (svgHeight - 300)) / Math.max(total - 1, 1);
        posMap.set(key, { cx: INJECTOR_X, cy: baseY });
      }
    });
    return posMap;
  }, [visibleInjectorGroups, svgHeight, dragOverrides]);

  // Auto-zoom to center on the expanded cluster; restore default view on collapse.
  // Re-runs when finding groups expand so the viewport always fits all visible items.
  // Uses animated viewBox transition instead of instant jump.
  useEffect(() => {
    if (!endpointClusters) return;

    const targetVB = (() => {
      if (expandedClusters.size === 0) return { x: 0, y: 0, w: 1600, h: 900 };
      if (draggingIdRef.current) return null; // don't interrupt active drag
      const expandedId = Array.from(expandedClusters)[0];
      const cluster = endpointClusters.find(c => c.id === expandedId);
      if (!cluster) return null;
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const node of cluster.nodes) {
        const coord = finalNodeCoords.find(c => c.nodeId === node.node_id);
        if (!coord) continue;
        const hasOpenGroups = (expandedGroups.get(node.node_id)?.size ?? 0) > 0;
        const r = hasOpenGroups ? ITEM_ORBIT_R + FINDING_R + 30 : NODE_R + 30;
        minX = Math.min(minX, coord.cx - r);
        maxX = Math.max(maxX, coord.cx + r);
        minY = Math.min(minY, coord.cy - r);
        maxY = Math.max(maxY, coord.cy + r);
      }
      if (!isFinite(minX)) return null;
      const PAD = 60;
      return { x: minX - PAD, y: minY - PAD, w: maxX - minX + PAD * 2, h: maxY - minY + PAD * 2 };
    })();

    if (!targetVB) return;

    // Animate viewBox from current to target
    if (animViewFrameRef.current) cancelAnimationFrame(animViewFrameRef.current);
    const from = { ...viewBoxAnimFromRef.current };
    const duration = 550;
    const startTime = performance.now();
    const step = (now: number) => {
      const rawT = Math.min((now - startTime) / duration, 1);
      const t = 1 - (1 - rawT) ** 3; // ease-out cubic
      const cur = {
        x: from.x + (targetVB.x - from.x) * t,
        y: from.y + (targetVB.y - from.y) * t,
        w: from.w + (targetVB.w - from.w) * t,
        h: from.h + (targetVB.h - from.h) * t,
      };
      viewBoxAnimFromRef.current = cur;
      setViewBox(cur);
      if (rawT < 1) animViewFrameRef.current = requestAnimationFrame(step);
    };
    animViewFrameRef.current = requestAnimationFrame(step);
    return () => { if (animViewFrameRef.current) cancelAnimationFrame(animViewFrameRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expandedClusters, expandedGroups]);

  // Keep viewBoxAnimFromRef in sync so next animation always starts from current position.
  // The animation step closes over `from` at start time, so updating this ref during
  // animation doesn't affect ongoing animations — only the NEXT one.
  useEffect(() => { viewBoxAnimFromRef.current = viewBox; }, [viewBox]);

  // ── Auto-zoom to show full finding→action link when a finding is selected ────
  // When a finding item is clicked, the bezier arc to the injector diamond may be
  // off-screen (injector is far left). Animate the viewport to include both endpoints.
  useEffect(() => {
    if (!selectedFindingActionLink) return;
    const { ix, iy, ax, ay, cpx, cpy } = selectedFindingActionLink;
    // Bounding box: arc endpoints + control point (conservative outer bound of bezier)
    const PAD = 80;
    const minX = Math.min(ix, ax, cpx) - PAD;
    const maxX = Math.max(ix, ax, cpx) + PAD;
    const minY = Math.min(iy, ay, cpy) - PAD;
    const maxY = Math.max(iy, ay, cpy) + PAD;
    const targetVB = { x: minX, y: minY, w: maxX - minX, h: maxY - minY };

    if (animViewFrameRef.current) cancelAnimationFrame(animViewFrameRef.current);
    const from = { ...viewBoxAnimFromRef.current };
    const duration = 600;
    const startTime = performance.now();
    const step = (now: number) => {
      const rawT = Math.min((now - startTime) / duration, 1);
      const t = 1 - (1 - rawT) ** 3;
      const cur = {
        x: from.x + (targetVB.x - from.x) * t,
        y: from.y + (targetVB.y - from.y) * t,
        w: from.w + (targetVB.w - from.w) * t,
        h: from.h + (targetVB.h - from.h) * t,
      };
      viewBoxAnimFromRef.current = cur;
      setViewBox(cur);
      if (rawT < 1) animViewFrameRef.current = requestAnimationFrame(step);
    };
    animViewFrameRef.current = requestAnimationFrame(step);
    return () => { if (animViewFrameRef.current) cancelAnimationFrame(animViewFrameRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFindingActionLink]);

  // ── Node position animation (water-ripple effect) ────────────────────────────
  // When finalNodeCoords changes (e.g., collision avoidance when groups expand),
  // smoothly interpolate displayed positions from previous to new values.
  useEffect(() => {
    // First render: set immediately, no animation
    if (isFirstCoordRenderRef.current) {
      isFirstCoordRenderRef.current = false;
      animFromCoordsRef.current = finalNodeCoords;
      setDisplayCoords(finalNodeCoords);
      return;
    }

    // Check if any coordinate changed significantly
    const from = animFromCoordsRef.current;
    const to = finalNodeCoords;
    const anyMoved = to.some(c => {
      const f = from.find(p => p.nodeId === c.nodeId);
      return f && (Math.abs(c.cx - f.cx) > 0.5 || Math.abs(c.cy - f.cy) > 0.5);
    });

    if (!anyMoved) {
      animFromCoordsRef.current = to;
      setDisplayCoords(to);
      return;
    }

    if (animNodeFrameRef.current) cancelAnimationFrame(animNodeFrameRef.current);
    const duration = 600;
    const startTime = performance.now();

    const step = (now: number) => {
      const rawT = Math.min((now - startTime) / duration, 1);
      // Ease out cubic — fast start, gentle landing (water ripple feel)
      const t = 1 - (1 - rawT) ** 3;
      const interp = to.map(c => {
        const f = from.find(p => p.nodeId === c.nodeId);
        if (!f) return c;
        return { ...c, cx: f.cx + (c.cx - f.cx) * t, cy: f.cy + (c.cy - f.cy) * t };
      });
      setDisplayCoords(interp);
      if (rawT < 1) {
        animNodeFrameRef.current = requestAnimationFrame(step);
      } else {
        animFromCoordsRef.current = to;
      }
    };

    animNodeFrameRef.current = requestAnimationFrame(step);
    return () => { if (animNodeFrameRef.current) cancelAnimationFrame(animNodeFrameRef.current); };
  }, [finalNodeCoords]);

  // ── Connection badges (unified injector→EP and EP→EP) ───────────────────────

  interface ConnectionBadge {
    key: string;
    x1: number; y1: number;
    x2: number; y2: number;
    cpx: number; cpy: number;     // normal bezier control point
    ecpx: number; ecpy: number;   // expanded bezier control point (larger bow for readability)
    actions: AttackPathNode[];
    sourceLabel: string;
  }

  const allConnectionBadges = useMemo((): ConnectionBadge[] => {
    const badges: ConnectionBadge[] = [];

    // Helper: when cluster mode and cluster collapsed, use cluster ID instead of asset ID
    const resolveTargetId = (assetId: string): string => {
      if (!endpointClusters) return assetId;
      const cluster = endpointClusters.find(c => c.nodes.some(n => n.node_id === assetId));
      if (!cluster || expandedClusters.has(cluster.id)) return assetId;
      return cluster.id;
    };

    // --- INJECTOR→ENDPOINT connections ---
    const injectorPairs = new Map<string, { tool: string; targetAssetId: string; actions: AttackPathNode[] }>();
    for (const group of visibleInjectorGroups) {
      for (const { action, targetAssetId } of group.contracts) {
        const resolvedTarget = resolveTargetId(targetAssetId);
        const pairKey = `inj:${group.tool}:${resolvedTarget}`;
        if (!injectorPairs.has(pairKey)) {
          injectorPairs.set(pairKey, { tool: group.tool, targetAssetId: resolvedTarget, actions: [] });
        }
        injectorPairs.get(pairKey)!.actions.push(action);
      }
    }

    const INJECTOR_SOURCE_X = 80;
    const injectorPairList = Array.from(injectorPairs.values());
    injectorPairList.sort((a, b) => {
      const ya = coordMap.get(a.targetAssetId)?.cy ?? 0;
      const yb = coordMap.get(b.targetAssetId)?.cy ?? 0;
      return ya - yb;
    });
    injectorPairList.forEach(({ tool, targetAssetId, actions }) => {
      const epCoord = coordMap.get(targetAssetId);
      if (!epCoord) return;
      const injPos = injectorPositions.get(`inj:${tool}`);
      // Use injector hexagon position if available, else fall back to INJECTOR_SOURCE_X
      const srcX = injPos?.cx ?? INJECTOR_SOURCE_X;
      const srcY = injPos?.cy ?? epCoord.cy;
      // Start line from hexagon edge pointing toward the endpoint
      const dirX = epCoord.cx - srcX; const dirY = epCoord.cy - srcY;
      const dirLen = Math.sqrt(dirX * dirX + dirY * dirY) || 1;
      const x1 = srcX + (dirX / dirLen) * (injPos ? INJECTOR_R : 0);
      const y1 = srcY + (dirY / dirLen) * (injPos ? INJECTOR_R : 0);
      const x2 = epCoord.cx - NODE_R;
      const y2 = epCoord.cy;
      const mx = (x1 + x2) / 2;
      const my = (y1 + y2) / 2;
      const dx = x2 - x1; const dy = y2 - y1;
      const len = Math.sqrt(dx * dx + dy * dy) || 1;
      const cpOverride = dragOverrides.get(`badge:inj:${tool}:${targetAssetId}`);
      const cpx = cpOverride?.cx ?? (mx - (dy / len) * 30);
      const cpy = cpOverride?.cy ?? (my + (dx / len) * 30);
      const bowExp = Math.max(120, actions.length * 70);
      const ecpx = cpOverride?.cx ?? (mx - (dy / len) * bowExp);
      const ecpy = cpOverride?.cy ?? (my + (dx / len) * bowExp);
      badges.push({ key: `inj:${tool}:${targetAssetId}`, x1, y1, x2, y2, cpx, cpy, ecpx, ecpy, actions, sourceLabel: tool });
    });

    // --- EP→EP connections from pivot/lateral edges ---
    // Build from explicit pivot edges in the data; fall back to path-consecutive segments.
    const epToEpPairs = new Map<string, { srcId: string; tgtId: string; actions: AttackPathNode[] }>();

    // When a specific path is selected, only show EP→EP links within that path.
    // When all paths are shown (selectedPathId=null), show cross-group EP→EP links.
    const selectedPathNodeSet = selectedPathId
      ? new Set(paths.find(p => p.path_id === selectedPathId)?.node_ids ?? [])
      : null;

    // 1. Pivot edges (explicit EP→EP lateral movement)
    for (const edge of edges.filter(e => e.edge_type === 'pivot' || e.edge_type === 'lateral_movement')) {
      // If a specific path is selected, skip links that cross outside that path
      if (selectedPathNodeSet && (!selectedPathNodeSet.has(edge.edge_source) || !selectedPathNodeSet.has(edge.edge_target))) {
        continue;
      }
      const srcId = resolveTargetId(edge.edge_source);
      const tgtId = resolveTargetId(edge.edge_target);
      if (srcId === tgtId) continue;
      const pairKey = `ep:${srcId}:${tgtId}`;
      if (!epToEpPairs.has(pairKey)) {
        epToEpPairs.set(pairKey, { srcId, tgtId, actions: [] });
      }
      // Synthetic action node representing the lateral movement technique
      epToEpPairs.get(pairKey)!.actions.push({
        node_id: edge.edge_id,
        node_type: 'ACTION',
        node_label: edge.edge_label ?? 'Lateral Movement',
        node_status: 'undetected',
      });
    }

    for (const { srcId, tgtId, actions } of Array.from(epToEpPairs.values())) {
      const srcCoord = coordMap.get(srcId);
      const tgtCoord = coordMap.get(tgtId);
      if (!srcCoord || !tgtCoord) continue;
      const x1 = srcCoord.cx; const y1 = srcCoord.cy;
      const x2 = tgtCoord.cx; const y2 = tgtCoord.cy;
      const mx = (x1 + x2) / 2; const my = (y1 + y2) / 2;
      const dx = x2 - x1; const dy = y2 - y1;
      const len = Math.sqrt(dx * dx + dy * dy) || 1;
      const cpOverride = dragOverrides.get(`badge:ep:${srcId}:${tgtId}`);
      const cpx = cpOverride?.cx ?? (mx - (dy / len) * 30);
      const cpy = cpOverride?.cy ?? (my + (dx / len) * 30);
      const bowExp = Math.max(80, actions.length * 60);
      const ecpx = cpOverride?.cx ?? (mx - (dy / len) * bowExp);
      const ecpy = cpOverride?.cy ?? (my + (dx / len) * bowExp);
      badges.push({ key: `ep:${srcId}:${tgtId}`, x1, y1, x2, y2, cpx, cpy, ecpx, ecpy, actions, sourceLabel: 'lateral' });
    }

    return badges;
  }, [visibleInjectorGroups, injectorPositions, coordMap, endpointClusters, expandedClusters, dragOverrides, paths, edges, selectedPathId]);

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <div
      style={{ position: 'relative', width: '100%', height, overflow: 'hidden' }}
      onClick={() => { setSelectedFindingId(null); }}
    >
      <svg
        ref={svgRef}
        width="100%" height="100%"
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        style={{ display: 'block', cursor: isPanning ? 'grabbing' : draggingId ? 'grabbing' : 'grab' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      >
        <defs>
          {/* Arrow marker for pivot */}
          <marker id="pivot-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
            <path d="M0,0 L0,6 L9,3 z" fill="#F59E0B" />
          </marker>
          {/* Arrow marker for EP-to-EP (neutral, color matches connection) */}
          <marker id="ep-ep-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto">
            <path d="M0,0 L0,6 L9,3 z" fill="#94A3B8" opacity="0.8" />
          </marker>
          {/* Arrow marker for action-to-finding */}
          <marker id="atf-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
            <path d="M0,0 L0,6 L8,3 z" fill="#fff" opacity="0.7" />
          </marker>
        </defs>

        {/* ── Unified connection badges (injector→EP and EP→EP) ── */}
        {allConnectionBadges.map((badge) => {
          const { key, x1, y1, x2, y2, cpx, cpy, ecpx, ecpy, actions } = badge;
          const isExpanded = expandedConnections.has(key);
          // Use expanded bow control point when expanded for better diamond readability
          const usedCpx = isExpanded ? ecpx : cpx;
          const usedCpy = isExpanded ? ecpy : cpy;
          const badgeColor = getStatusFill(worstStatus(actions.map(a => a.node_status)));
          const bx = bezierPoint(0.5, x1, y1, usedCpx, usedCpy, x2, y2).x;
          const by = bezierPoint(0.5, x1, y1, usedCpx, usedCpy, x2, y2).y;
          const isEpToEp = key.startsWith('ep:');
          // Dim all connections in focus mode unless they connect to a focused endpoint
          const connFocusOpacity = (() => {
            // Endpoint focused within expanded cluster — dim connections not involving that endpoint
            if (focusedEndpointId) {
              const parts = key.split(':');
              if (key.startsWith('ep:')) {
                // ep:<src>:<tgt>
                return (parts[1] === focusedEndpointId || parts[2] === focusedEndpointId) ? 1 : 0.06;
              }
              // inj:<tool>:<targetId>
              return parts[parts.length - 1] === focusedEndpointId ? 1 : 0.06;
            }
            if (!focusedNodeIds) return 1;
            // key format: inj:<tool>:<targetId> or ep:<src>:<tgt>
            const parts = key.split(':');
            const targetId = parts[parts.length - 1];
            return focusedNodeIds.has(targetId) ? 1 : 0.06;
          })();
          const connOpacity = connFocusOpacity < 1 ? connFocusOpacity : feedFocusInfo
            ? (feedFocusInfo.actionId && actions.some(a => a.node_id === feedFocusInfo.actionId) ? 1 : (isEpToEp ? 0.45 : 0.1))
            : 1;

          return (
            <g key={`conn-${key}`} opacity={connOpacity}>
              {/* Base line — bows out more when expanded */}
              <path
                id={`conn-path-${key.replace(/:/g, '-')}`}
                d={`M ${x1} ${y1} Q ${usedCpx} ${usedCpy} ${x2} ${y2}`}
                fill="none"
                stroke={badgeColor}
                strokeWidth={isEpToEp ? 2 : 1.5}
                strokeOpacity={0.35}
                markerEnd={isEpToEp ? 'url(#ep-ep-arrow)' : undefined}
              />
              {/* Animated flow dot for EP-to-EP lateral movement links */}
              {isEpToEp && (
                <circle r="5" fill={badgeColor} opacity="0.85" style={{ pointerEvents: 'none' }}>
                  <animateMotion dur="1.8s" repeatCount="indefinite" rotate="auto">
                    <mpath href={`#conn-path-${key.replace(/:/g, '-')}`} />
                  </animateMotion>
                </circle>
              )}
              {/* Source label (tool name for injector connections — omitted since hexagon already shows it) */}
              {/* Count badge */}
              {actions.length > 0 && (
                <g
                  style={{ cursor: draggingId === `badge:${key}` ? 'grabbing' : 'pointer' }}
                  onMouseDown={(e) => {
                    if (e.button === 0) {
                      e.stopPropagation();
                      setIsPanning(false);
                      hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                      draggingIdRef.current = `badge:${key}`;
                      setDraggingId(`badge:${key}`);
                    }
                  }}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (!hasDraggedRef.current) toggleConnection(key);
                  }}
                >
                  <circle cx={bx} cy={by} r={13} fill="rgba(10,14,25,0.92)" stroke={badgeColor} strokeWidth={1.5} />
                  <text x={bx} y={by + 4} textAnchor="middle" fontSize={9} fill="#fff" fontWeight={700}
                    style={{ pointerEvents: 'none', userSelect: 'none' }}>
                    {isExpanded ? '−' : `+${actions.length}`}
                  </text>
                </g>
              )}
              {/* Expanded action diamonds — each on its own fan arc for clear separation */}
              {isExpanded && (() => {
                const N = actions.length;
                const lineDx = x2 - x1; const lineDy = y2 - y1;
                const lineLen = Math.sqrt(lineDx * lineDx + lineDy * lineDy) || 1;
                const midX = (x1 + x2) / 2; const midY = (y1 + y2) / 2;
                // Base bow + per-action perpendicular offset so arcs fan out
                const baseBow = Math.max(120, N * 70);
                const FAN_STEP = 65; // px between adjacent fan arcs
                return actions.map((action, j) => {
                  const fanOffset = (j - (N - 1) / 2) * FAN_STEP;
                  const jCpx = midX - (lineDy / lineLen) * (baseBow + fanOffset);
                  const jCpy = midY + (lineDx / lineLen) * (baseBow + fanOffset);
                  const bp = bezierPoint(0.5, x1, y1, jCpx, jCpy, x2, y2);
                  const dColor = getStatusFill(action.node_status);
                  const labelText = action.node_label.slice(0, 18);
                  const labelWidth = Math.max(labelText.length * 4.8 + 10, 55);
                  return (
                    <g key={`badge-diamond-${action.node_id}`}
                      style={{ cursor: 'pointer' }}
                      onClick={(e) => { e.stopPropagation(); onDetailClick?.(action.node_id); }}
                    >
                      {/* Fan arc: source → diamond → target */}
                      <path d={`M ${x1} ${y1} Q ${jCpx} ${jCpy} ${bp.x} ${bp.y}`}
                        fill="none" stroke={dColor} strokeWidth={1.2} strokeOpacity={0.45} />
                      <path d={`M ${bp.x} ${bp.y} Q ${jCpx} ${jCpy} ${x2} ${y2}`}
                        fill="none" stroke={dColor} strokeWidth={1.2} strokeOpacity={0.45}
                        markerEnd="url(#path-arrow)" />
                      {/* Diamond */}
                      <polygon
                        points={`${bp.x},${bp.y - 10} ${bp.x + 10},${bp.y} ${bp.x},${bp.y + 10} ${bp.x - 10},${bp.y}`}
                        fill={`${dColor}33`} stroke={dColor} strokeWidth={1.5}
                        style={{ pointerEvents: 'none' }}
                      />
                      {/* Label */}
                      <rect x={bp.x - labelWidth / 2} y={bp.y + 13} width={labelWidth} height={14} rx={3}
                        fill="rgba(10,14,25,0.92)" stroke={dColor} strokeWidth={0.6} strokeOpacity={0.5}
                        style={{ pointerEvents: 'none' }} />
                      <text x={bp.x} y={bp.y + 23} textAnchor="middle" fontSize={7.5} fill={dColor} fontWeight={600}
                        style={{ pointerEvents: 'none', userSelect: 'none' }}>
                        {labelText}
                      </text>
                    </g>
                  );
                });
              })()}
            </g>
          );
        })}

        {/* ── Action-to-finding link ── */}
        {selectedFindingActionLink && (() => {
          const { ix, iy, ax, ay, cpx, cpy, findingColor, actionLabel, actionId } = selectedFindingActionLink;
          // Bezier midpoint for label: t=0.5 on quadratic bezier
          const mx = 0.25 * ix + 0.5 * cpx + 0.25 * ax;
          const my = 0.25 * iy + 0.5 * cpy + 0.25 * ay;
          const labelWidth = Math.min(Math.max(actionLabel.length * 5.5, 50), 160);
          return (
            <g
              style={{ cursor: 'pointer' }}
              onClick={(e) => { e.stopPropagation(); onDetailClick?.(actionId); }}
            >
              {/* Invisible wider hit area for easier clicking */}
              <path d={`M ${ix} ${iy} Q ${cpx} ${cpy} ${ax} ${ay}`}
                fill="none" stroke="transparent" strokeWidth={12} />
              {/* Visible bezier arc from finding item → injector diamond */}
              <path d={`M ${ix} ${iy} Q ${cpx} ${cpy} ${ax} ${ay}`}
                fill="none" stroke={findingColor} strokeWidth={2} strokeOpacity={0.85}
                markerEnd="url(#atf-arrow)" style={{ pointerEvents: 'none' }} />
              <rect x={mx - labelWidth / 2} y={my - 9} width={labelWidth} height={16} rx={8}
                fill="rgba(10,14,25,0.95)" stroke={findingColor} strokeWidth={0.8} strokeOpacity={0.6} />
              <text x={mx} y={my + 4} textAnchor="middle" fontSize={7.5} fill={findingColor} fontWeight={600}
                style={{ pointerEvents: 'none', userSelect: 'none' }}>{actionLabel}</text>
            </g>
          );
        })()}

        {/* ── Finding item nodes (level 2) ── */}
        {visibleAssetNodes.map((asset) => {
          // Hide findings for endpoints in collapsed clusters
          if (endpointClusters) {
            const myClusters = endpointClusters.filter(c => c.nodes.some(n => n.node_id === asset.node_id));
            if (myClusters.length > 0 && !myClusters.some(c => expandedClusters.has(c.id))) return null;
          }
          const epCoord = coordMap.get(asset.node_id);
          if (!epCoord || !expandedEndpoints.has(asset.node_id)) return null;
          const byType = findingsByType.get(asset.node_id);
          if (!byType) return null;
          const typeKeys = FINDING_TYPE_ORDER.filter((t) => (byType.get(t)?.length ?? 0) > 0);
          const groupPositions = computeGroupPositions(epCoord.cx, epCoord.cy, typeKeys.length, GROUP_ORBIT);
          const expandedTypeGroups = expandedGroups.get(asset.node_id) ?? new Set<FindingType>();
          const isFocusedEndpoint = !feedFocusInfo || feedFocusInfo.targetAssetId === asset.node_id;

          return typeKeys.map((type, gi) => {
            if (!expandedTypeGroups.has(type)) return null;
            const gPos = groupPositions[gi];
            if (!gPos) return null;
            // Respect group drag override for edge start
            const grpDragKey = `grp:${asset.node_id}:${type}`;
            const grpOverride = dragOverrides.get(grpDragKey);
            const ggfx = grpOverride?.cx ?? gPos.fx;
            const ggfy = grpOverride?.cy ?? gPos.fy;
            const items = byType.get(type) ?? [];
            const itemPositions = computeItemPositions(epCoord.cx, epCoord.cy, gPos.angle, items.length, ITEM_ORBIT_R);
            const meta = FINDING_META[type];

            return items.map((item, ii) => {
              const iPos = itemPositions[ii];
              if (!iPos) return null;
              const itemDragKey = `item:${item.id}`;
              const itemOverride = dragOverrides.get(itemDragKey);
              const ifx = itemOverride?.cx ?? iPos.fx;
              const ify = itemOverride?.cy ?? iPos.fy;
              const isSelected = selectedFindingId === item.id;
              const actionNode = nodes.find(n => n.node_id === item.actionId);
              const itemStatus = actionNode?.node_status ?? asset.node_status;
              const itemFill   = getStatusFill(itemStatus);
              const itemStroke = getStatusStroke(itemStatus);
              return (
                <g key={`item-${item.id}`} opacity={isFocusedEndpoint ? 1 : 0.15}>
                  {/* Edge from group node to item */}
                  <line x1={ggfx} y1={ggfy} x2={ifx} y2={ify}
                    stroke={itemStroke} strokeWidth={1.2} strokeOpacity={0.5} />
                  {/* Item node */}
                  <g style={{ cursor: draggingId === itemDragKey ? 'grabbing' : 'pointer' }}
                    onMouseDown={(e) => {
                      if (e.button === 0) {
                        e.stopPropagation();
                        setIsPanning(false);
                        hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                        draggingIdRef.current = itemDragKey;
                        setDraggingId(itemDragKey);
                      }
                    }}
                    onClick={(e) => { e.stopPropagation(); if (!hasDraggedRef.current) selectFinding(item.id, item.actionId); }}>
                    <circle cx={ifx} cy={ify} r={FINDING_R + (isSelected ? 4 : 0)}
                      fill={itemFill} stroke={itemStroke} strokeWidth={isSelected ? 2.5 : 1.8}
                      opacity={0.92} />
                    <text x={ifx} y={ify - 2} textAnchor="middle" fontSize={11}
                      style={{ userSelect: 'none', pointerEvents: 'none' }}>{meta.icon}</text>
                    <text x={ifx} y={ify + 11} textAnchor="middle" fontSize={6}
                      fill="#fff" fontWeight={600}
                      style={{ userSelect: 'none', pointerEvents: 'none' }}>{item.label}</text>
                    {/* Agent badge — small colored dot in top-right of finding circle (V7) */}
                    {item.agentType && (() => {
                      const agentMeta = AGENT_META[item.agentType];
                      const radius = FINDING_R + (isSelected ? 4 : 0);
                      const br = radius * 0.55;
                      const bx = ifx + radius * 0.7;
                      const by = ify - radius * 0.7;
                      return (
                        <g style={{ pointerEvents: 'none' }}>
                          <circle cx={bx} cy={by} r={br} fill={agentMeta.color} stroke="rgba(10,14,25,1)" strokeWidth={1.2} />
                          <text x={bx} y={by + 3.5} textAnchor="middle" fontSize={5.5} fontWeight={800} fill="#fff"
                            style={{ userSelect: 'none' }}>{agentMeta.abbr}</text>
                        </g>
                      );
                    })()}
                  </g>
                </g>
              );
            });
          });
        })}

        {/* ── Finding TYPE group nodes (level 1) ── */}
        {visibleAssetNodes.map((asset) => {
          // Hide findings for endpoints in collapsed clusters
          if (endpointClusters) {
            const myClusters = endpointClusters.filter(c => c.nodes.some(n => n.node_id === asset.node_id));
            if (myClusters.length > 0 && !myClusters.some(c => expandedClusters.has(c.id))) return null;
          }
          const epCoord = coordMap.get(asset.node_id);
          if (!epCoord || !expandedEndpoints.has(asset.node_id)) return null;
          const byType = findingsByType.get(asset.node_id);
          if (!byType) return null;
          const typeKeys = FINDING_TYPE_ORDER.filter((t) => (byType.get(t)?.length ?? 0) > 0);
          const groupPositions = computeGroupPositions(epCoord.cx, epCoord.cy, typeKeys.length, GROUP_ORBIT);
          const expandedTypeGroups = expandedGroups.get(asset.node_id) ?? new Set<FindingType>();
          const isFocusedEndpoint = !feedFocusInfo || feedFocusInfo.targetAssetId === asset.node_id;

          return typeKeys.map((type, gi) => {
            const gPos = groupPositions[gi];
            if (!gPos) return null;
            const meta = FINDING_META[type];
            const items = byType.get(type) ?? [];
            const isGroupExpanded = expandedTypeGroups.has(type);
            const grpDragKey = `grp:${asset.node_id}:${type}`;
            const grpOverride = dragOverrides.get(grpDragKey);
            const gfx = grpOverride?.cx ?? gPos.fx;
            const gfy = grpOverride?.cy ?? gPos.fy;

            // Color group by worst status of contained findings; fallback to asset status
            const groupStatus = worstStatus(items.map(item => nodes.find(n => n.node_id === item.actionId)?.node_status ?? asset.node_status));
            const groupFill   = getStatusFill(groupStatus);
            const groupStroke = getStatusStroke(groupStatus);

            return (
              <g key={`grp-${asset.node_id}-${type}`} opacity={isFocusedEndpoint ? 1 : 0.15}>
                {/* Edge from endpoint to group */}
                <line x1={epCoord.cx} y1={epCoord.cy} x2={gfx} y2={gfy}
                  stroke={groupStroke} strokeWidth={1.5} strokeOpacity={0.6} />
                {/* Edge label */}
                <rect x={(epCoord.cx + gfx) / 2 - 38} y={(epCoord.cy + gfy) / 2 - 8} width={76} height={14} rx={7}
                  fill="rgba(10,14,25,0.9)" stroke={groupStroke} strokeWidth={0.7} strokeOpacity={0.5} />
                <text x={(epCoord.cx + gfx) / 2} y={(epCoord.cy + gfy) / 2 + 4}
                  textAnchor="middle" fontSize={7.5} fill={groupStroke} fontWeight={600}
                  style={{ pointerEvents: 'none', userSelect: 'none' }}>{meta.groupLabel}</text>
                {/* Group node */}
                <g style={{ cursor: draggingId === grpDragKey ? 'grabbing' : 'pointer' }}
                  onMouseDown={(e) => {
                    if (e.button === 0) {
                      e.stopPropagation();
                      setIsPanning(false);
                      hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                      draggingIdRef.current = grpDragKey;
                      setDraggingId(grpDragKey);
                    }
                  }}
                  onClick={(e) => { e.stopPropagation(); if (!hasDraggedRef.current) toggleFindingGroup(asset.node_id, type); }}>
                  <circle cx={gfx} cy={gfy} r={GROUP_R + (isGroupExpanded ? 4 : 0)}
                    fill={`${groupFill}22`} stroke={groupStroke} strokeWidth={isGroupExpanded ? 2.5 : 1.8} />
                  <text x={gfx} y={gfy - 2} textAnchor="middle" fontSize={14}
                    style={{ userSelect: 'none', pointerEvents: 'none' }}>{meta.icon}</text>
                  {/* Count badge */}
                  {items.length > 1 && (
                    <>
                      <circle cx={gfx + GROUP_R - 5} cy={gfy - GROUP_R + 5} r={9}
                        fill={groupStroke} stroke="rgba(10,14,25,1)" strokeWidth={1.5} />
                      <text x={gfx + GROUP_R - 5} y={gfy - GROUP_R + 9} textAnchor="middle"
                        fontSize={8} fontWeight={700} fill="#fff"
                        style={{ userSelect: 'none', pointerEvents: 'none' }}>{items.length}</text>
                    </>
                  )}
                  {/* Expand hint */}
                  <text x={gfx} y={gfy + GROUP_R + 13} textAnchor="middle" fontSize={8}
                    fill="rgba(255,255,255,0.4)"
                    style={{ userSelect: 'none', pointerEvents: 'none' }}>
                    {isGroupExpanded ? '▾' : '▸'}
                  </text>
                </g>
              </g>
            );
          });
        })}

        {/* ── Endpoint clusters (when >10 endpoints) ── */}
        {endpointClusters && endpointClusters.map(cluster => {
          // Use display center (may be pushed away from expanded cluster)
          const center = clusterDisplayCenters.get(cluster.id);
          if (!center) return null;
          const { cx, cy } = center;
          const isExpanded = expandedClusters.has(cluster.id);
          const pathColor = (cluster as { pathColor?: string }).pathColor ?? '#888';
          const pathName = (cluster as { pathName?: string }).pathName ?? cluster.id;
          const shortName = pathName.length > 14 ? pathName.slice(0, 13) + '…' : pathName;
          // Focus mode: dim clusters not being expanded
          const clusterOpacity = focusedNodeIds && !isExpanded ? 0.18 : 1;

          if (isExpanded) {
            return (
              <g key={cluster.id} opacity={clusterOpacity}>
                <circle cx={cx} cy={cy} r={CLUSTER_R + 20}
                  fill="none" stroke={pathColor} strokeWidth={1.2} strokeOpacity={0.25}
                  strokeDasharray="6 4" style={{ pointerEvents: 'none' }} />
                {/* Collapse button */}
                <g style={{ cursor: 'pointer' }}
                  onClick={(e) => { e.stopPropagation(); toggleCluster(cluster.id); }}>
                  <circle cx={cx} cy={cy} r={20}
                    fill="rgba(14,18,32,0.92)" stroke={pathColor} strokeWidth={1.5} />
                  <text x={cx} y={cy - 4} textAnchor="middle" fontSize={6.5} fill={pathColor}
                    fontWeight={700} style={{ pointerEvents: 'none', userSelect: 'none' }}>
                    {shortName.slice(0, 10)}
                  </text>
                  <text x={cx} y={cy + 8} textAnchor="middle" fontSize={9} fill={pathColor}
                    style={{ pointerEvents: 'none', userSelect: 'none' }}>▾</text>
                </g>
              </g>
            );
          }

          const isDragging = draggingId === cluster.id;
          return (
            <g key={cluster.id} opacity={clusterOpacity}
              style={{ cursor: isDragging ? 'grabbing' : 'grab' }}
              onMouseDown={(e) => {
                if (e.button === 0) {
                  e.stopPropagation();
                  setIsPanning(false);
                  hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                  draggingIdRef.current = cluster.id;
                  setDraggingId(cluster.id);
                }
              }}
              onClick={(e) => {
                e.stopPropagation();
                if (!hasDraggedRef.current) toggleCluster(cluster.id);
              }}
            >
              <circle cx={cx} cy={cy} r={CLUSTER_R} fill="rgba(14,18,32,0.92)" stroke={pathColor} strokeWidth={2.5} />
              <text x={cx} y={cy - 12} textAnchor="middle" fontSize={10} fontWeight={800} fill={pathColor}
                style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {shortName}
              </text>
              <text x={cx} y={cy + 3} textAnchor="middle" fontSize={9} fill="rgba(255,255,255,0.65)"
                style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {cluster.nodes.length} endpoint{cluster.nodes.length !== 1 ? 's' : ''}
              </text>
              <text x={cx} y={cy + 16} textAnchor="middle" fontSize={8} fill="rgba(255,255,255,0.35)"
                style={{ pointerEvents: 'none', userSelect: 'none' }}>
                ▸ expand
              </text>
            </g>
          );
        })}

        {/* ── Injector hexagons ── */}
        {visibleInjectorGroups.map((group) => {
          const key = `inj:${group.tool}`;
          const pos = injectorPositions.get(key);
          if (!pos) return null;
          const { cx, cy } = pos;
          const pts = hexPoints(cx, cy, INJECTOR_R);
          const allActions = group.contracts.map(c => c.action);
          const injStatus = worstStatus(allActions.map(a => a.node_status));
          const hexColor = getStatusFill(injStatus === 'pending' ? undefined : injStatus);
          const hexOpacity = focusedNodeIds
            ? 0.15
            : feedFocusInfo
              ? (feedFocusInfo.tool === group.tool ? 1 : 0.15)
              : 1;
          const isDragging = draggingId === key;
          return (
            <g key={key} opacity={hexOpacity}>
              <polygon
                points={pts}
                fill="rgba(14,18,32,0.92)"
                stroke={hexColor}
                strokeWidth={2}
                style={{ cursor: isDragging ? 'grabbing' : 'grab' }}
                onMouseDown={(e) => {
                  if (e.button === 0) {
                    e.stopPropagation();
                    setIsPanning(false);
                    hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                    draggingIdRef.current = key;
                    setDraggingId(key);
                  }
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  if (!hasDraggedRef.current) {
                    // Toggle all connections for this injector tool
                    const toolKeys = allConnectionBadges
                      .filter(b => b.key.startsWith(`inj:${group.tool}:`))
                      .map(b => b.key);
                    setExpandedConnections(prev => {
                      const next = new Set(prev);
                      const allExp = toolKeys.every(k => next.has(k));
                      for (const k of toolKeys) { if (allExp) next.delete(k); else next.add(k); }
                      return next;
                    });
                  }
                }}
              />
              <text x={cx} y={cy - 3} textAnchor="middle" fontSize={8} fontWeight={700}
                fill={hexColor} style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {group.tool.toUpperCase().slice(0, 10)}
              </text>
              <text x={cx} y={cy + 8} textAnchor="middle" fontSize={6.5}
                fill="rgba(255,255,255,0.45)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                INJECTOR
              </text>
            </g>
          );
        })}

        {/* ── Endpoint nodes ── */}
        {visibleAssetNodes.map((asset) => {
          // In cluster mode, only show endpoints that are in an expanded cluster
          if (endpointClusters) {
            const myClusters = endpointClusters.filter(c => c.nodes.some(n => n.node_id === asset.node_id));
            if (myClusters.length > 0 && !myClusters.some(c => expandedClusters.has(c.id))) return null;
          }
          const coord = coordMap.get(asset.node_id);
          if (!coord) return null;
          const { cx, cy } = coord;
          const isExpanded = expandedEndpoints.has(asset.node_id);
          const findings = allFindings.get(asset.node_id) ?? [];
          const count = findings.length;
          const endpointOpacity = (() => {
            // Focused endpoint in same expanded cluster → dim siblings
            if (focusedEndpointId && focusedEndpointId !== asset.node_id && endpointClusters && expandedClusters.size > 0) {
              const focusedCluster = endpointClusters.find(
                c => expandedClusters.has(c.id) && c.nodes.some(n => n.node_id === focusedEndpointId),
              );
              const thisCluster = endpointClusters.find(
                c => expandedClusters.has(c.id) && c.nodes.some(n => n.node_id === asset.node_id),
              );
              if (focusedCluster && thisCluster && focusedCluster.id === thisCluster.id) return 0.12;
            }
            if (focusedNodeIds) return focusedNodeIds.has(asset.node_id) ? 1 : 0.12;
            if (feedFocusInfo) return feedFocusInfo.targetAssetId === asset.node_id ? 1 : 0.15;
            return 1;
          })();

          // Color based on worst action status; grey = no actions run
          const epActionNodes = edges
            .filter(e => e.edge_type === 'asset_link' && e.edge_target === asset.node_id)
            .map(e => nodes.find(n => n.node_id === e.edge_source))
            .filter(Boolean) as AttackPathNode[];
          const epStatus = worstStatus(epActionNodes.map(a => a.node_status));
          const nodeColor = getStatusFill(epStatus === 'pending' ? undefined : epStatus);
          const nodeStroke = getStatusStroke(epStatus === 'pending' ? undefined : epStatus);

          return (
            <g key={`ep-${asset.node_id}`} opacity={endpointOpacity}>
              {/* Expanded glow ring — status color */}
              {isExpanded && (
                <circle cx={cx} cy={cy} r={NODE_R + 12}
                  fill={`${nodeColor}12`} stroke={nodeColor} strokeWidth={1.5} strokeOpacity={0.45} />
              )}
              {/* Main node — handles both drag and click */}
              <circle cx={cx} cy={cy} r={NODE_R}
                fill="rgba(14,18,32,0.92)" stroke={nodeColor} strokeWidth={2.5}
                style={{ cursor: draggingId === asset.node_id ? 'grabbing' : 'grab' }}
                onMouseDown={(e) => {
                  if (e.button === 0) {
                    e.stopPropagation();
                    setIsPanning(false);
                    hasDraggedRef.current = false; dragStartClientRef.current = { x: e.clientX, y: e.clientY };
                    draggingIdRef.current = asset.node_id;
                    setDraggingId(asset.node_id);
                  }
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  if (!hasDraggedRef.current) toggleEndpoint(asset.node_id);
                }}
                onMouseEnter={(e) => { e.stopPropagation(); showTooltip(asset, e.clientX, e.clientY); }}
                onMouseMove={(e) => { e.stopPropagation(); showTooltip(asset, e.clientX, e.clientY); }}
                onMouseLeave={() => hideTooltip()} />
              {/* Labels */}
              <text x={cx} y={cy - 4} textAnchor="middle" fontSize={9} fontWeight={700}
                fill="#e2e8f0" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {(asset.node_hostname ?? asset.node_label).slice(0, 14)}
              </text>
              <text x={cx} y={cy + 9} textAnchor="middle" fontSize={8}
                fill="#94a3b8" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {asset.node_ip ?? ''}
              </text>
              {/* Finding count badge */}
              {count > 0 && !isExpanded && (
                <g style={{ pointerEvents: 'none' }}>
                  <circle cx={cx + NODE_R - 8} cy={cy - NODE_R + 8} r={12}
                    fill={nodeColor} stroke={nodeStroke} strokeWidth={1.5} />
                  <text x={cx + NODE_R - 8} y={cy - NODE_R + 12} textAnchor="middle"
                    fontSize={9} fontWeight={700} fill="#fff"
                    style={{ userSelect: 'none' }}>+{count}</text>
                </g>
              )}
              {/* Collapse indicator */}
              {isExpanded && (
                <g style={{ pointerEvents: 'none' }}>
                  <circle cx={cx + NODE_R - 8} cy={cy - NODE_R + 8} r={10}
                    fill={`${nodeColor}25`} stroke={nodeColor} strokeWidth={1.2} />
                  <text x={cx + NODE_R - 8} y={cy - NODE_R + 13} textAnchor="middle"
                    fontSize={12} fill={nodeColor} style={{ userSelect: 'none' }}>−</text>
                </g>
              )}
              {/* Entry point ring */}
              {asset.node_is_entry_point && (
                <circle cx={cx} cy={cy} r={NODE_R + 6} fill="none"
                  stroke="#fff" strokeWidth={1.2} strokeOpacity={0.5}
                  style={{ pointerEvents: 'none' }} />
              )}
              {/* Agent badges below node label (V7) */}
              {(asset.node_agents ?? []).length > 0 && (
                <g style={{ pointerEvents: 'none' }}>
                  {(asset.node_agents ?? []).map((agentType, ai, arr) => {
                    const agentMeta = AGENT_META[agentType];
                    const total = arr.length;
                    const spacing = 14;
                    const startX = cx - ((total - 1) * spacing) / 2;
                    const bx = startX + ai * spacing;
                    const by = cy + NODE_R + 10;
                    return (
                      <g key={agentType}>
                        <circle cx={bx} cy={by} r={6} fill={agentMeta.color} stroke="rgba(10,14,25,0.9)" strokeWidth={1} />
                        <text x={bx} y={by + 3} textAnchor="middle" fontSize={5} fontWeight={800} fill="#fff"
                          style={{ userSelect: 'none' }}>{agentMeta.abbr}</text>
                      </g>
                    );
                  })}
                </g>
              )}
            </g>
          );
        })}
      </svg>

      {/* ── Endpoint tooltip ── */}
      {tooltip && (
        <div style={{
          position: 'fixed', left: tooltip.x + 15, top: tooltip.y + 10,
          pointerEvents: 'auto', zIndex: 9999,
          backgroundColor: 'rgba(15,15,25,0.97)',
          border: '1px solid rgba(255,255,255,0.15)',
          borderRadius: 6, padding: '8px 12px', minWidth: 200, maxWidth: 280,
          boxShadow: '0 4px 20px rgba(0,0,0,0.6)',
        }}
          onMouseEnter={() => { if (tooltipHideTimer.current) clearTimeout(tooltipHideTimer.current); }}
          onMouseLeave={() => hideTooltip(0)}>
          <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#fff' }}>
            {tooltip.node.node_hostname ?? tooltip.node.node_label}
          </div>
          {tooltip.node.node_ip && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>IP</span>
              <span style={{ fontSize: 10, fontFamily: 'monospace', color: '#64b5f6' }}>{tooltip.node.node_ip}</span>
            </div>
          )}
          {tooltip.node.node_platform && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Platform</span>
              <span style={{ fontSize: 10, opacity: 0.8 }}>{tooltip.node.node_platform}</span>
            </div>
          )}
          {tooltip.node.node_zone && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Zone</span>
              <span style={{ fontSize: 10, opacity: 0.75 }}>{tooltip.node.node_zone}</span>
            </div>
          )}
          {tooltip.node.node_is_entry_point && (
            <div style={{ marginTop: 4, fontSize: 10, color: '#ffd54f' }}>★ Entry point</div>
          )}
          {tooltip.node.node_is_pivot && (
            <div style={{ marginTop: 2, fontSize: 10, color: '#ff9800' }}>↔ Pivot node</div>
          )}
          {/* Agent badges (V7) */}
          {(tooltip.node.node_agents ?? []).length > 0 && (
            <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span style={{ fontSize: 9, opacity: 0.45, textTransform: 'uppercase', letterSpacing: 0.5 }}>Agents</span>
              <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {(tooltip.node.node_agents ?? []).map(agentType => {
                  const m = AGENT_META[agentType];
                  return (
                    <span key={agentType} style={{
                      fontSize: 9, fontWeight: 700, color: m.color,
                      background: m.bg, border: `1px solid ${m.color}55`,
                      borderRadius: 3, padding: '1px 5px',
                    }}>{m.abbr} {m.name}</span>
                  );
                })}
              </div>
            </div>
          )}
          {/* Details button */}
          <div style={{ marginTop: 8, textAlign: 'right' }}>
            <span
              style={{ fontSize: 10, fontWeight: 700, color: '#3B82F6', cursor: 'pointer', padding: '3px 10px', borderRadius: 4, backgroundColor: 'rgba(59,130,246,0.12)', border: '1px solid rgba(59,130,246,0.35)' }}
              onClick={(e) => { e.stopPropagation(); setTooltip(null); onDetailClick?.(tooltip.node.node_id); }}>
              Details →
            </span>
          </div>
        </div>
      )}

      {/* ── Zoom controls ── */}
      <div style={{
        position: 'absolute', bottom: 16, right: 16,
        display: 'flex', flexDirection: 'column', gap: 4,
        backgroundColor: 'rgba(15,15,25,0.85)',
        border: '1px solid rgba(255,255,255,0.12)',
        borderRadius: 8, padding: 4,
        zIndex: 20, pointerEvents: 'auto',
      }}>
        <IconButton size="small" onClick={() => zoomFn(0.8)} sx={{ color: '#fff' }}><ZoomInIcon fontSize="small" /></IconButton>
        <IconButton size="small" onClick={() => setViewBox({ x: 0, y: 0, w: svgWidth, h: svgHeight })} sx={{ color: '#fff' }}><FitIcon fontSize="small" /></IconButton>
        <IconButton size="small" onClick={() => zoomFn(1.25)} sx={{ color: '#fff' }}><ZoomOutIcon fontSize="small" /></IconButton>
      </div>
    </div>
  );
};

export default AttackPathGraphV7;
