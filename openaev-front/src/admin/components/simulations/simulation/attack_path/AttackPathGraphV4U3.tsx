/**
 * AttackPathGraphV4U — Attacker Origin Map  (Updated Variant 4)
 *
 * Each injector TOOL (nmap, netexec, nuclei) is rendered as its own distinct
 * hexagonal node on the left of the canvas.
 *
 * Each INJECTOR CONTRACT (individual action run by that tool) gets its own
 * smaller circle node.  Contracts are COLLAPSED under their parent injector
 * by default; click the injector node to toggle expansion.
 *
 * When collapsed:
 *   – Aggregate dashed recon arrows run from the injector node to every
 *     endpoint it targeted.
 *
 * When expanded:
 *   – Thin connector lines join the injector to each contract node.
 *   – Each contract node shows its own dashed recon arrow to its specific
 *     target endpoint.
 *
 * The rest of the canvas (path lines, endpoint nodes, edge-reason badges,
 * legend) is identical to Variant 1.
 */

import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  type AttackPathNode,
  type AttackPathEdge,
  type AttackPathDefinition,
  getActionsForAssetFull,
  getNodeStatus,
  getPathOutcomeColor,
  STATUS_COLORS,
} from './attackPathUtils';

// ─────────────────────────────────────────────────────────────────────────────
// Props
// ─────────────────────────────────────────────────────────────────────────────

interface Props {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  paths: AttackPathDefinition[];
  selectedActionNodeId: string | null;
  onNodeClick?: (assetId: string) => void;
  onPathClick?: (assetNodeIds: string[]) => void;
  onBadgeClick?: (destAssetId: string) => void;
  onLegendPathSelect?: (path: AttackPathDefinition | null) => void;
  selectedPathId?: string | null;
  height?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const NODE_R          = 36;   // endpoint circle radius
const CONTRACT_R      = 22;   // contract (action) circle radius
const ARROW_SIZE      = 8;
const ARROW_LEN       = 13;

const INJECTOR_X      = 100;  // cx of injector hex nodes
const CONTRACT_X      = 262;  // cx of expanded contract nodes
const CONTRACT_SPACING = 55;  // vertical pitch between contract nodes
const CONTRACT_MAX    = 5;    // max contracts visible when expanded ("+N more" for the rest)
const INJECTOR_SPACING = 210;
const INJECTOR_R      = 38; // base vertical distance between injector nodes

const GN_W  = 220;  // group node width
const GN_H  = 170;  // group node height
const GN_R  = 12;   // group node corner radius

// ─────────────────────────────────────────────────────────────────────────────
// Tool colour palette
// ─────────────────────────────────────────────────────────────────────────────

const TOOL_COLORS: Record<string, string> = {
  nmap:     '#00bcd4',  // cyan
  netexec:  '#ff9800',  // orange
  nuclei:   '#ab47bc',  // purple
  injector: '#607d8b',  // blue-grey fallback
};

function getToolColor(tool: string): string {
  return TOOL_COLORS[tool] ?? TOOL_COLORS.injector;
}

function hexPoints(cx: number, cy: number, r: number): string {
  return Array.from({ length: 6 }, (_, i) => {
    const angle = (i * 60 - 30) * Math.PI / 180;
    return `${cx + r * Math.cos(angle)},${cy + r * Math.sin(angle)}`;
  }).join(' ');
}

// ─────────────────────────────────────────────────────────────────────────────
// Injector detection
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function seededFloat(seed: string): number {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) h = ((h << 5) + h) ^ seed.charCodeAt(i);
  return (h >>> 0) / 0xffffffff;
}

const AUTO_REASONS = [
  'Credentials Harvested', 'Port 445 (SMB)', 'Port 22 (SSH)', 'Port 3389 (RDP)',
  'Kerberoasting', 'Pass-the-Hash', 'Service Account Pivot', 'Admin Share (C$)',
  'WMI Execution', 'Token Impersonation', 'DCOM Remote Exec', 'HTTPS Exfil (443)',
  'DNS Recon', 'Scheduled Task', 'Named Pipe Hijacking', 'Golden Ticket',
  'Silver Ticket', 'NTLM Relay', 'DCSync Attack', 'Lateral via VPN',
];

function autoReason(nodeA: string, nodeB: string): string {
  const s = nodeA + '->' + nodeB;
  let h = 0;
  for (let i = 0; i < s.length; i++) h = ((h * 31 + s.charCodeAt(i)) & 0x7fffffff);
  return AUTO_REASONS[Math.abs(h) % AUTO_REASONS.length];
}

function getSegmentReason(path: AttackPathDefinition, nodeA: string, nodeB: string): string {
  return path.path_segment_reasons?.[`${nodeA}->${nodeB}`] ?? autoReason(nodeA, nodeB);
}

function getSegmentDetails(path: AttackPathDefinition, nodeA: string, nodeB: string) {
  return path.path_segment_details?.[`${nodeA}->${nodeB}`] ?? null;
}

