/**
 * AttackPathGraphV2 — Annotated Path Map + All Node Actions (Updated Variant 1)
 *
 * Identical to V1 (organic layout, edge reason badges, arrowheads, per-path colors)
 * with one key addition: every action that ran on each endpoint is rendered as a
 * small chip stack below/beside the node — giving defenders a full picture of
 * what the attacker executed, not just what caused lateral movement.
 *
 * Chip color semantics (defender perspective):
 *   GREEN  = prevented / failed    → attacker was blocked  ✓
 *   ORANGE = detected / partial    → seen but not stopped
 *   RED    = success / undetected  → attacker succeeded    ✗
 */

import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useCallback, useMemo, useRef, useState } from 'react';
import {
  type AttackPathNode,
  type AttackPathEdge,
  type AttackPathDefinition,
  getActionsForAssetFull,
  getNodeStatus,
  getPathOutcomeColor,
  STATUS_COLORS,
} from './attackPathUtils';

interface Props {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  paths: AttackPathDefinition[];
  selectedActionNodeId: string | null;
  onNodeClick?: (assetId: string) => void;
  onPathClick?: (assetNodeIds: string[]) => void;
  onBadgeClick?: (destAssetId: string) => void;
  /** V2: clicking an action chip focuses that specific action in the feed */
  onActionChipClick?: (actionNodeId: string) => void;
  onLegendPathSelect?: (path: AttackPathDefinition | null) => void;
  height?: string;
}

// ── Deterministic seed ────────────────────────────────────────────────────────
function seededFloat(seed: string): number {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) h = ((h << 5) + h) ^ seed.charCodeAt(i);
  return (h >>> 0) / 0xffffffff;
}

// ── Auto-generated segment reasons (fallback) ─────────────────────────────────
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

/** Map action outcome → defender-perspective color */
function getActionChipColor(actionNode: AttackPathNode): string {
  const s = (actionNode.node_status ?? '').toLowerCase();
  if (s === 'prevented' || s === 'failed') return STATUS_COLORS.prevented.fill;   // green
  if (s === 'detected'  || s === 'partial') return STATUS_COLORS.detected.fill;   // orange
  return STATUS_COLORS.undetected.fill;                                            // red
}

// ── Layout ────────────────────────────────────────────────────────────────────
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
  const BASE_X = 150; const BASE_Y = 160;
  // V2: extra vertical spacing to accommodate per-node chip stacks (no vertical jitter overlap)
  const STEP_X = 278; const STEP_Y = 310;
  const STAGGER = STEP_X * 0.5;
  const JX_MAX = 22; const JY_MAX = 14;
  return ordered.map((nodeId, i) => {
    const col = i % COLS; const row = Math.floor(i / COLS);
    const staggerX = (row % 2) * STAGGER;
    const rowNudge = (seededFloat(`row${row}nudge`) - 0.5) * 18;
    const jx = (seededFloat(nodeId + 'jx') - 0.5) * JX_MAX * 2;
    const jy = (seededFloat(nodeId + 'jy') - 0.5) * JY_MAX * 2;
    return { nodeId, cx: BASE_X + col * STEP_X + staggerX + jx, cy: BASE_Y + row * STEP_Y + rowNudge + jy };
  });
}

const NODE_R = 36;
const ARROW_SIZE = 8;
const ARROW_LEN  = 13;
const CHIP_H = 15;
const CHIP_MAX = 4;   // max chips shown before "+N more"
const CHIP_W  = 142;  // fixed chip width

/** Compute SVG polygon points for an arrowhead on segment A→B, tip just outside B's circle */
function arrowPoints(ax: number, ay: number, bx: number, by: number): string | null {
  const dist = Math.sqrt((bx - ax) ** 2 + (by - ay) ** 2);
  if (dist < NODE_R * 2 + ARROW_LEN + 24) return null;
  const ux = (bx - ax) / dist; const uy = (by - ay) / dist;
  const tipX = bx - ux * (NODE_R + 3); const tipY = by - uy * (NODE_R + 3);
  const baseX = tipX - ux * ARROW_LEN; const baseY = tipY - uy * ARROW_LEN;
  const px = -uy; const py = ux;
  return `${tipX},${tipY} ${baseX - px * ARROW_SIZE},${baseY - py * ARROW_SIZE} ${baseX + px * ARROW_SIZE},${baseY + py * ARROW_SIZE}`;
}

// ── Tooltip types ─────────────────────────────────────────────────────────────
interface BadgeDetail {
  label: string;
  detail: NonNullable<AttackPathDefinition['path_segment_details']>[string] | null;
  x: number;
  y: number;
  segColor: string;
}

interface ChipTooltip {
  actionNode: AttackPathNode;
  chipColor: string;
  x: number;
  y: number;
}