/** Derive a concise recon finding label from an injector contract action node */
function getReconFinding(action: AttackPathNode): string {
  const name = (action.node_payload_name ?? action.node_label ?? '').toLowerCase();
  const terminal = (action.node_terminal_output ?? '').toLowerCase();
  const creds = action.node_credentials_found;

  // Credentials found → highest priority
  if (creds && creds.length > 0) return 'Credentials found';
  if (terminal.includes('pwn3d') || terminal.includes('credential')) return 'Credentials found';

  // Nuclei / CVE exploits
  if (name.includes('log4shell') || terminal.includes('log4shell')) return 'CVE-2021-44228';
  if (name.includes('nuclei') || name.includes('cve-')) {
    const cveMatch = name.match(/cve-\d{4}-\d+/);
    return cveMatch ? cveMatch[0].toUpperCase() : 'CVE exploit found';
  }

  // Nmap: extract first open port from terminal output
  if (name.includes('nmap') || name.includes('tcp syn') || name.includes('tcp fin') || name.includes('connect scan')) {
    const portMatch = terminal.match(/(\d{2,5})\/tcp\s+open/);
    if (portMatch) return `Port ${portMatch[1]} open`;
    return 'Open ports found';
  }

  // Netexec by protocol
  if (name.includes('smb')) {
    if (name.includes('spray') || name.includes('password') || name.includes('gpp')) return 'Credentials found';
    if (name.includes('pass-the-hash') || name.includes('pth')) return 'Port 445 open';
    if (name.includes('av') || name.includes('enum')) return 'Port 445 open';
    return 'Port 445 open';
  }
  if (name.includes('ssh')) return 'Port 22 open';
  if (name.includes('rdp')) return 'Port 3389 open';
  if (name.includes('mssql') || name.includes('sql')) return 'Port 1433 open';
  if (name.includes('ldap') || name.includes('kerberoast')) return 'LDAP accessible';
  if (name.includes('wmi')) return 'WMI accessible';
  if (name.includes('ftp')) return 'Port 21 open';
  if (name.includes('nfs')) return 'Port 2049 open';

  // Fallback: deterministic label from node id
  const fallbacks = ['Port 443 open', 'Port 8080 open', 'Service found', 'Host reachable', 'Port 80 open'];
  let h = 0;
  for (let i = 0; i < action.node_id.length; i++) h = ((h * 31 + action.node_id.charCodeAt(i)) & 0x7fffffff);
  return fallbacks[Math.abs(h) % fallbacks.length];
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout — BFS organic, BASE_X = 490 to leave room for injectors + contracts
// ─────────────────────────────────────────────────────────────────────────────

function diamondPoints(cx: number, cy: number, r: number): string {
  return `${cx},${cy - r} ${cx + r},${cy} ${cx},${cy + r} ${cx - r},${cy}`;
}

function computeFlatLayout(
  assetNodes: AttackPathNode[],
  paths: AttackPathDefinition[],
): Array<{ nodeId: string; cx: number; cy: number }> {
  if (assetNodes.length === 0) return [];
  const n = assetNodes.length;
  const adjacent = new Map<string, Set<string>>();
  for (const node of assetNodes) adjacent.set(node.node_id, new Set());
  for (const path of paths) {
    for (let i = 0; i < path.node_ids.length - 1; i++) {
      const a = path.node_ids[i]; const b = path.node_ids[i + 1];
      adjacent.get(a)?.add(b); adjacent.get(b)?.add(a);
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
    for (const neighbor of Array.from(adjacent.get(id) ?? [])) {
      if (!visited.has(neighbor)) { visited.add(neighbor); queue.push(neighbor); }
    }
  }
  for (const node of assetNodes) { if (!visited.has(node.node_id)) ordered.push(node.node_id); }
  const COLS = Math.max(3, Math.ceil(Math.sqrt(n * 0.75)));
  const BASE_X = 490;
  const BASE_Y = 120;
  const STEP_X = 340; const STEP_Y = 280;
  const STAGGER = STEP_X * 0.5;
  const JX_MAX = 18; const JY_MAX = 12;
  return ordered.map((nodeId, i) => {
    const col = i % COLS; const row = Math.floor(i / COLS);
    const staggerX = (row % 2) * STAGGER;
    const rowNudge = (seededFloat(`row${row}nudge`) - 0.5) * 18;
    const jx = (seededFloat(nodeId + 'jx') - 0.5) * JX_MAX * 2;
    const jy = (seededFloat(nodeId + 'jy') - 0.5) * JY_MAX * 2;
    return { nodeId, cx: BASE_X + col * STEP_X + staggerX + jx, cy: BASE_Y + row * STEP_Y + rowNudge + jy };
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Arrowhead helper
// ─────────────────────────────────────────────────────────────────────────────

function arrowPoints(
  ax: number, ay: number, bx: number, by: number,
  srcR = NODE_R, dstR = NODE_R,
): string | null {
  const dist = Math.sqrt((bx - ax) ** 2 + (by - ay) ** 2);
  if (dist < srcR + dstR + ARROW_LEN + 10) return null;
  const ux = (bx - ax) / dist; const uy = (by - ay) / dist;
  const tipX = bx - ux * (dstR + 3); const tipY = by - uy * (dstR + 3);
  const baseX = tipX - ux * ARROW_LEN; const baseY = tipY - uy * ARROW_LEN;
  const px = -uy; const py = ux;
  return `${tipX},${tipY} ${baseX - px * ARROW_SIZE},${baseY - py * ARROW_SIZE} ${baseX + px * ARROW_SIZE},${baseY + py * ARROW_SIZE}`;
}

function curvedArrow(
  ax: number, ay: number, bx: number, by: number,
  srcR = CONTRACT_R, dstR = NODE_R, curvature = 70,
): { pathD: string; arrowPts: string | null; labelX: number; labelY: number } | null {
  const dist = Math.sqrt((bx - ax) ** 2 + (by - ay) ** 2);
  if (dist < srcR + dstR + 20) return null;
  const ux = (bx - ax) / dist; const uy = (by - ay) / dist;
  const startX = ax + ux * (srcR + 3); const startY = ay + uy * (srcR + 3);
  const endX   = bx - ux * (dstR + 3); const endY   = by - uy * (dstR + 3);
  const mx = (startX + endX) / 2; const my = (startY + endY) / 2;
  const px = -uy; const py = ux;
  const cpX = mx + px * curvature; const cpY = my + py * curvature;
  const pathD = `M ${startX} ${startY} Q ${cpX} ${cpY} ${endX} ${endY}`;
  const tdx = endX - cpX; const tdy = endY - cpY;
  const tlen = Math.sqrt(tdx * tdx + tdy * tdy);
  if (tlen < 1) return null;
  const tux = tdx / tlen; const tuy = tdy / tlen;
  const tipX = endX; const tipY = endY;
  const baseX = tipX - tux * ARROW_LEN; const baseY = tipY - tuy * ARROW_LEN;
  const perpX = -tuy; const perpY = tux;
  const arrowPts = `${tipX},${tipY} ${baseX - perpX * ARROW_SIZE},${baseY - perpY * ARROW_SIZE} ${baseX + perpX * ARROW_SIZE},${baseY + perpY * ARROW_SIZE}`;
  const labelX = 0.25 * startX + 0.5 * cpX + 0.25 * endX;
  const labelY = 0.25 * startY + 0.5 * cpY + 0.25 * endY;
  return { pathD, arrowPts, labelX, labelY };
}

// ─────────────────────────────────────────────────────────────────────────────
// Data types
// ─────────────────────────────────────────────────────────────────────────────

interface InjectorGroup {
  tool: string;
  color: string;
  contracts: Array<{ action: AttackPathNode; targetAssetId: string }>;
}

interface BadgeDetail {
  label: string;
  detail: NonNullable<AttackPathDefinition['path_segment_details']>[string] | null;
  x: number; y: number; segColor: string;
}

interface NodeTip    { node: AttackPathNode; x: number; y: number }
interface ContractTip { action: AttackPathNode; targetLabel: string; tool: string; x: number; y: number }
interface InjectorTip { group: InjectorGroup; x: number; y: number }

// ─────────────────────────────────────────────────────────────────────────────
// Finding categories
// ─────────────────────────────────────────────────────────────────────────────

type FindingCategory = 'credentials' | 'cves' | 'ports' | 'other' | 'grey';

const FINDING_META: Record<FindingCategory, { label: string; icon: string; color: string }> = {
  credentials: { label: 'Credentials Found',    icon: '🔑', color: '#ff9800' },
  cves:        { label: 'CVE / Vulnerabilities', icon: '☣',  color: '#f44336' },
  ports:       { label: 'Open Ports',            icon: '⬡',  color: '#42a5f5' },
  other:       { label: 'Other Findings',        icon: '⚡', color: '#ab47bc' },
  grey:        { label: 'Discovered',           icon: '⬡',  color: '#616161' },
};
const CATEGORY_ORDER: FindingCategory[] = ['credentials', 'cves', 'ports', 'other', 'grey'];

function getAssetFindingCategory(
  assetId: string,
  injectorGroups: InjectorGroup[],
  nodesInAnyPath: Set<string>,
): FindingCategory {
  if (!nodesInAnyPath.has(assetId)) return 'grey';
  const contracts = injectorGroups.flatMap((g) => g.contracts.filter((c) => c.targetAssetId === assetId));
  if (contracts.length === 0) return 'grey';
  let cat: FindingCategory = 'other';
  for (const { action } of contracts) {
    if (action.node_credentials_found && action.node_credentials_found.length > 0) return 'credentials';
    const out = action.node_terminal_output ?? '';
    if (/CVE-|nuclei/i.test(out)) { if (cat !== 'credentials') cat = 'cves'; }
    else if (/\d+\/tcp\s+open/i.test(out) && cat === 'other') cat = 'ports';
  }
  return cat;
}

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────

const AttackPathGraphV4U3: FunctionComponent<Props> = ({
  nodes, edges, paths, selectedActionNodeId,
  onNodeClick, onPathClick, onBadgeClick, onLegendPathSelect, selectedPathId, height = '100%',
}) => {
  const [nodeTooltip,   setNodeTooltip]   = useState<NodeTip | null>(null);
  const [badgeTooltip,  setBadgeTooltip]  = useState<BadgeDetail | null>(null);
  const [contractTip,   setContractTip]   = useState<ContractTip | null>(null);

  // whether the endpoint network is expanded (default: collapsed into one group node)
  const [endpointsExpanded, setEndpointsExpanded] = useState(true);
  // when an endpoint is selected and contracts are filtered, track which tools' dimmed groups are manually expanded
  const [expandedDimmedGroups, setExpandedDimmedGroups] = useState<Set<string>>(new Set());
  const [expandedInjectors,    setExpandedInjectors]    = useState<Set<string>>(new Set());
  const [autoExpandedInjectors, setAutoExpandedInjectors] = useState<Set<string>>(new Set());
  const [expandedFindingGroups, setExpandedFindingGroups] = useState<Set<FindingCategory>>(new Set());

  // Auto-expand endpoints when a path is selected from the stats bar
  useEffect(() => {
    if (selectedPathId) setEndpointsExpanded(true);
  }, [selectedPathId]);

  // ── Zoom / Pan ─────────────────────────────────────────────────────────────
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox,    setViewBox]   = useState({ x: 0, y: 0, w: 1800, h: 1000 });
  const [isPanning,  setIsPanning] = useState(false);
  const [panStart,   setPanStart]  = useState({ x: 0, y: 0 });

  const zoomFn = useCallback((factor: number) => {
    setViewBox((vb) => {
      const cx = vb.x + vb.w / 2; const cy = vb.y + vb.h / 2;
      const nw = Math.min(Math.max(vb.w * factor, 300), 6000);
      const nh = Math.min(Math.max(vb.h * factor, 200), 6000);
      return { x: cx - nw / 2, y: cy - nh / 2, w: nw, h: nh };
    });
  }, []);

  const handleWheel     = useCallback((e: React.WheelEvent) => { e.preventDefault(); zoomFn(e.deltaY > 0 ? 1.1 : 0.9); }, [zoomFn]);
  const handleMouseDown = useCallback((e: React.MouseEvent) => { if (e.button === 0) { setIsPanning(true); setPanStart({ x: e.clientX, y: e.clientY }); } }, []);
  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!isPanning) return;
    const dx = (e.clientX - panStart.x) * (viewBox.w / (svgRef.current?.clientWidth ?? 1));
    const dy = (e.clientY - panStart.y) * (viewBox.h / (svgRef.current?.clientHeight ?? 1));
    setViewBox((vb) => ({ ...vb, x: vb.x - dx, y: vb.y - dy }));
    setPanStart({ x: e.clientX, y: e.clientY });
  }, [isPanning, panStart, viewBox]);
  const handleMouseUp   = useCallback(() => setIsPanning(false), []);

  // ── Legend ─────────────────────────────────────────────────────────────────
  const [legendSelectedPathId, setLegendSelectedPathId] = useState<string | null>(null);
  const handleLegendClick = useCallback((pathId: string) => {
    setLegendSelectedPathId((prev) => {
      const next = prev === pathId ? null : pathId;
      onLegendPathSelect?.(next ? paths.find((p) => p.path_id === next) ?? null : null);
      return next;
    });
  }, [paths, onLegendPathSelect]);

  // ── Core data ───────────────────────────────────────────────────────────────
  const assetNodes = useMemo(() => nodes.filter((n) => n.node_type === 'ASSET'), [nodes]);
  // Always show ALL endpoints in their groups; path selection uses opacity to highlight/dim
  const visibleAssetNodes = assetNodes;

  // Node IDs in the currently selected path (null when All Paths)
  const selectedPathNodeIds = useMemo(() => {
    if (!selectedPathId) return null;
    const path = paths.find((p) => p.path_id === selectedPathId);
    return path ? new Set(path.node_ids) : null;
  }, [selectedPathId, paths]);
  const nodeCoords = useMemo(() => computeFlatLayout(visibleAssetNodes, paths), [visibleAssetNodes, paths]);
  const rawCoordMap = useMemo(() => {
    const m = new Map<string, { cx: number; cy: number }>();
    for (const c of nodeCoords) m.set(c.nodeId, c);
    return m;
  }, [nodeCoords]);

  // ── Injector groups ─────────────────────────────────────────────────────────
  const injectorGroups = useMemo<InjectorGroup[]>(() => {
    const groupMap = new Map<string, InjectorGroup>();
    for (const assetNode of assetNodes) {
      const acts = getActionsForAssetFull(assetNode, nodes, edges).filter(isInjectorAction);
      for (const act of acts) {
        const tool = injectorTool(act);
        if (!groupMap.has(tool)) {
          groupMap.set(tool, { tool, color: getToolColor(tool), contracts: [] });
        }
        groupMap.get(tool)!.contracts.push({ action: act, targetAssetId: assetNode.node_id });
      }
    }
    for (const group of groupMap.values()) {
      group.contracts.sort((a, b) =>
        (a.action.node_executed_at ?? '').localeCompare(b.action.node_executed_at ?? ''));
    }
    return Array.from(groupMap.values());
  }, [assetNodes, nodes, edges]);

  // ── Contract & injector positions — global sequential layout, no overlaps ──
  const CONTRACT_GROUP_GAP = 36; // extra vertical gap between groups

  const { injectorPositions, contractPositions } = useMemo(() => {
    const injMap = new Map<string, { cx: number; cy: number }>();
    const cMap   = new Map<string, { cx: number; cy: number }>();
    if (injectorGroups.length === 0) return { injectorPositions: injMap, contractPositions: cMap };

    const totalContracts = injectorGroups.reduce((s, g) => s + g.contracts.length, 0);
    const totalSpan =
      (totalContracts - 1) * CONTRACT_SPACING +
      Math.max(0, injectorGroups.length - 1) * CONTRACT_GROUP_GAP;
    const avgY = nodeCoords.length > 0
      ? nodeCoords.reduce((s, c) => s + c.cy, 0) / nodeCoords.length
      : 380;
    let cursor = avgY - totalSpan / 2;

    for (const group of injectorGroups) {
      const groupTop = cursor;
      group.contracts.forEach(({ action }) => {
        cMap.set(action.node_id, { cx: CONTRACT_X, cy: cursor });
        cursor += CONTRACT_SPACING;
      });
      cursor -= CONTRACT_SPACING;
      const groupBottom = cursor;
      const midY = (groupTop + groupBottom) / 2;
      injMap.set(group.tool, { cx: INJECTOR_X, cy: midY });
      cursor += CONTRACT_SPACING + CONTRACT_GROUP_GAP;
    }

    return { injectorPositions: injMap, contractPositions: cMap };
  }, [injectorGroups, nodeCoords]);

  const svgWidth  = useMemo(() => Math.max(1400, ...nodeCoords.map((c) => c.cx + NODE_R + 400)), [nodeCoords]);
  const svgHeight = useMemo(
    () => Math.max(endpointsExpanded ? 3000 : 1000, ...nodeCoords.map((c) => c.cy + NODE_R + 400)),
    [nodeCoords, endpointsExpanded],
  );

  // ── Group node position — centre of the endpoint cloud ─────────────────────
  const groupNodePos = useMemo(() => {
    if (nodeCoords.length === 0) return { cx: 820, cy: 400 };
    const avgX = nodeCoords.reduce((s, c) => s + c.cx, 0) / nodeCoords.length;
    const avgY = nodeCoords.reduce((s, c) => s + c.cy, 0) / nodeCoords.length;
    return { cx: avgX, cy: avgY };
  }, [nodeCoords]);

  // ── Flattened contract names (shown on group node) ──────────────────────────
  const allContractNames = useMemo(
    () => injectorGroups.flatMap((g) => g.contracts.map((c) => c.action.node_label)),
    [injectorGroups],
  );

  const nodesInAnyPath = useMemo(() => {
    const set = new Set<string>();
    for (const path of paths) for (const id of path.node_ids) set.add(id);
    return set;
  }, [paths]);

  const assetCategories = useMemo(() => {
    const map = new Map<string, FindingCategory>();
    for (const asset of visibleAssetNodes) {
      map.set(asset.node_id, getAssetFindingCategory(asset.node_id, injectorGroups, nodesInAnyPath));
    }
    return map;
  }, [visibleAssetNodes, injectorGroups, nodesInAnyPath]);

  // Auto-expand finding groups that contain endpoints in the selected path
  useEffect(() => {
    if (!selectedPathId) return;
    const path = paths.find((p) => p.path_id === selectedPathId);
    if (!path) return;
    const pathNodeSet = new Set(path.node_ids);
    const catsToExpand = new Set<FindingCategory>();
    for (const [nodeId, cat] of assetCategories) {
      if (pathNodeSet.has(nodeId)) catsToExpand.add(cat);
    }
    if (catsToExpand.size > 0) {
      setExpandedFindingGroups((prev) => {
        const next = new Set(prev);
        for (const cat of catsToExpand) next.add(cat);
        return next;
      });
    }
  }, [selectedPathId, paths, assetCategories]);

  // Auto-expand injector tool groups on endpoint click or path select
  useEffect(() => {
    const toolsToExpand = new Set<string>();
    if (selectedActionNodeId) {
      const assetNode = nodes.find((n) => n.node_id === selectedActionNodeId && n.node_type === 'ASSET');
      if (assetNode) {
        for (const g of injectorGroups) {
          if (g.contracts.some((c) => c.targetAssetId === selectedActionNodeId)) toolsToExpand.add(g.tool);
        }
      }
    }
    if (selectedPathId) {
      const path = paths.find((p) => p.path_id === selectedPathId);
      if (path) {
        const pathNodeSet = new Set(path.node_ids);
        for (const g of injectorGroups) {
          if (g.contracts.some((c) => pathNodeSet.has(c.targetAssetId))) toolsToExpand.add(g.tool);
        }
      }
    }
    if (toolsToExpand.size === 0) return;
    setAutoExpandedInjectors((prev) => {
      const next = new Set(prev);
      for (const t of toolsToExpand) next.add(t);
      return next;
    });
  }, [selectedActionNodeId, selectedPathId, nodes, paths, injectorGroups]);

  // ── Finding group layout: vertical stacking so groups never overlap ────────
  // All groups are stacked top-to-bottom in a fixed column starting at FG_START_X.
  // Expanded groups use a zone-box with a node grid; collapsed groups use a compact card.
  const FG_PAD       = 28;    // internal padding inside zone box
  const FG_HEADER_H  = 46;    // zone box header height
  const FG_COL_W     = 180;   // horizontal pitch between node centres
  const FG_ROW_H     = 150;   // vertical pitch between node centres
  const FG_MAX_COLS  = 4;
  const FG_START_X   = 520;   // left edge of all group boxes / cards
  const FG_CARD_W    = 220;   // collapsed card width
  const FG_CARD_H    = 52;    // collapsed card height
  const FG_STACK_GAP = 48;    // vertical gap between stacked groups
  const FG_START_Y   = 60;    // Y of the first group

  const findingGroupStack = useMemo(() => {
    type StackEntry = {
      x: number; y: number; width: number; height: number;
      isExpanded: boolean;
      nodePositions: Map<string, { cx: number; cy: number }>;
    };
    const result = new Map<FindingCategory, StackEntry>();
    let currentY = FG_START_Y;
    for (const cat of CATEGORY_ORDER) {
      const assetIds = Array.from(assetCategories.entries())
        .filter(([, c]) => c === cat).map(([id]) => id);
      if (assetIds.length === 0) continue;
      if (expandedFindingGroups.has(cat)) {
        const cols   = Math.min(FG_MAX_COLS, assetIds.length);
        const rows   = Math.ceil(assetIds.length / cols);
        const width  = cols * FG_COL_W + FG_PAD * 2;
        const height = rows * FG_ROW_H + FG_HEADER_H + FG_PAD;
        const x = FG_START_X; const y = currentY;
        const nodePositions = new Map<string, { cx: number; cy: number }>();
        assetIds.forEach((id, i) => {
          nodePositions.set(id, {
            cx: x + FG_PAD + (i % cols) * FG_COL_W + FG_COL_W / 2,
            cy: y + FG_HEADER_H + FG_PAD / 2 + Math.floor(i / cols) * FG_ROW_H + NODE_R,
          });
        });
        result.set(cat, { x, y, width, height, isExpanded: true, nodePositions });
        currentY += height + FG_STACK_GAP;
      } else {
        result.set(cat, {
          x: FG_START_X, y: currentY,
          width: FG_CARD_W, height: FG_CARD_H,
          isExpanded: false, nodePositions: new Map(),
        });
        currentY += FG_CARD_H + FG_STACK_GAP;
      }
    }
    return result;
  }, [expandedFindingGroups, assetCategories]);

  // Dynamic coord map: always use stacked group positions (zone boxes / collapsed cards).
  // When a path is selected, visibleAssetNodes is already filtered to that path's nodes,
  // so groups naturally show only the relevant endpoints.
  const coordMap = useMemo(() => {
    const m = new Map<string, { cx: number; cy: number }>();
    for (const [assetId, cat] of assetCategories) {
      const entry = findingGroupStack.get(cat);
      if (!entry) continue;
      if (entry.isExpanded) {
        const pos = entry.nodePositions.get(assetId) ?? rawCoordMap.get(assetId);
        if (pos) m.set(assetId, pos);
      } else {
        m.set(assetId, { cx: entry.x + entry.width / 2, cy: entry.y + entry.height / 2 });
      }
    }
    for (const c of nodeCoords) {
      if (!m.has(c.nodeId)) m.set(c.nodeId, c);
    }
    return m;
  }, [assetCategories, rawCoordMap, findingGroupStack, nodeCoords]);

  // ── V1 path data ────────────────────────────────────────────────────────────
  const outcomeIndexMap = useMemo(() => {
    const map = new Map<string, number>(); const counts: Record<string, number> = {};
    for (const path of paths) {
      const o = path.path_outcome ?? 'success';
      map.set(path.path_id, counts[o] ?? 0);
      counts[o] = (counts[o] ?? 0) + 1;
    }
    return map;
  }, [paths]);

  const assetToActionLabelMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const node of nodes) {
      if (node.node_type !== 'ASSET') continue;
      const acts = getActionsForAssetFull(node, nodes, edges)
        .filter((a) => !isInjectorAction(a))
        .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));
      if (acts.length > 0) map.set(node.node_id, acts[0].node_label);
    }
    return map;
  }, [nodes, edges]);

  const segmentBadgeOrder = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const path of paths) {
      for (let i = 0; i < path.node_ids.length - 1; i++) {
        const key = `${path.node_ids[i]}->${path.node_ids[i + 1]}`;
        if (!map.has(key)) map.set(key, []);
        map.get(key)!.push(path.path_id);
      }
    }
    return map;
  }, [paths]);

  /**
   * Collision-resolved badge positions — ZERO OVERLAP GUARANTEE.
   * Phase 1: Pure AABB+circle separation (300 iters). Phase 2: Relax toward anchor.
   */
  const resolvedBadgePositions = useMemo(() => {
    const BADGE_H    = 20;
    const BADGE_HH   = BADGE_H / 2;
    const GAP        = 10;
    const NODE_GAP   = 10;
    const SEP_ITERS  = 300;
    const RELAX_ITERS = 60;
    const RELAX_K    = 0.025;

    type BadgeItem = {
      key: string;
      x: number; y: number;
      hw: number;
      ax: number; ay: number;
    };

    const items: BadgeItem[] = [];
    for (const path of paths) {
      for (let i = 0; i < path.node_ids.length - 1; i++) {
        const nodeA = path.node_ids[i];
        const nodeB = path.node_ids[i + 1];
        const a = coordMap.get(nodeA);
        const b = coordMap.get(nodeB);
        if (!a || !b) continue;
        const segDist = Math.sqrt((b.cx - a.cx) ** 2 + (b.cy - a.cy) ** 2);
        if (segDist < NODE_R * 2 + 50) continue;
        const mx = (a.cx + b.cx) / 2;
        const my = (a.cy + b.cy) / 2;
        const px = -(b.cy - a.cy) / segDist;
        const py =  (b.cx - a.cx) / segDist;
        const segKey = `${nodeA}->${nodeB}`;
        const segGroup = segmentBadgeOrder.get(segKey) ?? [path.path_id];
        const rank = segGroup.indexOf(path.path_id);
        const dir  = rank === 0 ? 0 : (rank % 2 === 1 ? 1 : -1) * Math.ceil(rank / 2);
        const offset = 14 + dir * (BADGE_H + 5);
        const ax = mx + px * offset;
        const ay = my + py * offset;
        const details = getSegmentDetails(path, nodeA, nodeB);
        const label = assetToActionLabelMap.get(nodeB) ?? details?.action ?? getSegmentReason(path, nodeA, nodeB);
        const trunc  = label.length > 28 ? label.slice(0, 26) + '…' : label;
        const textW  = Math.min(trunc.length * 6.2 + 20, 190);
        items.push({ key: `${path.path_id}-badge-${i}`, x: ax, y: ay, hw: textW / 2, ax, ay });
      }
    }
    if (items.length === 0) return new Map<string, { x: number; y: number }>();

    function separatePair(a: BadgeItem, b: BadgeItem): boolean {
      const ox = (a.hw + b.hw + GAP) - Math.abs(a.x - b.x);
      const oy = (BADGE_HH + BADGE_HH + GAP) - Math.abs(a.y - b.y);
      if (ox <= 0 || oy <= 0) return false;
      if (ox <= oy) {
        const d = ox / 2 + 0.5;
        if (a.x <= b.x) { a.x -= d; b.x += d; } else { a.x += d; b.x -= d; }
      } else {
        const d = oy / 2 + 0.5;
        if (a.y <= b.y) { a.y -= d; b.y += d; } else { a.y += d; b.y -= d; }
      }
      return true;
    }
    function separateFromNode(item: BadgeItem, nc: { cx: number; cy: number }): boolean {
      const nearX = Math.max(item.x - item.hw, Math.min(nc.cx, item.x + item.hw));
      const nearY = Math.max(item.y - BADGE_HH,  Math.min(nc.cy, item.y + BADGE_HH));
      const dx = nc.cx - nearX; const dy = nc.cy - nearY;
      const rectDist = Math.sqrt(dx * dx + dy * dy);
      const required = NODE_R + NODE_GAP;
      if (rectDist >= required || rectDist < 0.001) return false;
      const bcDx = item.x - nc.cx; const bcDy = item.y - nc.cy;
      const bcDist = Math.sqrt(bcDx * bcDx + bcDy * bcDy);
      if (bcDist < 0.001) { item.y -= (required - rectDist) + 0.5; }
      else { const push = (required - rectDist) + 0.5; item.x += (bcDx / bcDist) * push; item.y += (bcDy / bcDist) * push; }
      return true;
    }

    for (let iter = 0; iter < SEP_ITERS; iter++) {
      let moved = 0;
      for (let i = 0; i < items.length; i++)
        for (let j = i + 1; j < items.length; j++)
          if (separatePair(items[i], items[j])) moved++;
      for (const item of items)
        for (const nc of nodeCoords)
          if (separateFromNode(item, nc)) moved++;
      if (moved === 0) break;
    }
    for (let iter = 0; iter < RELAX_ITERS; iter++) {
      for (const item of items) { item.x += (item.ax - item.x) * RELAX_K; item.y += (item.ay - item.y) * RELAX_K; }
      for (let i = 0; i < items.length; i++)
        for (let j = i + 1; j < items.length; j++) separatePair(items[i], items[j]);
      for (const item of items)
        for (const nc of nodeCoords) separateFromNode(item, nc);
    }

    const posMap = new Map<string, { x: number; y: number }>();
    for (const item of items) posMap.set(item.key, { x: item.x, y: item.y });
    return posMap;
  }, [paths, coordMap, segmentBadgeOrder, nodeCoords, assetToActionLabelMap]);

  /** Collision-resolved positions for recon finding labels on curved arrows. */
  const resolvedReconLabelPositions = useMemo(() => {
    const LABEL_H  = 17;
    const HALF_H   = LABEL_H / 2;
    const GAP      = 6;
    const NODE_GAP = 8;
    const SEP_ITERS   = 200;
    const RELAX_ITERS = 40;
    const RELAX_K     = 0.025;

    type LabelItem = { key: string; x: number; y: number; hw: number; ax: number; ay: number; };
    const items: LabelItem[] = [];
    for (const group of injectorGroups) {
      for (const { action, targetAssetId } of group.contracts) {
        const cp = contractPositions.get(action.node_id);
        const tp = coordMap.get(targetAssetId);
        if (!cp || !tp) continue;
        const ca = curvedArrow(cp.cx, cp.cy, tp.cx, tp.cy, CONTRACT_R, NODE_R);
        if (!ca) continue;
        const finding = getReconFinding(action);
        const w = finding.length * 5.5 + 14;
        items.push({ key: action.node_id, x: ca.labelX, y: ca.labelY, hw: w / 2, ax: ca.labelX, ay: ca.labelY });
      }
    }
    if (items.length === 0) return new Map<string, { x: number; y: number }>();

    function sepPair(a: LabelItem, b: LabelItem): boolean {
      const ox = (a.hw + b.hw + GAP) - Math.abs(a.x - b.x);
      const oy = (HALF_H + HALF_H + GAP) - Math.abs(a.y - b.y);
      if (ox <= 0 || oy <= 0) return false;
      if (ox <= oy) { const d = ox / 2 + 0.5; if (a.x <= b.x) { a.x -= d; b.x += d; } else { a.x += d; b.x -= d; } }
      else { const d = oy / 2 + 0.5; if (a.y <= b.y) { a.y -= d; b.y += d; } else { a.y += d; b.y -= d; } }
      return true;
    }
    function sepNode(item: LabelItem, nc: { cx: number; cy: number }): boolean {
      const nearX = Math.max(item.x - item.hw, Math.min(nc.cx, item.x + item.hw));
      const nearY = Math.max(item.y - HALF_H,  Math.min(nc.cy, item.y + HALF_H));
      const dx = nc.cx - nearX; const dy = nc.cy - nearY;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const req = NODE_R + NODE_GAP;
      if (dist >= req || dist < 0.001) return false;
      const bcDx = item.x - nc.cx; const bcDy = item.y - nc.cy;
      const bcDist = Math.sqrt(bcDx * bcDx + bcDy * bcDy);
      const push = (req - dist) + 0.5;
      if (bcDist < 0.001) { item.y -= push; } else { item.x += (bcDx / bcDist) * push; item.y += (bcDy / bcDist) * push; }
      return true;
    }

    for (let iter = 0; iter < SEP_ITERS; iter++) {
      let moved = 0;
      for (let i = 0; i < items.length; i++)
        for (let j = i + 1; j < items.length; j++)
          if (sepPair(items[i], items[j])) moved++;
      for (const item of items)
        for (const nc of nodeCoords)
          if (sepNode(item, nc)) moved++;
      if (moved === 0) break;
    }
    for (let iter = 0; iter < RELAX_ITERS; iter++) {
      for (const item of items) { item.x += (item.ax - item.x) * RELAX_K; item.y += (item.ay - item.y) * RELAX_K; }
      for (let i = 0; i < items.length; i++)
        for (let j = i + 1; j < items.length; j++) sepPair(items[i], items[j]);
      for (const item of items)
        for (const nc of nodeCoords) sepNode(item, nc);
    }

    const result = new Map<string, { x: number; y: number }>();
    for (const item of items) result.set(item.key, { x: item.x, y: item.y });
    return result;
  }, [injectorGroups, contractPositions, coordMap, nodeCoords]);

  const targetAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    const s = new Set<string>();
    for (const e of edges) { if (e.edge_type === 'asset_link' && e.edge_source === selectedActionNodeId) s.add(e.edge_target); }
    return s;
  }, [selectedActionNodeId, edges]);

  const sourceAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    const s = new Set<string>();
    for (const e of edges) { if (e.edge_type === 'asset_link' && e.edge_target === selectedActionNodeId) s.add(e.edge_source); }
    return s;
  }, [selectedActionNodeId, edges]);

  const activePaths = useMemo(() => {
    if (legendSelectedPathId) return new Set([legendSelectedPathId]);
    if (!selectedActionNodeId) return new Set<string>();
    const active = new Set<string>();
    for (const path of paths) {
      for (const nodeId of path.node_ids) {
        if (targetAssetIds.has(nodeId) || sourceAssetIds.has(nodeId)) { active.add(path.path_id); break; }
      }
    }
    return active;
  }, [selectedActionNodeId, paths, targetAssetIds, sourceAssetIds, legendSelectedPathId]);

  const hasSelection = activePaths.size > 0;

  const getNodeOpacity = useCallback((nodeId: string) => {
    // Path selected from stats bar: highlight path nodes, dim everything else
    if (selectedPathNodeIds) {
      return selectedPathNodeIds.has(nodeId) ? 1 : 0.15;
    }
    if (!hasSelection) return 1;
    for (const path of paths) {
      if (activePaths.has(path.path_id) && path.node_ids.includes(nodeId)) return 1;
    }
    if (targetAssetIds.has(nodeId) || sourceAssetIds.has(nodeId)) return 1;
    return 0.18;
  }, [hasSelection, activePaths, paths, targetAssetIds, sourceAssetIds, selectedPathNodeIds]);

  const getNodeScale = useCallback((nodeId: string) => {
    if (targetAssetIds.has(nodeId) || sourceAssetIds.has(nodeId)) return 1.15;
    return 1;
  }, [targetAssetIds, sourceAssetIds]);

  const buildPathD = useCallback((nodeIds: string[]): string => {
    const pts = nodeIds.map((id) => coordMap.get(id)).filter(Boolean) as { cx: number; cy: number }[];
    if (pts.length < 2) return '';
    let d = `M ${pts[0].cx} ${pts[0].cy}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i]; const p1 = pts[i + 1];
      const cp1x = p0.cx + (p1.cx - p0.cx) * 0.5; const cp1y = p0.cy - 50;
      const cp2x = p0.cx + (p1.cx - p0.cx) * 0.5; const cp2y = p1.cy - 50;
      d += ` C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p1.cx} ${p1.cy}`;
    }
    return d;
  }, [coordMap]);

  const getBadgePos = useCallback((nodeA: string, nodeB: string) => {
    const a = coordMap.get(nodeA); const b = coordMap.get(nodeB);
    if (!a || !b) return null;
    const dist = Math.sqrt((b.cx - a.cx) ** 2 + (b.cy - a.cy) ** 2);
    if (dist < NODE_R * 2 + 50) return null;
    const mx = (a.cx + b.cx) / 2; const my = (a.cy + b.cy) / 2;
    const px = -(b.cy - a.cy) / dist; const py = (b.cx - a.cx) / dist;
    return { mx, my, px, py, dist };
  }, [coordMap]);

  // Status colour for a contract node
  function contractStatusColor(action: AttackPathNode): string {
    const s = (action.node_status ?? '').toLowerCase();
    if (s === 'prevented' || s === 'failed')  return STATUS_COLORS.prevented.fill;
    if (s === 'detected'  || s === 'partial') return STATUS_COLORS.detected.fill;
    return STATUS_COLORS.undetected.fill;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Render
  // ─────────────────────────────────────────────────────────────────────────────

  return (
    <div style={{ position: 'relative', width: '100%', height, overflow: 'hidden' }}>
      <svg
        ref={svgRef}
        width="100%" height="100%"
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        style={{ display: 'block', cursor: isPanning ? 'grabbing' : 'grab' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onClick={() => { setBadgeTooltip(null); setContractTip(null); }}
      >
        <defs>
          {(['prevented', 'detected', 'undetected', 'pending'] as const).map((status) => (
            <filter key={`glow-v4u-${status}`} id={`glow-v4u-${status}`} x="-50%" y="-50%" width="200%" height="200%">
              <feDropShadow dx="0" dy="0" stdDeviation="8" floodColor={STATUS_COLORS[status].fill} floodOpacity="0.85" />
            </filter>
          ))}
          <filter id="glow-v4u-untouched" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#9e9e9e" floodOpacity="0.6" />
          </filter>
          {injectorGroups.map(({ tool, color }) => (
            <filter key={`glow-inj-${tool}`} id={`glow-inj-${tool}`} x="-60%" y="-60%" width="220%" height="220%">
              <feDropShadow dx="0" dy="0" stdDeviation="10" floodColor={color} floodOpacity="0.65" />
            </filter>
          ))}
        </defs>

        {/* ═══════════════════════════════════════════════════════════════════
            PATH LINES  (only when endpoint nodes are expanded AND a path is selected from the top bar)
        ═══════════════════════════════════════════════════════════════════ */}
        {endpointsExpanded && selectedPathId && paths.filter((p) => p.path_id === selectedPathId).map((path) => {
          const isActive = activePaths.has(path.path_id);
          const isFailed = path.path_outcome === 'failed' || path.path_outcome === 'partial';
          const successColor = getPathOutcomeColor('success', outcomeIndexMap.get(path.path_id) ?? 0);
          const failedSegColor = path.path_outcome === 'partial' ? STATUS_COLORS.detected.fill : STATUS_COLORS.prevented.fill;

          const failFromIdx = isFailed && path.failed_from_node_id
            ? path.node_ids.indexOf(path.failed_from_node_id)
            : (isFailed ? 0 : path.node_ids.length);
          const successIds = failFromIdx > 0 ? path.node_ids.slice(0, failFromIdx + 1) : (isFailed ? [] : path.node_ids);
          const failedIds  = isFailed && failFromIdx >= 0 ? path.node_ids.slice(failFromIdx) : [];

          const successD = successIds.length >= 2 ? buildPathD(successIds) : '';
          const failedD  = failedIds.length  >= 2 ? buildPathD(failedIds)  : '';

          let baseOpacity: number; let sw: number;
          if (!hasSelection)    { baseOpacity = 0.72; sw = 2.5; }
          else if (isActive)    { baseOpacity = 1.00; sw = 3.5; }
          else                  { baseOpacity = 0.05; sw = 2.0; }

          const lastAssetId = path.node_ids[path.node_ids.length - 1];
          const deadCoord = isFailed ? coordMap.get(lastAssetId) : null;

          return (
            <g key={path.path_id}>
              {/* Hit area */}
              {(() => {
                const hitD = (!isFailed ? buildPathD(path.node_ids) : null) || successD || failedD;
                if (!hitD) return null;
                return <path d={hitD} fill="none" stroke="transparent" strokeWidth={20}
                  style={{ cursor: 'pointer', pointerEvents: 'stroke' }}
                  onClick={(e) => { e.stopPropagation(); onPathClick?.(path.node_ids); }} />;
              })()}
              <g style={{ pointerEvents: 'none' }}>
                {successD && (
                  <g opacity={baseOpacity}>
                    <path d={successD} fill="none" stroke={successColor} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" />
                    {(isActive || !hasSelection) && <circle r={4} fill={successColor} opacity={0.9}><animateMotion dur="2.2s" repeatCount="indefinite" path={successD} /></circle>}
                  </g>
                )}
                {failedD && (
                  <g opacity={Math.max(baseOpacity * 0.75, 0.10)}>
                    <path d={failedD} fill="none" stroke={failedSegColor} strokeWidth={sw * 0.85} strokeLinecap="round" strokeLinejoin="round" strokeDasharray="8 6" />
                  </g>
                )}
                {!isFailed && (() => {
                  const fullD = buildPathD(path.node_ids);
                  if (!fullD) return null;
                  return (
                    <g opacity={baseOpacity}>
                      <path d={fullD} fill="none" stroke={successColor} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" />
                      {(isActive || !hasSelection) && <circle r={4} fill={successColor} opacity={0.9}><animateMotion dur="2.2s" repeatCount="indefinite" path={fullD} /></circle>}
                    </g>
                  );
                })()}
                {isFailed && deadCoord && (isActive || !hasSelection) && (
                  <g transform={`translate(${deadCoord.cx + NODE_R - 6}, ${deadCoord.cy - NODE_R + 6})`}>
                    <circle r={9} fill="rgba(20,20,30,0.92)" stroke={`${failedSegColor}cc`} strokeWidth={1.5} />
                    <text x={0} y={4} textAnchor="middle" fontSize={11} fontWeight={700} fill={failedSegColor} style={{ userSelect: 'none' }}>
                      {path.path_outcome === 'partial' ? '!' : '✓'}
                    </text>
                  </g>
                )}
              </g>

              {/* Arrowheads */}
              {(isActive || !hasSelection) && (() => {
                const arrowIds = successIds.length >= 2 ? successIds : (!isFailed ? path.node_ids : []);
                const arr = arrowIds.map((nId, i) => {
                  if (i === arrowIds.length - 1) return null;
                  const a = coordMap.get(nId); const b = coordMap.get(arrowIds[i + 1]);
                  if (!a || !b) return null;
                  const pts = arrowPoints(a.cx, a.cy, b.cx, b.cy);
                  if (!pts) return null;
                  return <polygon key={`arr-s-${path.path_id}-${i}`} points={pts} fill={successColor} opacity={baseOpacity * 0.9} style={{ pointerEvents: 'none' }} />;
                });
                const fArr = failedIds.length >= 2 ? (() => {
                  const a = coordMap.get(failedIds[0]); const b = coordMap.get(failedIds[failedIds.length - 1]);
                  if (!a || !b) return null;
                  const pts = arrowPoints(a.cx, a.cy, b.cx, b.cy);
                  if (!pts) return null;
                  return <polygon key={`arr-f-${path.path_id}`} points={pts} fill={failedSegColor} opacity={Math.max(baseOpacity * 0.85, 0.18) * 0.85} style={{ pointerEvents: 'none' }} />;
                })() : null;
                return <>{arr}{fArr}</>;
              })()}

              {/* Edge reason badges — collision-resolved (no overlap) */}
              {(isActive || !hasSelection) && path.node_ids.map((nodeId, i) => {
                if (i === path.node_ids.length - 1) return null;
                const nextNodeId = path.node_ids[i + 1];
                const pos = getBadgePos(nodeId, nextNodeId);
                if (!pos) return null;
                const badgeKey = `${path.path_id}-badge-${i}`;
                const resolved = resolvedBadgePositions.get(badgeKey);
                if (!resolved) return null;
                const bx = resolved.x;
                const by = resolved.y;
                const details = getSegmentDetails(path, nodeId, nextNodeId);
                const label = assetToActionLabelMap.get(nextNodeId) ?? details?.action ?? getSegmentReason(path, nodeId, nextNodeId);
                const isFailedSeg = isFailed && failFromIdx >= 0 && i >= failFromIdx;
                const segColor = isFailedSeg ? failedSegColor : successColor;
                const segOpacity = isFailedSeg ? Math.max(baseOpacity * 0.85, 0.18) : baseOpacity;
                const truncLabel = label.length > 28 ? label.slice(0, 26) + '…' : label;
                const textW = Math.min(truncLabel.length * 6.2 + 20, 190);
                const bh = 20;
                const hasDetails = !!(details?.trigger_event || details?.condition || details?.technique);
                const connDx = pos.mx - bx;
                const connDy = pos.my - by;
                const connDist = Math.sqrt(connDx * connDx + connDy * connDy);
                return (
                  <g key={badgeKey} transform={`translate(${bx}, ${by})`} opacity={segOpacity}
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => { e.stopPropagation(); setBadgeTooltip({ label, detail: details, x: e.clientX, y: e.clientY, segColor }); }}
                    onMouseMove={(e) => { e.stopPropagation(); setBadgeTooltip((p) => p ? { ...p, x: e.clientX, y: e.clientY } : null); }}
                    onMouseLeave={(e) => { e.stopPropagation(); setBadgeTooltip(null); }}
                    onClick={(e) => { e.stopPropagation(); onBadgeClick?.(nextNodeId); }}
                  >
                    {connDist > 2 && <line x1={0} y1={0} x2={connDx} y2={connDy} stroke={segColor} strokeWidth={0.8} opacity={0.35} strokeDasharray="2 2" style={{ pointerEvents: 'none' }} />}
                    <rect x={-textW / 2 - 2} y={-bh / 2 - 2} width={textW + 4} height={bh + 4} rx={bh / 2 + 2} fill={`${segColor}15`} />
                    <rect x={-textW / 2} y={-bh / 2} width={textW} height={bh} rx={bh / 2} fill="rgba(10,11,20,0.97)" stroke={segColor} strokeWidth={1.5} />
                    <text x={0} y={1} textAnchor="middle" dominantBaseline="middle" fontSize={9.5} fontWeight={700} fill={segColor} letterSpacing={0.2} style={{ userSelect: 'none' }}>{truncLabel}</text>
                    {hasDetails && <circle cx={textW / 2 - 5} cy={-bh / 2 + 5} r={3.5} fill={segColor} opacity={0.75} />}
                  </g>
                );
              })}
            </g>
          );
        })}

        {/* ═══════════════════════════════════════════════════════════════════
            RECON ARROWS — V4U3: visible only when a path is selected
            • Injector collapsed → aggregate arrow from injector hex to path endpoints
            • Injector expanded  → per-contract arrows (only path contracts)
        ═══════════════════════════════════════════════════════════════════ */}
        {injectorGroups.map((group) => {
          if (!selectedPathId) return null;
          const selectedPath = paths.find((p) => p.path_id === selectedPathId);
          const pathNodeIds = selectedPath ? new Set(selectedPath.node_ids) : new Set<string>();
          const pathContracts = group.contracts.filter((c) => pathNodeIds.has(c.targetAssetId));
          if (pathContracts.length === 0) return null;

          const isExpanded = expandedInjectors.has(group.tool) || autoExpandedInjectors.has(group.tool);

          if (!isExpanded) {
            // Collapsed: aggregate arrow from injector hex to each path endpoint
            const injPos = injectorPositions.get(group.tool);
            if (!injPos) return null;
            const uniqueTargets = [...new Set(pathContracts.map((c) => c.targetAssetId))];
            return uniqueTargets.map((targetAssetId) => {
              const tp = endpointsExpanded ? coordMap.get(targetAssetId) : { cx: groupNodePos.cx - GN_W / 2, cy: groupNodePos.cy };
              if (!tp) return null;
              const dist = Math.sqrt((tp.cx - injPos.cx) ** 2 + (tp.cy - injPos.cy) ** 2);
              if (dist < INJECTOR_R + 10) return null;
              const ux = (tp.cx - injPos.cx) / dist; const uy = (tp.cy - injPos.cy) / dist;
              const startX = injPos.cx + ux * (INJECTOR_R + 4); const startY = injPos.cy + uy * (INJECTOR_R + 4);
              const endX = tp.cx - ux * (endpointsExpanded ? NODE_R + 3 : 4); const endY = tp.cy - uy * (endpointsExpanded ? NODE_R + 3 : 4);
              const pts = endpointsExpanded ? arrowPoints(injPos.cx, injPos.cy, tp.cx, tp.cy, INJECTOR_R, NODE_R) : null;
              return (
                <g key={`recon-inj-${group.tool}-${targetAssetId}`} style={{ pointerEvents: 'none' }}>
                  <line x1={startX} y1={startY} x2={endX} y2={endY}
                    stroke={group.color} strokeWidth={1.4} strokeDasharray="6 4" opacity={0.55} />
                  {pts && <polygon points={pts} fill={group.color} opacity={0.7} />}
                  {!endpointsExpanded && <polygon points={`${endX},${endY} ${endX - ux * ARROW_LEN - (-uy) * ARROW_SIZE},${endY - uy * ARROW_LEN - ux * ARROW_SIZE} ${endX - ux * ARROW_LEN + (-uy) * ARROW_SIZE},${endY - uy * ARROW_LEN + ux * ARROW_SIZE}`} fill={group.color} opacity={0.7} />}
                </g>
              );
            });
          }

          // Expanded: contract-level arrows for path contracts only
          const selAsset = selectedActionNodeId
            ? nodes.find((n) => n.node_id === selectedActionNodeId && n.node_type === 'ASSET')
            : null;
          const dimmedGroupExpanded = expandedDimmedGroups.has(group.tool);
          const showAll = !selAsset || dimmedGroupExpanded;
          const visibleContracts = pathContracts.filter(
            ({ targetAssetId }) => showAll || targetAssetId === selectedActionNodeId
          );

          return visibleContracts.map(({ action, targetAssetId }) => {
            const cp = contractPositions.get(action.node_id);
            const sc = contractStatusColor(action);

            if (!endpointsExpanded) {
              if (!cp) return null;
              const gx = groupNodePos.cx - GN_W / 2;
              const gy = groupNodePos.cy;
              const caGrp = curvedArrow(cp.cx, cp.cy, gx, gy, CONTRACT_R, 4, 40);
              if (!caGrp) return null;
              return (
                <g key={`recon-c-${action.node_id}`} style={{ pointerEvents: 'none' }}>
                  <path d={caGrp.pathD} stroke={sc} strokeWidth={1.4} strokeDasharray="6 4" fill="none" opacity={0.55} />
                  {caGrp.arrowPts && <polygon points={caGrp.arrowPts} fill={sc} opacity={0.7} />}
                </g>
              );
            }

            const tp = coordMap.get(targetAssetId);
            if (!cp || !tp) return null;
            const ca = curvedArrow(cp.cx, cp.cy, tp.cx, tp.cy, CONTRACT_R, NODE_R);
            if (!ca) return null;
            const finding = getReconFinding(action);
            const labelLen = finding.length * 5.5 + 14;
            const rl = resolvedReconLabelPositions.get(action.node_id) ?? { x: ca.labelX, y: ca.labelY };
            return (
              <g key={`recon-c-${action.node_id}`} style={{ pointerEvents: 'none' }}>
                <path d={ca.pathD} stroke={sc} strokeWidth={1.4} strokeDasharray="6 4" fill="none" opacity={0.55} />
                {ca.arrowPts && <polygon points={ca.arrowPts} fill={sc} opacity={0.7} />}
                <rect x={rl.x - labelLen / 2} y={rl.y - 9} width={labelLen} height={17} rx={4}
                  fill="rgba(10,12,22,0.88)" stroke={sc} strokeWidth={0.8} opacity={0.92} />
                <text x={rl.x} y={rl.y + 4} textAnchor="middle" fontSize={9} fontWeight={600}
                  fill={sc} style={{ userSelect: 'none' }}>
                  {finding}
                </text>
              </g>
            );
          });
        })}

        {/* ═══════════════════════════════════════════════════════════════════
            ENDPOINT GROUP NODE  (shown when endpoints are collapsed)
        ═══════════════════════════════════════════════════════════════════ */}
        {!endpointsExpanded && (() => {
          const { cx, cy } = groupNodePos;
          const x = cx - GN_W / 2;
          const y = cy - GN_H / 2;
          const visibleNames = allContractNames.slice(0, 4);
          const moreCount   = allContractNames.length - visibleNames.length;

          return (
            <g style={{ cursor: 'pointer' }}
              onClick={(e) => { e.stopPropagation(); setEndpointsExpanded((v) => !v); }}
            >
              {/* Glow */}
              <rect x={x - 8} y={y - 8} width={GN_W + 16} height={GN_H + 16} rx={GN_R + 6}
                fill="rgba(100,180,255,0.06)" filter="url(#glow-v4u-untouched)"
                style={{ pointerEvents: 'none' }} />
              {/* Body */}
              <rect x={x} y={y} width={GN_W} height={GN_H} rx={GN_R}
                fill="rgba(14,18,36,0.97)" stroke="rgba(100,180,255,0.45)" strokeWidth={2} />
              {/* Top accent bar */}
              <rect x={x} y={y} width={GN_W} height={4} rx={GN_R}
                fill="rgba(100,180,255,0.35)" style={{ pointerEvents: 'none' }} />

              {/* Icon + title */}
              <text x={cx - GN_W / 2 + 16} y={y + 22} fontSize={13}
                fill="rgba(100,180,255,0.9)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                🖧
              </text>
              <text x={cx - GN_W / 2 + 32} y={y + 23} fontSize={11} fontWeight={700}
                fill="rgba(255,255,255,0.9)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                Target Network
              </text>

              {/* Endpoint count */}
              <text x={cx} y={y + 42} textAnchor="middle" fontSize={18} fontWeight={700}
                fill="#64b5f6" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {assetNodes.length}
              </text>
              <text x={cx} y={y + 56} textAnchor="middle" fontSize={9}
                fill="rgba(255,255,255,0.4)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                endpoint{assetNodes.length !== 1 ? 's' : ''}
              </text>

              {/* Separator */}
              <line x1={x + 12} y1={y + 66} x2={x + GN_W - 12} y2={y + 66}
                stroke="rgba(255,255,255,0.08)" strokeWidth={1} style={{ pointerEvents: 'none' }} />

              {/* Contract names */}
              {visibleNames.map((name, idx) => (
                <text key={idx} x={cx} y={y + 80 + idx * 14} textAnchor="middle"
                  fontSize={8.5} fontWeight={500}
                  fill="rgba(255,255,255,0.6)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                  {name.length > 26 ? name.slice(0, 24) + '…' : name}
                </text>
              ))}
              {moreCount > 0 && (
                <text x={cx} y={y + 80 + visibleNames.length * 14} textAnchor="middle"
                  fontSize={8} fill="rgba(255,255,255,0.3)"
                  style={{ pointerEvents: 'none', userSelect: 'none' }}>
                  +{moreCount} more
                </text>
              )}

              {/* Expand hint */}
              <text x={cx} y={y + GN_H - 10} textAnchor="middle" fontSize={8.5} fontWeight={600}
                fill="rgba(100,180,255,0.65)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                ▸ click to expand
              </text>
            </g>
          );
        })()}

        {/* ═══════════════════════════════════════════════════════════════════
            ENDPOINT NODES  (same as V1 — only when expanded)
        ═══════════════════════════════════════════════════════════════════ */}
        {endpointsExpanded && (() => {
          const collapseBtn = (
            <g style={{ cursor: 'pointer' }}
              onClick={(e) => { e.stopPropagation(); setEndpointsExpanded(false); }}
            >
              <rect x={groupNodePos.cx - 66} y={groupNodePos.cy - 80}
                width={132} height={22} rx={11}
                fill="rgba(14,18,36,0.92)" stroke="rgba(100,180,255,0.35)" strokeWidth={1.2} />
              <text x={groupNodePos.cx} y={groupNodePos.cy - 65}
                textAnchor="middle" fontSize={9} fontWeight={600}
                fill="rgba(100,180,255,0.7)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                ▾ collapse endpoints
              </text>
            </g>
          );

          const renderNode = (nodeId: string, cx: number, cy: number) => {
            const node = nodes.find((n) => n.node_id === nodeId);
            if (!node) return null;
            const isUntouched = node.node_untouched === true || !nodesInAnyPath.has(nodeId);
            const status  = isUntouched ? 'pending' : getNodeStatus(node);
            const colors  = STATUS_COLORS[status];
            const opacity = isUntouched && !targetAssetIds.has(nodeId) && !sourceAssetIds.has(nodeId)
              ? Math.min(getNodeOpacity(nodeId), 0.30) : getNodeOpacity(nodeId);
            const scale   = getNodeScale(nodeId);
            const isTarget = targetAssetIds.has(nodeId);
            const isSource = sourceAssetIds.has(nodeId);
            const filterId = (isTarget || isSource)
              ? (isUntouched ? 'glow-v4u-untouched' : `glow-v4u-${status}`) : undefined;
            const label = node.node_label.length > 14 ? `${node.node_label.slice(0, 12)}…` : node.node_label;
            return (
              <g key={nodeId} opacity={opacity}
                transform={scale !== 1 ? `translate(${cx},${cy}) scale(${scale}) translate(${-cx},${-cy})` : undefined}
                style={{ cursor: 'pointer', transition: 'opacity 0.25s' }}
                onClick={(e) => { e.stopPropagation(); onNodeClick?.(nodeId); }}
                onMouseEnter={(e) => { e.stopPropagation(); setNodeTooltip({ node, x: e.clientX, y: e.clientY }); setBadgeTooltip(null); }}
                onMouseMove={(e) => { e.stopPropagation(); setNodeTooltip((p) => p ? { ...p, x: e.clientX, y: e.clientY } : null); }}
                onMouseLeave={(e) => { e.stopPropagation(); setNodeTooltip(null); }}
              >
                {filterId && <circle cx={cx} cy={cy} r={NODE_R + (isTarget ? 12 : 8)} fill={isUntouched ? '#9e9e9e' : colors.fill} opacity={isTarget ? 0.18 : 0.10} filter={`url(#${filterId})`} />}
                {node.node_is_entry_point && <circle cx={cx} cy={cy} r={NODE_R + 8} fill="none" stroke="#fff" strokeWidth={1.5} opacity={0.6} strokeDasharray="4 3" />}
                {node.node_is_pivot && <circle cx={cx} cy={cy} r={NODE_R + 5} fill="none" stroke={colors.fill} strokeWidth={1.2} opacity={0.45} strokeDasharray="3 3" />}
                {isSource && !isTarget && <text x={cx} y={cy - NODE_R - 6} textAnchor="middle" fontSize={11} fill="#ff9800" style={{ pointerEvents: 'none' }}>↑</text>}
                <circle cx={cx} cy={cy} r={NODE_R}
                  fill={isUntouched ? 'rgba(30,30,46,0.7)' : 'rgba(20,20,36,0.95)'}
                  stroke={isUntouched ? '#616161' : colors.fill}
                  strokeWidth={isTarget ? 3.5 : isSource ? 2.5 : 2}
                />
                <circle cx={cx} cy={cy} r={NODE_R - 7}
                  fill={isUntouched ? 'rgba(97,97,97,0.12)' : `${colors.fill}${isTarget ? '28' : '18'}`}
                />
                <circle cx={cx + NODE_R - 9} cy={cy - NODE_R + 9} r={6}
                  fill={isUntouched ? '#616161' : colors.fill}
                  stroke="rgba(20,20,36,1)" strokeWidth={1.5}
                />
                <text x={cx} y={cy + 4} textAnchor="middle" fontSize={9} fontWeight={700}
                  fill={isUntouched ? 'rgba(255,255,255,0.45)' : '#fff'}
                  style={{ pointerEvents: 'none' }}>{label}</text>
                {node.node_ip && <text x={cx} y={cy + 15} textAnchor="middle" fontSize={8} fill="rgba(255,255,255,0.45)" style={{ pointerEvents: 'none' }}>{node.node_ip}</text>}
                <text x={cx} y={cy + NODE_R + 16} textAnchor="middle" fontSize={10} fontWeight={600}
                  fill={isUntouched ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.85)'}
                  style={{ pointerEvents: 'none' }}>
                  {node.node_label.length > 18 ? `${node.node_label.slice(0, 16)}…` : node.node_label}
                </text>
              </g>
            );
          };

          // ── Stacked finding group zone boxes + collapsed cards (always) ──
          type StackEntry = {
            x: number; y: number; width: number; height: number;
            isExpanded: boolean;
            nodePositions: Map<string, { cx: number; cy: number }>;
          };
          const stackEntries = CATEGORY_ORDER
            .map((cat) => {
              const entry = findingGroupStack.get(cat) as StackEntry | undefined;
              if (!entry) return null;
              const assetIds = Array.from(assetCategories.entries())
                .filter(([, c]) => c === cat).map(([id]) => id);
              if (assetIds.length === 0) return null;
              return { cat, assetIds, entry, meta: FINDING_META[cat] };
            })
            .filter(Boolean) as Array<{
              cat: FindingCategory; assetIds: string[];
              entry: StackEntry;
              meta: { label: string; icon: string; color: string };
            }>;

          const toggleGroup = (cat: FindingCategory) => {
            setExpandedFindingGroups((prev) => {
              const next = new Set(prev);
              if (next.has(cat)) next.delete(cat); else next.add(cat);
              return next;
            });
          };

          return (
            <>
              {collapseBtn}

              {/* ── Expanded zone boxes (rendered first — behind nodes) ── */}
              {stackEntries.filter((e) => e.entry.isExpanded).map(({ cat, assetIds, entry, meta }) => {
                const { x, y, width, height } = entry;
                return (
                  <g key={`fgzone-${cat}`}>
                    <rect x={x - 4} y={y - 4} width={width + 8} height={height + 8} rx={16}
                      fill={meta.color} opacity={0.04} style={{ pointerEvents: 'none' }} />
                    <rect x={x} y={y} width={width} height={height} rx={14}
                      fill={`${meta.color}0C`} stroke={meta.color} strokeWidth={1.6} strokeOpacity={0.5}
                      strokeDasharray="8 5" />
                    <rect x={x} y={y} width={width} height={FG_HEADER_H} rx={14}
                      fill={`${meta.color}22`} style={{ pointerEvents: 'none' }} />
                    <rect x={x} y={y + FG_HEADER_H - 10} width={width} height={10}
                      fill={`${meta.color}22`} style={{ pointerEvents: 'none' }} />
                    <text x={x + 16} y={y + 30} fontSize={12} fontWeight={700} fill={meta.color}
                      style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {meta.icon}{'  '}{meta.label}
                    </text>
                    <text x={x + width - 56} y={y + 30} fontSize={10} fill="rgba(255,255,255,0.45)"
                      style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {assetIds.length} endpoint{assetIds.length !== 1 ? 's' : ''}
                    </text>
                    <g style={{ cursor: 'pointer' }} onClick={(e) => { e.stopPropagation(); toggleGroup(cat); }}>
                      <circle cx={x + width - 18} cy={y + FG_HEADER_H / 2} r={11}
                        fill="rgba(255,255,255,0.06)" stroke="rgba(255,255,255,0.22)" strokeWidth={1.2} />
                      <text x={x + width - 18} y={y + FG_HEADER_H / 2 + 4} textAnchor="middle"
                        fontSize={11} fill="rgba(255,255,255,0.65)"
                        style={{ pointerEvents: 'none', userSelect: 'none' }}>✕</text>
                    </g>
                  </g>
                );
              })}

              {/* ── Collapsed group cards ── */}
              {stackEntries.filter((e) => !e.entry.isExpanded).map(({ cat, assetIds, entry, meta }) => {
                const { x, y, width, height } = entry;
                const cx = x + width / 2; const cy = y + height / 2;
                return (
                  <g key={`fgcard-${cat}`} style={{ cursor: 'pointer' }}
                    onClick={(e) => { e.stopPropagation(); toggleGroup(cat); }}>
                    <rect x={x - 4} y={y - 4} width={width + 8} height={height + 8} rx={12}
                      fill={meta.color} opacity={0.06} style={{ pointerEvents: 'none' }} />
                    <rect x={x} y={y} width={width} height={height} rx={9}
                      fill="rgba(14,18,32,0.96)" stroke={meta.color} strokeWidth={1.8} strokeOpacity={0.65} />
                    <rect x={x} y={y} width={5} height={height} rx={4}
                      fill={meta.color} opacity={0.7} style={{ pointerEvents: 'none' }} />
                    <text x={x + 20} y={cy + 5} textAnchor="middle" fontSize={14}
                      style={{ pointerEvents: 'none', userSelect: 'none' }}>{meta.icon}</text>
                    <text x={x + 36} y={cy - 5} textAnchor="start" fontSize={10} fontWeight={700}
                      fill={meta.color} style={{ pointerEvents: 'none', userSelect: 'none' }}>{meta.label}</text>
                    <text x={x + 36} y={cy + 9} textAnchor="start" fontSize={9}
                      fill="rgba(255,255,255,0.5)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {assetIds.length} endpoint{assetIds.length !== 1 ? 's' : ''} · ▸ expand
                    </text>
                    <circle cx={x + width - 20} cy={cy} r={15}
                      fill={`${meta.color}22`} stroke={meta.color} strokeWidth={1.2} opacity={0.7}
                      style={{ pointerEvents: 'none' }} />
                    <text x={x + width - 20} y={cy + 4} textAnchor="middle" fontSize={10} fontWeight={700}
                      fill={meta.color} style={{ pointerEvents: 'none', userSelect: 'none' }}>{assetIds.length}</text>
                  </g>
                );
              })}

              {/* ── Nodes inside expanded zone boxes ── */}
              {stackEntries.filter((e) => e.entry.isExpanded).map(({ cat, entry }) => (
                <g key={`fgnodes-${cat}`}>
                  {Array.from(entry.nodePositions.entries()).map(([nodeId, { cx, cy }]) =>
                    renderNode(nodeId, cx, cy)
                  )}
                </g>
              ))}
            </>
          );
        })()}

        {/* ═══════════════════════════════════════════════════════════════════
            INJECTOR → CONTRACT CONNECTOR LINES  (visible when expanded)
        ═══════════════════════════════════════════════════════════════════ */}
        {injectorGroups.map((group) => {
          if (!expandedInjectors.has(group.tool) && !autoExpandedInjectors.has(group.tool)) return null;
          const injPos = injectorPositions.get(group.tool);
          if (!injPos) return null;
          // V4U3: when path selected, only draw lines to path contracts
          const pathObj2 = selectedPathId ? paths.find((p) => p.path_id === selectedPathId) : null;
          const pathAssetIds2 = pathObj2 ? new Set(pathObj2.node_ids) : null;
          const selAsset = selectedActionNodeId
            ? nodes.find((n) => n.node_id === selectedActionNodeId && n.node_type === 'ASSET')
            : null;
          const dimmedGroupExp = expandedDimmedGroups.has(group.tool);
          const showAll = (!selAsset && !pathAssetIds2) || dimmedGroupExp;
          if (showAll) {
            return group.contracts.map(({ action }) => {
              const cp = contractPositions.get(action.node_id);
              if (!cp) return null;
              return (
                <path key={`conn-${action.node_id}`}
                  d={`M ${injPos.cx + INJECTOR_R} ${injPos.cy} Q ${(injPos.cx + INJECTOR_R + cp.cx - CONTRACT_R) / 2} ${Math.min(injPos.cy, cp.cy) - 30} ${cp.cx - CONTRACT_R} ${cp.cy}`}
                  stroke={group.color} strokeWidth={1.2} strokeDasharray="4 3" fill="none" opacity={0.38}
                  style={{ pointerEvents: 'none' }}
                />
              );
            });
          }
          const relevantContracts = selAsset
            ? group.contracts.filter((c) => c.targetAssetId === selectedActionNodeId)
            : group.contracts.filter((c) => pathAssetIds2!.has(c.targetAssetId));
          const dimmedContracts = selAsset
            ? group.contracts.filter((c) => c.targetAssetId !== selectedActionNodeId)
            : group.contracts.filter((c) => !pathAssetIds2!.has(c.targetAssetId));
          const lines: JSX.Element[] = relevantContracts.flatMap(({ action }) => {
            const cp = contractPositions.get(action.node_id);
            if (!cp) return [];
            return [(<path key={`conn-${action.node_id}`} d={`M ${injPos.cx + INJECTOR_R} ${injPos.cy} Q ${(injPos.cx + INJECTOR_R + cp.cx - CONTRACT_R) / 2} ${Math.min(injPos.cy, cp.cy) - 30} ${cp.cx - CONTRACT_R} ${cp.cy}`} stroke={group.color} strokeWidth={1.2} strokeDasharray="4 3" fill="none" opacity={0.38} style={{ pointerEvents: 'none' }} />)];
          });
          if (dimmedContracts.length > 0) {
            const positions = dimmedContracts.map((c) => contractPositions.get(c.action.node_id)).filter(Boolean) as Array<{ cx: number; cy: number }>;
            if (positions.length > 0) {
              const avgY = positions.reduce((s, p) => s + p.cy, 0) / positions.length;
              lines.push(<path key={`conn-dim-${group.tool}`}
                d={`M ${injPos.cx + INJECTOR_R} ${injPos.cy} Q ${(injPos.cx + INJECTOR_R + CONTRACT_X - CONTRACT_R) / 2} ${Math.min(injPos.cy, avgY) - 25} ${CONTRACT_X - CONTRACT_R} ${avgY}`}
                stroke={group.color} strokeWidth={1.2} strokeDasharray="4 3" fill="none" opacity={0.18}
                style={{ pointerEvents: 'none' }}
              />);
            }
          }
          return lines;
        })}

        {/* ═══════════════════════════════════════════════════════════════════
            CONTRACT NODES  (one per injector action; shown when expanded)
        ═══════════════════════════════════════════════════════════════════ */}
        {injectorGroups.map((group) => {
          if (!expandedInjectors.has(group.tool) && !autoExpandedInjectors.has(group.tool)) return null;
          // Determine if endpoint filter is active for this tool
          const selectedAssetNode = selectedActionNodeId
            ? nodes.find((n) => n.node_id === selectedActionNodeId && n.node_type === 'ASSET')
            : null;
          const dimmedGroupExpanded = expandedDimmedGroups.has(group.tool);

          // Path filter: when path selected (and no endpoint), only show path-targeted contracts
          const pathObj = selectedPathId ? paths.find((p) => p.path_id === selectedPathId) : null;
          const pathAssetIds = pathObj ? new Set(pathObj.node_ids) : null;

          // showAll: no filter active at all, or user manually expanded dimmed group
          const showAll = (!selectedAssetNode && !pathAssetIds) || dimmedGroupExpanded;

          const visibleContracts = showAll
            ? group.contracts
            : selectedAssetNode
              ? group.contracts.filter((c) => c.targetAssetId === selectedActionNodeId)
              : group.contracts.filter((c) => pathAssetIds!.has(c.targetAssetId));

          const dimmedContracts = showAll
            ? []
            : selectedAssetNode
              ? group.contracts.filter((c) => c.targetAssetId !== selectedActionNodeId)
              : group.contracts.filter((c) => !pathAssetIds!.has(c.targetAssetId));

          return (
            <g key={`contracts-${group.tool}`}>
              {/* Visible contracts */}
              {visibleContracts.map(({ action, targetAssetId }) => {
                const pos = contractPositions.get(action.node_id);
                if (!pos) return null;
                const { cx, cy } = pos;
                const sc = contractStatusColor(action);
                const statusKey = sc === STATUS_COLORS.prevented.fill ? 'prevented'
                  : sc === STATUS_COLORS.detected.fill ? 'detected' : 'undetected';
                const truncLabel = action.node_label.length > 20
                  ? action.node_label.slice(0, 18) + '…'
                  : action.node_label;
                return (
                  <g key={`contract-${action.node_id}`}
                    style={{ cursor: 'pointer' }}
                    onClick={(e) => { e.stopPropagation(); onNodeClick?.(action.node_id); }}
                    onMouseEnter={(e) => {
                      e.stopPropagation();
                      const tn = assetNodes.find((n) => n.node_id === targetAssetId);
                      setContractTip({ action, targetLabel: tn?.node_label ?? targetAssetId, tool: group.tool, x: e.clientX, y: e.clientY });
                    }}
                    onMouseMove={(e) => { e.stopPropagation(); setContractTip((p) => p ? { ...p, x: e.clientX, y: e.clientY } : null); }}
                    onMouseLeave={(e) => { e.stopPropagation(); setContractTip(null); }}
                  >
                    <polygon points={diamondPoints(cx, cy, CONTRACT_R + 10)} fill={sc} opacity={0.07} filter={`url(#glow-v4u-${statusKey})`} />
                    <polygon points={diamondPoints(cx, cy, CONTRACT_R)} fill="rgba(14,14,26,0.97)" stroke={sc} strokeWidth={2} />
                    <polygon points={diamondPoints(cx, cy, CONTRACT_R - 6)} fill={`${sc}14`} />
                    <circle cx={cx + CONTRACT_R - 7} cy={cy - CONTRACT_R + 7} r={5}
                      fill={group.color} stroke="rgba(14,14,26,1)" strokeWidth={1.5} />
                    <text x={cx} y={cy - 3} textAnchor="middle" fontSize={6.5} fontWeight={700}
                      fill={group.color} style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {group.tool}
                    </text>
                    <text x={cx} y={cy + 7} textAnchor="middle" fontSize={6.5} fontWeight={500}
                      fill="rgba(255,255,255,0.7)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {action.node_label.length > 10 ? action.node_label.slice(0, 9) + '…' : action.node_label}
                    </text>
                    <text x={cx + CONTRACT_R + 6} y={cy + 4} textAnchor="start" fontSize={9} fontWeight={600}
                      fill="rgba(255,255,255,0.82)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                      {truncLabel}
                    </text>
                  </g>
                );
              })}

              {/* Dimmed group badge for non-relevant contracts — click to expand/collapse */}
              {dimmedContracts.length > 0 && (() => {
                const positions = dimmedContracts
                  .map((c) => contractPositions.get(c.action.node_id))
                  .filter(Boolean) as Array<{ cx: number; cy: number }>;
                if (positions.length === 0) return null;
                const avgY = positions.reduce((s, p) => s + p.cy, 0) / positions.length;
                const cx = CONTRACT_X;
                const cy = avgY;
                return (
                  <g key={`dimmed-group-${group.tool}`}
                    style={{ cursor: 'pointer' }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setExpandedDimmedGroups((prev) => {
                        const next = new Set(prev);
                        if (next.has(group.tool)) next.delete(group.tool);
                        else next.add(group.tool);
                        return next;
                      });
                    }}
                  >
                    <polygon points={diamondPoints(cx, cy, CONTRACT_R + 10)} fill="transparent" />
                    <polygon points={diamondPoints(cx, cy, CONTRACT_R)} fill="rgba(14,14,26,0.88)"
                      stroke={group.color} strokeWidth={1.5} strokeDasharray="4 3" opacity={0.45} />
                    <text x={cx} y={cy - 2} textAnchor="middle" fontSize={9} fontWeight={700}
                      fill={group.color} opacity={0.55} style={{ userSelect: 'none' }}>
                      +{dimmedContracts.length}
                    </text>
                    <text x={cx} y={cy + 9} textAnchor="middle" fontSize={7}
                      fill="rgba(255,255,255,0.4)" style={{ userSelect: 'none' }}>
                      more ▸
                    </text>
                  </g>
                );
              })()}
            </g>
          );
        })}

        {/* ═══════════════════════════════════════════════════════════════════
            INJECTOR NODES  (one per tool, always visible — click to expand)
        ═══════════════════════════════════════════════════════════════════ */}
        {injectorGroups.map((group) => {
          const injPos = injectorPositions.get(group.tool);
          if (!injPos) return null;
          const { cx, cy } = injPos;
          const isExpanded = expandedInjectors.has(group.tool) || autoExpandedInjectors.has(group.tool);
          return (
            <g key={`injector-${group.tool}`} style={{ cursor: 'pointer' }}
              onClick={(e) => {
                e.stopPropagation();
                setExpandedInjectors((prev) => {
                  const next = new Set(prev);
                  if (next.has(group.tool)) next.delete(group.tool); else next.add(group.tool);
                  return next;
                });
              }}
            >
              <polygon points={hexPoints(cx, cy, INJECTOR_R + 16)}
                fill={group.color} opacity={0.06} filter="url(#glow-v4u-undetected)" />
              <polygon points={hexPoints(cx, cy, INJECTOR_R)}
                fill="rgba(14,14,26,0.97)" stroke={group.color} strokeWidth={2.2} />
              <polygon points={hexPoints(cx, cy, INJECTOR_R - 8)}
                fill={`${group.color}14`} style={{ pointerEvents: 'none' }} />
              <circle cx={cx + INJECTOR_R - 9} cy={cy - INJECTOR_R + 10} r={7}
                fill={group.color} stroke="rgba(14,14,26,1)" strokeWidth={1.5} />
              <text x={cx + INJECTOR_R - 9} y={cy - INJECTOR_R + 14} textAnchor="middle"
                fontSize={8} fontWeight={700} fill="#000"
                style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {group.contracts.length}
              </text>
              <text x={cx} y={cy + 5} textAnchor="middle" fontSize={10} fontWeight={700}
                fill={group.color} style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {group.tool}
              </text>
              <text x={cx} y={cy + INJECTOR_R + 30} textAnchor="middle" fontSize={8}
                fill="rgba(255,255,255,0.4)" style={{ pointerEvents: 'none', userSelect: 'none' }}>
                {isExpanded ? '▾' : '▸'} {group.contracts.length} action{group.contracts.length !== 1 ? 's' : ''}
              </text>
            </g>
          );
        })}

      </svg>

      {/* ── Node tooltip ── */}
      {nodeTooltip && (
        <div style={{ position: 'fixed', left: nodeTooltip.x + 15, top: nodeTooltip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(15,15,25,0.97)', border: '1px solid rgba(255,255,255,0.15)', borderRadius: 6, padding: '8px 12px', minWidth: 200, maxWidth: 280, boxShadow: '0 4px 20px rgba(0,0,0,0.6)' }}>
          <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#fff' }}>{nodeTooltip.node.node_hostname ?? nodeTooltip.node.node_label}</div>
          {nodeTooltip.node.node_ip && <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}><span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>IP</span><span style={{ fontSize: 10, fontFamily: 'monospace', color: '#64b5f6' }}>{nodeTooltip.node.node_ip}</span></div>}
          {nodeTooltip.node.node_platform && <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}><span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Platform</span><span style={{ fontSize: 10, opacity: 0.8 }}>{nodeTooltip.node.node_platform}</span></div>}
          {nodeTooltip.node.node_zone && <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}><span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Zone</span><span style={{ fontSize: 10, opacity: 0.75 }}>{nodeTooltip.node.node_zone}</span></div>}
          {nodeTooltip.node.node_is_entry_point && <div style={{ marginTop: 4, fontSize: 10, color: '#ffd54f' }}>★ Entry point</div>}
          {nodeTooltip.node.node_is_pivot && <div style={{ marginTop: 2, fontSize: 10, color: '#ff9800' }}>↔ Pivot node</div>}
        </div>
      )}

      {/* ── Contract node tooltip ── */}
      {contractTip && (
        <div style={{ position: 'fixed', left: contractTip.x + 15, top: contractTip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(10,12,22,0.98)', border: `1px solid ${getToolColor(contractTip.tool)}55`, borderRadius: 8, padding: '10px 14px', minWidth: 240, maxWidth: 300, boxShadow: '0 6px 28px rgba(0,0,0,0.7)' }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#fff', marginBottom: 6 }}>{contractTip.action.node_label}</div>
          <div style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.08)', marginBottom: 6 }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <div style={{ display: 'flex', gap: 8 }}>
              <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Tool</span>
              <span style={{ fontSize: 10, fontFamily: 'monospace', color: getToolColor(contractTip.tool), fontWeight: 700 }}>{contractTip.tool}</span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Target</span>
              <span style={{ fontSize: 10, color: 'rgba(255,255,255,0.8)' }}>{contractTip.targetLabel}</span>
            </div>
            {contractTip.action.node_status && (
              <div style={{ display: 'flex', gap: 8 }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Status</span>
                <span style={{ fontSize: 10, color: contractStatusColor(contractTip.action), fontWeight: 600 }}>{contractTip.action.node_status}</span>
              </div>
            )}
            {contractTip.action.node_payload_name && (
              <div style={{ display: 'flex', gap: 8 }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Payload</span>
                <span style={{ fontSize: 10, fontFamily: 'monospace', color: 'rgba(255,255,255,0.6)' }}>{contractTip.action.node_payload_name}</span>
              </div>
            )}
            {contractTip.action.node_executed_at && (
              <div style={{ display: 'flex', gap: 8, paddingTop: 4, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Executed</span>
                <span style={{ fontSize: 9, fontFamily: 'monospace', color: 'rgba(255,255,255,0.5)' }}>{contractTip.action.node_executed_at}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Badge tooltip ── */}
      {badgeTooltip && (
        <div style={{ position: 'fixed', left: badgeTooltip.x + 15, top: badgeTooltip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(10,12,22,0.98)', border: `1px solid ${badgeTooltip.segColor}55`, borderRadius: 8, padding: '10px 14px', minWidth: 240, maxWidth: 320, boxShadow: '0 6px 28px rgba(0,0,0,0.7)' }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: badgeTooltip.segColor, marginBottom: 6 }}>{badgeTooltip.label}</div>
          {badgeTooltip.detail ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.08)', marginBottom: 2 }} />
              {badgeTooltip.detail.trigger_event && <div style={{ display: 'flex', gap: 8 }}><span style={{ fontSize: 9, fontWeight: 700, color: '#ff9800', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Event</span><span style={{ fontSize: 10, color: 'rgba(255,255,255,0.85)' }}>{badgeTooltip.detail.trigger_event}</span></div>}
              {badgeTooltip.detail.condition && <div style={{ display: 'flex', gap: 8 }}><span style={{ fontSize: 9, fontWeight: 700, color: '#64b5f6', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>Condition</span><span style={{ fontSize: 10, color: 'rgba(255,255,255,0.75)', lineHeight: 1.4 }}>{badgeTooltip.detail.condition}</span></div>}
              {badgeTooltip.detail.technique && <div style={{ display: 'flex', gap: 8, marginTop: 2, paddingTop: 4, borderTop: '1px solid rgba(255,255,255,0.06)' }}><span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5 }}>ATT&CK</span><span style={{ fontSize: 9, fontFamily: 'monospace', color: '#80cbc4' }}>{badgeTooltip.detail.technique}</span></div>}
            </div>
          ) : (
            <div style={{ fontSize: 10, color: 'rgba(255,255,255,0.35)', fontStyle: 'italic' }}>No event detail available.</div>
          )}
        </div>
      )}

      {/* ── Zoom controls ── */}
      <div style={{ position: 'absolute', bottom: 16, right: 16, display: 'flex', flexDirection: 'column', gap: 4, backgroundColor: 'rgba(15,15,25,0.85)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 8, padding: 4 }}>
        <IconButton size="small" onClick={() => zoomFn(0.8)} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}><ZoomInIcon sx={{ fontSize: 14 }} /></IconButton>
        <IconButton size="small" onClick={() => setViewBox({ x: 0, y: 0, w: svgWidth, h: svgHeight })} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}><FitIcon sx={{ fontSize: 14 }} /></IconButton>
        <IconButton size="small" onClick={() => zoomFn(1.25)} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}><ZoomOutIcon sx={{ fontSize: 14 }} /></IconButton>
      </div>
    </div>
  );
};

export default AttackPathGraphV4U3;