// ── Component ─────────────────────────────────────────────────────────────────
const AttackPathGraphV2: FunctionComponent<Props> = ({
  nodes, edges, paths, selectedActionNodeId,
  onNodeClick, onPathClick, onBadgeClick, onActionChipClick, onLegendPathSelect, height = '100%',
}) => {
  const [nodeTooltip, setNodeTooltip] = useState<{ node: AttackPathNode; x: number; y: number } | null>(null);
  const [badgeTooltip, setBadgeTooltip] = useState<BadgeDetail | null>(null);
  const [chipTooltip, setChipTooltip] = useState<ChipTooltip | null>(null);

  // ── Zoom / Pan ────────────────────────────────────────────────────────────
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 1200, h: 800 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

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
    if (e.button === 0) { setIsPanning(true); setPanStart({ x: e.clientX, y: e.clientY }); }
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!isPanning) return;
    const dx = (e.clientX - panStart.x) * (viewBox.w / (svgRef.current?.clientWidth ?? 1));
    const dy = (e.clientY - panStart.y) * (viewBox.h / (svgRef.current?.clientHeight ?? 1));
    setViewBox((vb) => ({ ...vb, x: vb.x - dx, y: vb.y - dy }));
    setPanStart({ x: e.clientX, y: e.clientY });
  }, [isPanning, panStart, viewBox]);

  const handleMouseUp = useCallback(() => setIsPanning(false), []);

  // ── Legend state ──────────────────────────────────────────────────────────
  const [legendSelectedPathId, setLegendSelectedPathId] = useState<string | null>(null);
  const handleLegendClick = useCallback((pathId: string) => {
    setLegendSelectedPathId((prev) => {
      const next = prev === pathId ? null : pathId;
      const pathDef = next ? paths.find((p) => p.path_id === next) ?? null : null;
      onLegendPathSelect?.(pathDef);
      return next;
    });
  }, [paths, onLegendPathSelect]);

  // ── Data ──────────────────────────────────────────────────────────────────
  const assetNodes = useMemo(() => nodes.filter((n) => n.node_type === 'ASSET'), [nodes]);
  const nodeCoords = useMemo(() => computeFlatLayout(assetNodes, paths), [assetNodes, paths]);
  const svgWidth  = useMemo(() => Math.max(1200, ...nodeCoords.map((c) => c.cx + NODE_R + 100)), [nodeCoords]);
  const svgHeight = useMemo(() => Math.max(800,  ...nodeCoords.map((c) => c.cy + NODE_R + 120)), [nodeCoords]);

  const coordMap = useMemo(() => {
    const m = new Map<string, { cx: number; cy: number }>();
    for (const c of nodeCoords) m.set(c.nodeId, c);
    return m;
  }, [nodeCoords]);

  const outcomeIndexMap = useMemo(() => {
    const map = new Map<string, number>();
    const counts: Record<string, number> = {};
    for (const path of paths) {
      const o = path.path_outcome ?? 'success';
      map.set(path.path_id, counts[o] ?? 0);
      counts[o] = (counts[o] ?? 0) + 1;
    }
    return map;
  }, [paths]);

  const nodesInAnyPath = useMemo(() => {
    const set = new Set<string>();
    for (const path of paths) for (const id of path.node_ids) set.add(id);
    return set;
  }, [paths]);

  /** Map: assetId → label of the first action (for edge badge text to match feed) */
  const assetToActionLabelMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const node of nodes) {
      if (node.node_type !== 'ASSET') continue;
      const acts = getActionsForAssetFull(node, nodes, edges)
        .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));
      if (acts.length > 0) map.set(node.node_id, acts[0].node_label);
    }
    return map;
  }, [nodes, edges]);

  /** ★ NEW: Map: assetId → ALL action nodes sorted by execution time */
  const assetAllActionsMap = useMemo(() => {
    const map = new Map<string, AttackPathNode[]>();
    for (const node of assetNodes) {
      const acts = getActionsForAssetFull(node, nodes, edges)
        .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));
      if (acts.length > 0) map.set(node.node_id, acts);
    }
    return map;
  }, [assetNodes, nodes, edges]);

  /** For each path segment "A->B", lists which pathIds have a badge there (for stagger). */
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

  const targetAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    const targets = new Set<string>();
    for (const edge of edges) {
      if (edge.edge_type === 'asset_link' && edge.edge_source === selectedActionNodeId) targets.add(edge.edge_target);
    }
    return targets;
  }, [selectedActionNodeId, edges]);

  const sourceAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    const sources = new Set<string>();
    for (const edge of edges) {
      if (edge.edge_type === 'asset_link' && edge.edge_target === selectedActionNodeId) sources.add(edge.edge_source);
    }
    return sources;
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
    if (!hasSelection) return 1;
    for (const path of paths) {
      if (activePaths.has(path.path_id) && path.node_ids.includes(nodeId)) return 1;
    }
    if (targetAssetIds.has(nodeId) || sourceAssetIds.has(nodeId)) return 1;
    return 0.18;
  }, [hasSelection, activePaths, paths, targetAssetIds, sourceAssetIds]);

  const getNodeScale = useCallback((nodeId: string) => {
    if (targetAssetIds.has(nodeId) || sourceAssetIds.has(nodeId)) return 1.15;
    return 1;
  }, [targetAssetIds, sourceAssetIds]);

  // ── Path builder (same bezier as V1/V6) ──────────────────────────────────
  const buildPathD = useCallback((nodeIds: string[]): string => {
    const pts = nodeIds.map((id) => coordMap.get(id)).filter(Boolean) as { cx: number; cy: number }[];
    if (pts.length < 2) return '';
    let d = `M ${pts[0].cx} ${pts[0].cy}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i]; const p1 = pts[i + 1];
      const cp1x = p0.cx + (p1.cx - p0.cx) * 0.5;
      const cp1y = p0.cy - 50;
      const cp2x = p0.cx + (p1.cx - p0.cx) * 0.5;
      const cp2y = p1.cy - 50;
      d += ` C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p1.cx} ${p1.cy}`;
    }
    return d;
  }, [coordMap]);

  // ── Badge position: midpoint + perpendicular direction for stagger ────────
  const getBadgePos = useCallback((nodeA: string, nodeB: string) => {
    const a = coordMap.get(nodeA); const b = coordMap.get(nodeB);
    if (!a || !b) return null;
    const dist = Math.sqrt((b.cx - a.cx) ** 2 + (b.cy - a.cy) ** 2);
    if (dist < NODE_R * 2 + 50) return null;
    const mx = (a.cx + b.cx) / 2; const my = (a.cy + b.cy) / 2;
    const px = -(b.cy - a.cy) / dist; const py = (b.cx - a.cx) / dist;
    return { mx, my, px, py, dist };
  }, [coordMap]);

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
        onClick={() => { setBadgeTooltip(null); setChipTooltip(null); }}
      >
        <defs>
          {(['prevented', 'detected', 'undetected', 'pending'] as const).map((status) => (
            <filter key={`glow-v2-${status}`} id={`glow-v2-${status}`} x="-50%" y="-50%" width="200%" height="200%">
              <feDropShadow dx="0" dy="0" stdDeviation="8" floodColor={STATUS_COLORS[status].fill} floodOpacity="0.85" />
            </filter>
          ))}
          <filter id="glow-v2-untouched" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#9e9e9e" floodOpacity="0.6" />
          </filter>
        </defs>

        {/* ── Path lines ── */}
        {paths.map((path) => {
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
          const failedD  = failedIds.length >= 2  ? buildPathD(failedIds)  : '';

          let baseOpacity: number; let sw: number;
          if (!hasSelection) { baseOpacity = 0.72; sw = 2.5; }
          else if (isActive) { baseOpacity = 1.00; sw = 3.5; }
          else               { baseOpacity = 0.05; sw = 2.0; }

          const lastAssetId = path.node_ids[path.node_ids.length - 1];
          const deadCoord = isFailed ? coordMap.get(lastAssetId) : null;

          return (
            <g key={path.path_id}>
              {/* Hit area */}
              {(() => {
                const hitD = (!isFailed ? buildPathD(path.node_ids) : null) || successD || failedD;
                if (!hitD) return null;
                return (
                  <path d={hitD} fill="none" stroke="transparent" strokeWidth={20}
                    style={{ cursor: 'pointer', pointerEvents: 'stroke' }}
                    onClick={(e) => { e.stopPropagation(); onPathClick?.(path.node_ids); }}
                  />
                );
              })()}

              <g style={{ pointerEvents: 'none' }}>
                {successD && (
                  <g opacity={baseOpacity}>
                    <path d={successD} fill="none" stroke={successColor} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" />
                    {(isActive || !hasSelection) && (
                      <circle r={4} fill={successColor} opacity={0.9}>
                        <animateMotion dur="2.2s" repeatCount="indefinite" path={successD} />
                      </circle>
                    )}
                  </g>
                )}
                {failedD && (
                  <g opacity={Math.max(baseOpacity * 0.75, 0.10)}>
                    <path d={failedD} fill="none" stroke={failedSegColor} strokeWidth={sw * 0.85}
                      strokeLinecap="round" strokeLinejoin="round" strokeDasharray="8 6" />
                  </g>
                )}
                {!isFailed && (() => {
                  const fullD = buildPathD(path.node_ids);
                  if (!fullD) return null;
                  return (
                    <g opacity={baseOpacity}>
                      <path d={fullD} fill="none" stroke={successColor} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" />
                      {(isActive || !hasSelection) && (
                        <circle r={4} fill={successColor} opacity={0.9}>
                          <animateMotion dur="2.2s" repeatCount="indefinite" path={fullD} />
                        </circle>
                      )}
                    </g>
                  );
                })()}
                {isFailed && deadCoord && (isActive || !hasSelection) && (
                  <g transform={`translate(${deadCoord.cx + NODE_R - 6}, ${deadCoord.cy - NODE_R + 6})`}>
                    <circle r={9} fill="rgba(20,20,30,0.92)" stroke={`${failedSegColor}cc`} strokeWidth={1.5} />
                    <text x={0} y={4} textAnchor="middle" fontSize={11} fontWeight={700} fill={failedSegColor}
                      style={{ userSelect: 'none' }}>{path.path_outcome === 'partial' ? '!' : '✓'}</text>
                  </g>
                )}
              </g>

              {/* ── Arrowheads on each segment ── */}
              {(isActive || !hasSelection) && (() => {
                const arrowIds = successIds.length >= 2 ? successIds : (!isFailed ? path.node_ids : []);
                const successArrows = arrowIds.map((nId, i) => {
                  if (i === arrowIds.length - 1) return null;
                  const a = coordMap.get(nId); const b = coordMap.get(arrowIds[i + 1]);
                  if (!a || !b) return null;
                  const pts = arrowPoints(a.cx, a.cy, b.cx, b.cy);
                  if (!pts) return null;
                  return <polygon key={`arr-s-${path.path_id}-${i}`} points={pts} fill={successColor} opacity={baseOpacity * 0.9} style={{ pointerEvents: 'none' }} />;
                });
                const failedArrow = failedIds.length >= 2 ? (() => {
                  const a = coordMap.get(failedIds[0]); const b = coordMap.get(failedIds[failedIds.length - 1]);
                  if (!a || !b) return null;
                  const pts = arrowPoints(a.cx, a.cy, b.cx, b.cy);
                  if (!pts) return null;
                  return <polygon key={`arr-f-${path.path_id}`} points={pts} fill={failedSegColor} opacity={Math.max(baseOpacity * 0.85, 0.18) * 0.85} style={{ pointerEvents: 'none' }} />;
                })() : null;
                return <>{successArrows}{failedArrow}</>;
              })()}

              {/* ── Action badges on each edge (same as V1) ── */}
              {(isActive || !hasSelection) && path.node_ids.map((nodeId, i) => {
                if (i === path.node_ids.length - 1) return null;
                const nextNodeId = path.node_ids[i + 1];
                const pos = getBadgePos(nodeId, nextNodeId);
                if (!pos) return null;

                const details = getSegmentDetails(path, nodeId, nextNodeId);
                const label = assetToActionLabelMap.get(nextNodeId) ?? details?.action ?? getSegmentReason(path, nodeId, nextNodeId);
                const isFailedSeg = isFailed && failFromIdx >= 0 && i >= failFromIdx;
                const segColor = isFailedSeg ? failedSegColor : successColor;
                const segOpacity = isFailedSeg ? Math.max(baseOpacity * 0.85, 0.18) : baseOpacity;

                const truncLabel = label.length > 28 ? label.slice(0, 26) + '…' : label;
                const pad = 10;
                const textW = Math.min(truncLabel.length * 6.2 + pad * 2, 190);
                const bh = 20;
                const hasDetails = !!(details?.trigger_event || details?.condition || details?.technique);

                const segKey = `${nodeId}->${nextNodeId}`;
                const segGroup = segmentBadgeOrder.get(segKey) ?? [path.path_id];
                const staggerRank = segGroup.indexOf(path.path_id);
                const staggerDir = staggerRank === 0 ? 0 : (staggerRank % 2 === 1 ? 1 : -1) * Math.ceil(staggerRank / 2);
                const BADGE_SPREAD = bh + 5;
                const totalOffset = 14 + staggerDir * BADGE_SPREAD;
                const bx = pos.mx + pos.px * totalOffset;
                const by = pos.my + pos.py * totalOffset;

                return (
                  <g
                    key={`${path.path_id}-badge-${i}`}
                    transform={`translate(${bx}, ${by})`}
                    opacity={segOpacity}
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => {
                      e.stopPropagation();
                      setBadgeTooltip({ label, detail: details, x: e.clientX, y: e.clientY, segColor });
                    }}
                    onMouseMove={(e) => {
                      e.stopPropagation();
                      setBadgeTooltip((prev) => prev ? { ...prev, x: e.clientX, y: e.clientY } : null);
                    }}
                    onMouseLeave={(e) => { e.stopPropagation(); setBadgeTooltip(null); }}
                    onClick={(e) => { e.stopPropagation(); onBadgeClick?.(nextNodeId); }}
                  >
                    {totalOffset > 2 && (
                      <line x1={0} y1={0} x2={-pos.px * totalOffset} y2={-pos.py * totalOffset}
                        stroke={segColor} strokeWidth={0.8} opacity={0.35} strokeDasharray="2 2"
                        style={{ pointerEvents: 'none' }} />
                    )}
                    <rect x={-textW / 2 - 2} y={-bh / 2 - 2} width={textW + 4} height={bh + 4}
                      rx={bh / 2 + 2} ry={bh / 2 + 2} fill={`${segColor}15`} />
                    <rect x={-textW / 2} y={-bh / 2} width={textW} height={bh}
                      rx={bh / 2} ry={bh / 2} fill="rgba(10,11,20,0.97)" stroke={segColor} strokeWidth={1.5} />
                    <text x={0} y={1} textAnchor="middle" dominantBaseline="middle"
                      fontSize={9.5} fontWeight={700} fill={segColor} letterSpacing={0.2}
                      style={{ userSelect: 'none' }}>
                      {truncLabel}
                    </text>
                    {hasDetails && (
                      <circle cx={textW / 2 - 5} cy={-bh / 2 + 5} r={3.5} fill={segColor} opacity={0.75} />
                    )}
                  </g>
                );
              })}
            </g>
          );
        })}

        {/* ── Node circles + Action chip stacks ── */}
        {nodeCoords.map(({ nodeId, cx, cy }) => {
          const node = nodes.find((n) => n.node_id === nodeId);
          if (!node) return null;

          const isUntouched = node.node_untouched === true || !nodesInAnyPath.has(nodeId);
          const status   = isUntouched ? 'pending' : getNodeStatus(node);
          const colors   = STATUS_COLORS[status];
          const opacity  = isUntouched && !targetAssetIds.has(nodeId) && !sourceAssetIds.has(nodeId)
            ? Math.min(getNodeOpacity(nodeId), 0.30)
            : getNodeOpacity(nodeId);
          const scale    = getNodeScale(nodeId);
          const isTarget = targetAssetIds.has(nodeId);
          const isSource = sourceAssetIds.has(nodeId);
          const filterId = (isTarget || isSource)
            ? (isUntouched ? 'glow-v2-untouched' : `glow-v2-${status}`)
            : undefined;

          const label = node.node_label.length > 14 ? `${node.node_label.slice(0, 12)}…` : node.node_label;

          // All actions on this node (V2 new feature)
          const allActions = assetAllActionsMap.get(nodeId) ?? [];
          const visibleActions = allActions.slice(0, CHIP_MAX);
          const hiddenCount = allActions.length - visibleActions.length;

          // Chips start below the hostname label with extra clearance
          const HOSTNAME_Y = cy + NODE_R + 17;
          const CHIPS_START_Y = HOSTNAME_Y + 18;

          return (
            <g
              key={nodeId}
              opacity={opacity}
              transform={scale !== 1 ? `translate(${cx},${cy}) scale(${scale}) translate(${-cx},${-cy})` : undefined}
              style={{ transition: 'opacity 0.25s' }}
            >
              {/* Node circle — clickable */}
              <g
                style={{ cursor: 'pointer' }}
                onClick={(e) => { e.stopPropagation(); onNodeClick?.(nodeId); }}
                onMouseEnter={(e) => { e.stopPropagation(); setNodeTooltip({ node, x: e.clientX, y: e.clientY }); setBadgeTooltip(null); setChipTooltip(null); }}
                onMouseMove={(e) => { e.stopPropagation(); setNodeTooltip((prev) => prev ? { ...prev, x: e.clientX, y: e.clientY } : null); }}
                onMouseLeave={(e) => { e.stopPropagation(); setNodeTooltip(null); }}
              >
                {filterId && (
                  <circle cx={cx} cy={cy} r={NODE_R + (isTarget ? 12 : 8)}
                    fill={isUntouched ? '#9e9e9e' : colors.fill}
                    opacity={isTarget ? 0.18 : 0.10}
                    filter={`url(#${filterId})`} />
                )}
                {node.node_is_entry_point && (
                  <circle cx={cx} cy={cy} r={NODE_R + 8} fill="none"
                    stroke="#fff" strokeWidth={1.5} opacity={0.6} strokeDasharray="4 3" />
                )}
                {node.node_is_pivot && (
                  <circle cx={cx} cy={cy} r={NODE_R + 5} fill="none"
                    stroke={colors.fill} strokeWidth={1.2} opacity={0.45} strokeDasharray="3 3" />
                )}
                {isSource && !isTarget && (
                  <text x={cx} y={cy - NODE_R - 6} textAnchor="middle" fontSize={11}
                    fill="#ff9800" style={{ pointerEvents: 'none' }}>↑</text>
                )}
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
                  style={{ pointerEvents: 'none' }}>
                  {label}
                </text>
                {node.node_ip && (
                  <text x={cx} y={cy + 15} textAnchor="middle" fontSize={8}
                    fill="rgba(255,255,255,0.45)" style={{ pointerEvents: 'none' }}>
                    {node.node_ip}
                  </text>
                )}
                {/* Hostname label */}
                <text x={cx} y={HOSTNAME_Y} textAnchor="middle" fontSize={10} fontWeight={600}
                  fill={isUntouched ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.85)'}
                  style={{ pointerEvents: 'none' }}>
                  {node.node_label.length > 18 ? `${node.node_label.slice(0, 16)}…` : node.node_label}
                </text>
              </g>

              {/* ★ Action chip stack (V2 addition) */}
              {allActions.length > 0 && (
                <g>
                  {/* Thin separator line under hostname */}
                  <line
                    x1={cx - CHIP_W / 2} y1={CHIPS_START_Y - 4}
                    x2={cx + CHIP_W / 2} y2={CHIPS_START_Y - 4}
                    stroke="rgba(255,255,255,0.08)" strokeWidth={0.8}
                  />
                  {/* Section header */}
                  <text x={cx} y={CHIPS_START_Y + 2} textAnchor="middle" fontSize={7.5} fontWeight={700}
                    fill="rgba(255,255,255,0.28)" letterSpacing={0.8}
                    style={{ pointerEvents: 'none', userSelect: 'none', textTransform: 'uppercase' }}>
                    ACTIONS ({allActions.length})
                  </text>

                  {visibleActions.map((act, idx) => {
                    const chipColor = getActionChipColor(act);
                    const chipY = CHIPS_START_Y + 12 + idx * (CHIP_H + 2);
                    const truncName = act.node_label.length > 19 ? act.node_label.slice(0, 17) + '…' : act.node_label;
                    const isSelectedAction = selectedActionNodeId === act.node_id;

                    return (
                      <g
                        key={act.node_id}
                        transform={`translate(${cx - CHIP_W / 2}, ${chipY})`}
                        style={{ cursor: 'pointer' }}
                        onClick={(e) => { e.stopPropagation(); onActionChipClick?.(act.node_id); }}
                        onMouseEnter={(e) => {
                          e.stopPropagation();
                          setChipTooltip({ actionNode: act, chipColor, x: e.clientX, y: e.clientY });
                          setNodeTooltip(null);
                          setBadgeTooltip(null);
                        }}
                        onMouseMove={(e) => {
                          e.stopPropagation();
                          setChipTooltip((prev) => prev ? { ...prev, x: e.clientX, y: e.clientY } : null);
                        }}
                        onMouseLeave={(e) => { e.stopPropagation(); setChipTooltip(null); }}
                      >
                        {/* Chip background */}
                        <rect x={0} y={0} width={CHIP_W} height={CHIP_H}
                          rx={CHIP_H / 2} ry={CHIP_H / 2}
                          fill={isSelectedAction ? `${chipColor}28` : 'rgba(15,15,25,0.88)'}
                          stroke={chipColor}
                          strokeWidth={isSelectedAction ? 1.8 : 0.9}
                          opacity={0.92}
                        />
                        {/* Left color accent dot */}
                        <circle cx={10} cy={CHIP_H / 2} r={3.5} fill={chipColor} />
                        {/* Action name */}
                        <text x={19} y={CHIP_H / 2 + 1} dominantBaseline="middle"
                          fontSize={8} fontWeight={isSelectedAction ? 700 : 500}
                          fill={isSelectedAction ? '#fff' : 'rgba(255,255,255,0.78)'}
                          style={{ userSelect: 'none' }}>
                          {truncName}
                        </text>
                        {/* Step index (small) if there's a number in node_label */}
                        {idx < 9 && (
                          <text x={CHIP_W - 8} y={CHIP_H / 2 + 1} textAnchor="middle" dominantBaseline="middle"
                            fontSize={7} fill={chipColor} opacity={0.55}
                            style={{ userSelect: 'none' }}>
                            {idx + 1}
                          </text>
                        )}
                      </g>
                    );
                  })}

                  {/* "+N more" overflow badge */}
                  {hiddenCount > 0 && (
                    <g transform={`translate(${cx - CHIP_W / 2}, ${CHIPS_START_Y + 12 + visibleActions.length * (CHIP_H + 2)})`}>
                      <rect x={0} y={0} width={CHIP_W} height={CHIP_H - 3}
                        rx={(CHIP_H - 3) / 2} ry={(CHIP_H - 3) / 2}
                        fill="rgba(255,255,255,0.04)" stroke="rgba(255,255,255,0.15)" strokeWidth={0.8}
                      />
                      <text x={CHIP_W / 2} y={(CHIP_H - 3) / 2 + 1} textAnchor="middle" dominantBaseline="middle"
                        fontSize={7.5} fill="rgba(255,255,255,0.4)"
                        style={{ userSelect: 'none' }}>
                        +{hiddenCount} more actions
                      </text>
                    </g>
                  )}
                </g>
              )}
            </g>
          );
        })}
      </svg>

      {/* ── Legend overlay ── */}
      {paths.length > 0 && (
        <div style={{
          position: 'absolute', top: 12, left: 12,
          backgroundColor: 'rgba(15,15,25,0.92)',
          border: '1px solid rgba(255,255,255,0.15)',
          borderRadius: 8, padding: '8px 0 4px',
          minWidth: 200, maxWidth: 260, zIndex: 10, pointerEvents: 'auto',
        }}>
          <div style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', letterSpacing: 1, padding: '0 10px 6px', textTransform: 'uppercase' }}>
            Attack Paths
          </div>
          {paths.map((path) => {
            const isActive = legendSelectedPathId === path.path_id;
            const isFailed = path.path_outcome === 'failed' || path.path_outcome === 'partial';
            const successColor = getPathOutcomeColor('success', outcomeIndexMap.get(path.path_id) ?? 0);
            const failedSegColor = path.path_outcome === 'partial' ? STATUS_COLORS.detected.fill : STATUS_COLORS.prevented.fill;
            const borderColor = isFailed ? failedSegColor : successColor;
            return (
              <div key={path.path_id}
                onClick={(e) => { e.stopPropagation(); handleLegendClick(path.path_id); }}
                style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 10px', cursor: 'pointer', backgroundColor: isActive ? `${borderColor}22` : 'transparent', borderLeft: isActive ? `3px solid ${borderColor}` : '3px solid transparent' }}
                onMouseEnter={(e) => { if (!isActive) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.05)'; }}
                onMouseLeave={(e) => { if (!isActive) e.currentTarget.style.backgroundColor = 'transparent'; }}
              >
                <svg width={28} height={12} style={{ flexShrink: 0 }}>
                  {isFailed ? (
                    <>
                      <line x1={2} y1={6} x2={16} y2={6} stroke={successColor} strokeWidth={isActive ? 3 : 2} strokeLinecap="round" />
                      <line x1={16} y1={6} x2={26} y2={6} stroke={failedSegColor} strokeWidth={isActive ? 2.5 : 1.8} strokeLinecap="round" strokeDasharray="4 3" />
                    </>
                  ) : (
                    <line x1={2} y1={6} x2={26} y2={6} stroke={successColor} strokeWidth={isActive ? 3 : 2} strokeLinecap="round" />
                  )}
                </svg>
                <span style={{ fontSize: 10, fontWeight: isActive ? 700 : 400, color: isActive ? '#fff' : 'rgba(255,255,255,0.75)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {isFailed && <span style={{ color: failedSegColor, marginRight: 3 }}>{path.path_outcome === 'partial' ? '!' : '✓'}</span>}
                  {path.path_name}
                </span>
                {isActive && <span style={{ fontSize: 9, opacity: 0.5, flexShrink: 0 }}>✕</span>}
              </div>
            );
          })}
        </div>
      )}

      {/* ── Node tooltip ── */}
      {nodeTooltip && (
        <div style={{ position: 'fixed', left: nodeTooltip.x + 15, top: nodeTooltip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(15,15,25,0.97)', border: '1px solid rgba(255,255,255,0.15)', borderRadius: 6, padding: '8px 12px', minWidth: 200, maxWidth: 280, boxShadow: '0 4px 20px rgba(0,0,0,0.6)' }}>
          <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#fff' }}>{nodeTooltip.node.node_hostname ?? nodeTooltip.node.node_label}</div>
          {nodeTooltip.node.node_ip && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>IP</span>
              <span style={{ fontSize: 10, fontFamily: 'monospace', color: '#64b5f6' }}>{nodeTooltip.node.node_ip}</span>
            </div>
          )}
          {nodeTooltip.node.node_platform && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Platform</span>
              <span style={{ fontSize: 10, opacity: 0.8 }}>{nodeTooltip.node.node_platform}</span>
            </div>
          )}
          {nodeTooltip.node.node_user_privileges && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>User</span>
              <span style={{ fontSize: 10, color: '#ff9800' }}>{nodeTooltip.node.node_user_privileges}</span>
            </div>
          )}
          {nodeTooltip.node.node_zone && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Zone</span>
              <span style={{ fontSize: 10, opacity: 0.75 }}>{nodeTooltip.node.node_zone}</span>
            </div>
          )}
          {nodeTooltip.node.node_subnet && (
            <div style={{ display: 'flex', gap: 6 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Subnet</span>
              <span style={{ fontSize: 10, fontFamily: 'monospace', opacity: 0.6 }}>{nodeTooltip.node.node_subnet}</span>
            </div>
          )}
          {nodeTooltip.node.node_is_entry_point && <div style={{ marginTop: 6, fontSize: 10, color: '#ffd54f' }}>★ Entry point</div>}
          {nodeTooltip.node.node_is_pivot && <div style={{ marginTop: 2, fontSize: 10, color: '#ff9800' }}>↔ Pivot node</div>}
          {nodeTooltip.node.node_untouched && <div style={{ marginTop: 2, fontSize: 10, color: '#9e9e9e', fontStyle: 'italic' }}>⬡ Not attacked</div>}
        </div>
      )}

      {/* ── Edge badge tooltip ── */}
      {badgeTooltip && (
        <div style={{ position: 'fixed', left: badgeTooltip.x + 15, top: badgeTooltip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(10,12,22,0.98)', border: `1px solid ${badgeTooltip.segColor}55`, borderRadius: 8, padding: '10px 14px', minWidth: 240, maxWidth: 320, boxShadow: '0 6px 28px rgba(0,0,0,0.7)' }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: badgeTooltip.segColor, marginBottom: 6, letterSpacing: 0.3 }}>
            {badgeTooltip.label}
          </div>
          {badgeTooltip.detail ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <div style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.08)', marginBottom: 2 }} />
              {badgeTooltip.detail.trigger_event && (
                <div style={{ display: 'flex', gap: 8 }}>
                  <span style={{ fontSize: 9, fontWeight: 700, color: '#ff9800', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>Event</span>
                  <span style={{ fontSize: 10, color: 'rgba(255,255,255,0.85)' }}>{badgeTooltip.detail.trigger_event}</span>
                </div>
              )}
              {badgeTooltip.detail.condition && (
                <div style={{ display: 'flex', gap: 8 }}>
                  <span style={{ fontSize: 9, fontWeight: 700, color: '#64b5f6', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>Condition</span>
                  <span style={{ fontSize: 10, color: 'rgba(255,255,255,0.75)', lineHeight: 1.4 }}>{badgeTooltip.detail.condition}</span>
                </div>
              )}
              {badgeTooltip.detail.technique && (
                <div style={{ display: 'flex', gap: 8, marginTop: 2, paddingTop: 4, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                  <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>ATT&CK</span>
                  <span style={{ fontSize: 9, fontFamily: 'monospace', color: '#80cbc4' }}>{badgeTooltip.detail.technique}</span>
                </div>
              )}
            </div>
          ) : (
            <div style={{ fontSize: 10, color: 'rgba(255,255,255,0.35)', fontStyle: 'italic' }}>
              No event detail available for this segment.
            </div>
          )}
        </div>
      )}

      {/* ★ Action chip tooltip (V2 addition) */}
      {chipTooltip && (
        <div style={{ position: 'fixed', left: chipTooltip.x + 15, top: chipTooltip.y + 10, pointerEvents: 'none', zIndex: 9999, backgroundColor: 'rgba(10,12,22,0.98)', border: `1px solid ${chipTooltip.chipColor}55`, borderRadius: 8, padding: '10px 14px', minWidth: 240, maxWidth: 320, boxShadow: '0 6px 28px rgba(0,0,0,0.7)' }}>
          {/* Action name header */}
          <div style={{ fontSize: 12, fontWeight: 700, color: chipTooltip.chipColor, marginBottom: 6, letterSpacing: 0.3 }}>
            {chipTooltip.actionNode.node_label}
          </div>
          <div style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.08)', marginBottom: 6 }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {chipTooltip.actionNode.node_status && (
              <div style={{ display: 'flex', gap: 8 }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>Status</span>
                <span style={{ fontSize: 10, color: chipTooltip.chipColor, fontWeight: 600 }}>{chipTooltip.actionNode.node_status}</span>
              </div>
            )}
            {chipTooltip.actionNode.node_payload_name && (
              <div style={{ display: 'flex', gap: 8 }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>Payload</span>
                <span style={{ fontSize: 10, color: 'rgba(255,255,255,0.75)' }}>{chipTooltip.actionNode.node_payload_name}</span>
              </div>
            )}
            {chipTooltip.actionNode.node_executed_at && (
              <div style={{ display: 'flex', gap: 8, marginTop: 2, paddingTop: 4, borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                <span style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', minWidth: 68, textTransform: 'uppercase', letterSpacing: 0.5, paddingTop: 1 }}>Executed</span>
                <span style={{ fontSize: 9, fontFamily: 'monospace', color: 'rgba(255,255,255,0.55)' }}>{chipTooltip.actionNode.node_executed_at}</span>
              </div>
            )}
          </div>
          <div style={{ marginTop: 8, fontSize: 9, color: 'rgba(255,255,255,0.3)', fontStyle: 'italic' }}>
            Click to focus in execution feed →
          </div>
        </div>
      )}

      {/* ── Zoom controls ── */}
      <div style={{ position: 'absolute', bottom: 16, right: 16, display: 'flex', flexDirection: 'column', gap: 4, backgroundColor: 'rgba(15,15,25,0.85)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 8, padding: 4 }}>
        <IconButton size="small" onClick={() => zoomFn(0.8)} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}>
          <ZoomInIcon sx={{ fontSize: 14 }} />
        </IconButton>
        <IconButton size="small" onClick={() => setViewBox({ x: 0, y: 0, w: svgWidth, h: svgHeight })} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}>
          <FitIcon sx={{ fontSize: 14 }} />
        </IconButton>
        <IconButton size="small" onClick={() => zoomFn(1.25)} sx={{ color: 'rgba(255,255,255,0.7)', width: 28, height: 28, '&:hover': { color: '#fff', bgcolor: 'rgba(255,255,255,0.08)' } }}>
          <ZoomOutIcon sx={{ fontSize: 14 }} />
        </IconButton>
      </div>
    </div>
  );
};

export default AttackPathGraphV2;
