import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useCallback, useMemo, useRef, useState } from 'react';
import {
  type AttackPathNode,
  type AttackPathEdge,
  type AttackPathDefinition,
  getNodeStatus,
  getPathOutcomeColor,
  STATUS_COLORS,
} from './attackPathUtils';

// ── Props ────────────────────────────────────────────────────────────────────

interface Props {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  paths: AttackPathDefinition[];
  selectedActionNodeId: string | null; // action node expanded in feed
  onNodeClick?: (assetId: string) => void;
  /** Called when user selects/deselects a path from the legend widget */
  onLegendPathSelect?: (path: AttackPathDefinition | null) => void;
  height?: string;
}

// ── Deterministic jitter (no Math.random) ────────────────────────────────────

function seededFloat(seed: string): number {
  let h = 5381;
  for (let i = 0; i < seed.length; i++) {
    h = ((h << 5) + h) ^ seed.charCodeAt(i);
  }
  return (h >>> 0) / 0xffffffff;
}

// ── Layout constants ─────────────────────────────────────────────────────────

const ZONE_V_GAP = 80;
const ZONE_PAD_X = 40;
const ZONE_PAD_TOP = 54;
const ZONE_PAD_BOTTOM = 36;
const NODE_SPACING = 130;
const NODE_R = 36;

// Two-column x positions (staggered)
const COL_X = [80, 480];

interface NodeCoord {
  nodeId: string;
  cx: number;
  cy: number;
}

interface ZoneInfo {
  zoneName: string;
  zoneX: number;
  zoneY: number;
  rx: number;
  ry: number;
  colorIdx: number;
}

const ZONE_FILL_COLORS = [
  'rgba(100,181,246,0.07)',
  'rgba(129,199,132,0.07)',
  'rgba(186,104,200,0.07)',
  'rgba(255,183,77,0.07)',
  'rgba(77,208,225,0.07)',
  'rgba(255,138,101,0.07)',
  'rgba(240,98,146,0.07)',
  'rgba(174,213,129,0.07)',
  'rgba(159,168,218,0.07)',
  'rgba(128,203,196,0.07)',
];
const ZONE_STROKE_COLORS = [
  'rgba(100,181,246,0.4)',
  'rgba(129,199,132,0.4)',
  'rgba(186,104,200,0.4)',
  'rgba(255,183,77,0.4)',
  'rgba(77,208,225,0.4)',
  'rgba(255,138,101,0.4)',
  'rgba(240,98,146,0.4)',
  'rgba(174,213,129,0.4)',
  'rgba(159,168,218,0.4)',
  'rgba(128,203,196,0.4)',
];

// ── Main component ────────────────────────────────────────────────────────────

const AttackPathGraphV3: FunctionComponent<Props> = ({
  nodes,
  edges,
  paths,
  selectedActionNodeId,
  onNodeClick,
  onLegendPathSelect,
  height = '100%',
}) => {
  const [tooltip, setTooltip] = useState<{ node: AttackPathNode; x: number; y: number } | null>(null);

  // ── Zoom / Pan state ──────────────────────────────────────────────────────
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 900, h: 700 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  const zoomFn = useCallback((factor: number) => {
    setViewBox((vb) => {
      const cx = vb.x + vb.w / 2;
      const cy = vb.y + vb.h / 2;
      const nw = Math.min(Math.max(vb.w * factor, 300), 4000);
      const nh = Math.min(Math.max(vb.h * factor, 200), 4000);
      return { x: cx - nw / 2, y: cy - nh / 2, w: nw, h: nh };
    });
  }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    zoomFn(e.deltaY > 0 ? 1.1 : 0.9);
  }, [zoomFn]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    const tag = (e.target as Element).tagName;
    if (tag === 'svg' || tag === 'ellipse' || tag === 'rect') {
      setIsPanning(true);
      setPanStart({ x: e.clientX, y: e.clientY });
    }
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!isPanning) return;
    const dx = (e.clientX - panStart.x) * (viewBox.w / (svgRef.current?.clientWidth ?? 1));
    const dy = (e.clientY - panStart.y) * (viewBox.h / (svgRef.current?.clientHeight ?? 1));
    setViewBox((vb) => ({ ...vb, x: vb.x - dx, y: vb.y - dy }));
    setPanStart({ x: e.clientX, y: e.clientY });
  }, [isPanning, panStart, viewBox]);

  const handleMouseUp = useCallback(() => setIsPanning(false), []);

  // Only ASSET nodes participate in the layout
  const assetNodes = useMemo(
    () => nodes.filter((n) => n.node_type === 'ASSET'),
    [nodes],
  );

  // Group asset nodes by zone
  const zoneMap = useMemo(() => {
    const map = new Map<string, AttackPathNode[]>();
    for (const node of assetNodes) {
      const zone = node.node_zone ?? 'Unknown Zone';
      const arr = map.get(zone) ?? [];
      arr.push(node);
      map.set(zone, arr);
    }
    return map;
  }, [assetNodes]);

  // Ordered unique zones
  const zoneNames = useMemo(() => {
    const names: string[] = [];
    for (const node of assetNodes) {
      const zone = node.node_zone ?? 'Unknown Zone';
      if (!names.includes(zone)) names.push(zone);
    }
    return names;
  }, [assetNodes]);

  // Compute zone layouts and node coordinates
  const { zoneInfos, nodeCoords, svgWidth, svgHeight } = useMemo(() => {
    const infos: ZoneInfo[] = [];
    const coords: NodeCoord[] = [];

    // Track y-cursor per column
    const colCurrentY = [60, 60];

    zoneNames.forEach((zoneName, zoneIdx) => {
      const col = zoneIdx % 2;
      const otherCol = 1 - col;
      const baseX = COL_X[col];

      const zoneNodes = zoneMap.get(zoneName) ?? [];
      const nodeCount = zoneNodes.length;

      // Zone ellipse dimensions
      const rx = Math.max(160, nodeCount * 72);
      const ry = 80;

      // Stagger: odd zones get a small y-offset relative to even zones
      const staggerY = col === 1 ? 60 : 0;

      const zoneCX = baseX + rx; // center x of zone
      const zoneCY = colCurrentY[col] + staggerY + ry + ZONE_PAD_TOP;

      infos.push({
        zoneName,
        zoneX: zoneCX,
        zoneY: zoneCY,
        rx,
        ry,
        colorIdx: zoneIdx,
      });

      // Position nodes within zone: centered row with jitter
      const totalWidth = (nodeCount - 1) * NODE_SPACING;
      const startX = zoneCX - totalWidth / 2;

      zoneNodes.forEach((node, nodeIdx) => {
        const baseNodeX = startX + nodeIdx * NODE_SPACING;
        const jx = (seededFloat(node.node_id + 'x') - 0.5) * 40;
        const jy = (seededFloat(node.node_id + 'y') - 0.5) * 30;
        coords.push({
          nodeId: node.node_id,
          cx: baseNodeX + jx,
          cy: zoneCY + jy,
        });
      });

      // Advance y cursor for this column
      const zoneHeight = ry * 2 + ZONE_PAD_TOP + ZONE_PAD_BOTTOM;
      colCurrentY[col] = zoneCY + ry + ZONE_PAD_BOTTOM + ZONE_V_GAP;

      // Keep columns roughly in sync by bumping the other column too if it lags far behind
      if (colCurrentY[otherCol] < colCurrentY[col] - ry * 2) {
        colCurrentY[otherCol] = colCurrentY[col] - ry * 2;
      }
    });

    const maxY = Math.max(...colCurrentY) + 60;
    const maxX = Math.max(...infos.map((z) => z.zoneX + z.rx)) + ZONE_PAD_X + 60;

    return { zoneInfos: infos, nodeCoords: coords, svgWidth: Math.max(maxX, 800), svgHeight: Math.max(maxY, 400) };
  }, [zoneNames, zoneMap]);

  // Build a nodeId → coordinate lookup
  const coordMap = useMemo(() => {
    const map = new Map<string, NodeCoord>();
    for (const c of nodeCoords) map.set(c.nodeId, c);
    return map;
  }, [nodeCoords]);

  // Per-outcome index for each path (to vary shade within same outcome family)
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

  // Build set of all node IDs that belong to at least one path
  const nodesInAnyPath = useMemo(() => {
    const set = new Set<string>();
    for (const path of paths) {
      for (const id of path.node_ids) set.add(id);
    }
    return set;
  }, [paths]);

  // ── Legend-selected path (user clicks a path name in the legend) ──────────
  const [legendSelectedPathId, setLegendSelectedPathId] = useState<string | null>(null);

  const handleLegendClick = useCallback((pathId: string) => {
    setLegendSelectedPathId((prev) => {
      const next = prev === pathId ? null : pathId;
      const pathDef = next ? paths.find((p) => p.path_id === next) ?? null : null;
      onLegendPathSelect?.(pathDef);
      return next;
    });
  }, [paths, onLegendPathSelect]);

  // Determine which ASSET nodes are the PRIMARY targets of the selected action
  const targetAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    const targets = new Set<string>();
    for (const edge of edges) {
      if (edge.edge_type === 'asset_link' && edge.edge_source === selectedActionNodeId) {
        targets.add(edge.edge_target);
      }
    }
    return targets;
  }, [selectedActionNodeId, edges]);

  // Determine the "source" machine — the asset linked to the PREDECESSOR action in the chain.
  // This lets us show "from machine → to machine" for lateral movement actions.
  const sourceAssetIds = useMemo(() => {
    if (!selectedActionNodeId) return new Set<string>();
    // Find the action that has a chain_flow edge INTO the selected action
    const predecessorActionId = edges.find(
      (e) => e.edge_type === 'chain_flow' && e.edge_target === selectedActionNodeId,
    )?.edge_source;
    if (!predecessorActionId) return new Set<string>();
    const sources = new Set<string>();
    for (const edge of edges) {
      if (edge.edge_type === 'asset_link' && edge.edge_source === predecessorActionId) {
        // Only add as source if it's not already a primary target
        if (!targetAssetIds.has(edge.edge_target)) sources.add(edge.edge_target);
      }
    }
    return sources;
  }, [selectedActionNodeId, edges, targetAssetIds]);

  // Paths that contain any of the targeted assets (from feed selection)
  const feedActivePaths = useMemo(() => {
    if (targetAssetIds.size === 0) return new Set<string>();
    const active = new Set<string>();
    for (const path of paths) {
      if (path.node_ids.some((id) => targetAssetIds.has(id) || sourceAssetIds.has(id))) {
        active.add(path.path_id);
      }
    }
    return active;
  }, [targetAssetIds, sourceAssetIds, paths]);

  // Combined: feed selection OR legend selection drives active paths
  const activePaths = useMemo(() => {
    if (legendSelectedPathId) return new Set([legendSelectedPathId]);
    return feedActivePaths;
  }, [legendSelectedPathId, feedActivePaths]);

  // Nodes in active paths (for dimming logic)
  const nodesInActivePaths = useMemo(() => {
    const set = new Set<string>();
    for (const path of paths) {
      if (activePaths.has(path.path_id)) {
        for (const id of path.node_ids) set.add(id);
      }
    }
    return set;
  }, [paths, activePaths]);

  const hasSelection = selectedActionNodeId !== null || legendSelectedPathId !== null;

  // Node opacity based on selection state
  // Tier 1 (1.0)  – primary target of this action
  // Tier 2 (0.80) – source machine the action pivoted FROM
  // Tier 3 (0.50) – other nodes in the same attack path
  // Tier 4 (0.15) – unrelated nodes
  function getNodeOpacity(nodeId: string): number {
    if (!hasSelection) return 1.0;
    if (legendSelectedPathId) {
      return nodesInActivePaths.has(nodeId) ? 1.0 : 0.15;
    }
    if (targetAssetIds.has(nodeId)) return 1.0;
    if (sourceAssetIds.has(nodeId)) return 0.80;
    if (nodesInActivePaths.has(nodeId)) return 0.50;
    return 0.15;
  }

  // Scale: primary target = 1.25, source machine = 1.10, others = 1.0
  function getNodeScale(nodeId: string): number {
    if (!hasSelection) return 1.0;
    if (targetAssetIds.has(nodeId)) return 1.25;
    if (sourceAssetIds.has(nodeId)) return 1.10;
    return 1.0;
  }

  // Build a smooth bezier path through a sequence of node coordinates
  function buildPathD(nodeIds: string[]): string {
    const pts = nodeIds.map((id) => coordMap.get(id)).filter(Boolean) as NodeCoord[];
    if (pts.length < 2) return '';

    let d = `M ${pts[0].cx} ${pts[0].cy}`;
    for (let i = 0; i < pts.length - 1; i++) {
      const p0 = pts[i];
      const p1 = pts[i + 1];
      const cp1x = p0.cx + (p1.cx - p0.cx) * 0.5;
      const cp1y = p0.cy - 50;
      const cp2x = p0.cx + (p1.cx - p0.cx) * 0.5;
      const cp2y = p1.cy - 50;
      d += ` C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p1.cx} ${p1.cy}`;
    }
    return d;
  }

  return (
    <div style={{ position: 'relative', width: '100%', height, overflow: 'hidden' }}>
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        style={{ display: 'block', cursor: isPanning ? 'grabbing' : 'grab' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      >
        <defs>
          {/* Glow filters for target nodes */}
          {(['prevented', 'detected', 'undetected', 'pending'] as const).map((status) => (
            <filter key={`glow-${status}`} id={`glow-v3-${status}`} x="-50%" y="-50%" width="200%" height="200%">
              <feDropShadow dx="0" dy="0" stdDeviation="8"
                floodColor={STATUS_COLORS[status].fill}
                floodOpacity="0.85"
              />
            </filter>
          ))}
          <filter id="glow-v3-untouched" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#9e9e9e" floodOpacity="0.6" />
          </filter>
        </defs>

        {/* Zone ellipses */}
        {zoneInfos.map((zone, idx) => (
          <g key={zone.zoneName}>
            <ellipse
              cx={zone.zoneX}
              cy={zone.zoneY}
              rx={zone.rx + ZONE_PAD_X}
              ry={zone.ry + 20}
              fill={ZONE_FILL_COLORS[zone.colorIdx % ZONE_FILL_COLORS.length]}
              stroke={ZONE_STROKE_COLORS[zone.colorIdx % ZONE_STROKE_COLORS.length]}
              strokeWidth={1.5}
            />
            <text
              x={zone.zoneX}
              y={zone.zoneY - zone.ry - 6}
              textAnchor="middle"
              fontSize={11}
              fontWeight={700}
              fontFamily="monospace"
              fill="rgba(255,255,255,0.6)"
              style={{ pointerEvents: 'none' }}
            >
              {zone.zoneName}
            </text>
          </g>
        ))}

        {/* Attack path lines (drawn behind nodes) */}
        {paths.map((path) => {
          const isActive = activePaths.has(path.path_id);
          const isFailed = path.path_outcome === 'failed' || path.path_outcome === 'partial';
          // Successful portion is always red (attacker reached those nodes)
          const successColor = getPathOutcomeColor('success', outcomeIndexMap.get(path.path_id) ?? 0);
          // Failed segment color: orange if only detected, green if prevented
          const failedSegColor = path.path_outcome === 'partial'
            ? STATUS_COLORS.detected.fill  // orange — detected but not fully stopped
            : STATUS_COLORS.prevented.fill; // green — fully prevented

          // Split: successIds = nodes up to and including failure point
          const failFromIdx = isFailed && path.failed_from_node_id
            ? path.node_ids.indexOf(path.failed_from_node_id)
            : (isFailed ? 0 : path.node_ids.length);
          const successIds = failFromIdx > 0 ? path.node_ids.slice(0, failFromIdx + 1) : (isFailed ? [] : path.node_ids);
          const failedIds  = isFailed && failFromIdx >= 0 ? path.node_ids.slice(failFromIdx) : [];

          const successD = successIds.length >= 2 ? buildPathD(successIds) : (isFailed ? '' : buildPathD(path.node_ids) ?? '');
          const failedD  = failedIds.length >= 2  ? buildPathD(failedIds)  : '';
          const fullD    = !isFailed ? (buildPathD(path.node_ids) ?? '') : '';

          let opacity: number;
          let sw: number;
          if (!hasSelection)    { opacity = 0.70; sw = 2.5; }
          else if (isActive)    { opacity = 1.00; sw = 3.5; }
          else                  { opacity = 0.06; sw = 2.0; }

          const lastAssetId = path.node_ids[path.node_ids.length - 1];
          const deadCoord = isFailed ? nodeCoords.find((c) => c.nodeId === lastAssetId) : null;

          return (
            <g key={path.path_id} style={{ pointerEvents: 'none' }}>
              {/* Successful portion — red (attack reached these nodes) */}
              {successD && (
                <path
                  d={successD}
                  fill="none"
                  stroke={successColor}
                  strokeWidth={sw}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  opacity={opacity}
                />
              )}
              {/* Failed portion — green/orange (attack was stopped here) */}
              {failedD && (
                <path
                  d={failedD}
                  fill="none"
                  stroke={failedSegColor}
                  strokeWidth={sw * 0.85}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeDasharray="8 6"
                  opacity={Math.max(opacity * 0.75, 0.10)}
                />
              )}
              {/* Whole-path success — animated particle */}
              {fullD && (isActive || !hasSelection) && (
                <>
                  <path d={fullD} fill="none" stroke={successColor} strokeWidth={sw}
                    strokeLinecap="round" strokeLinejoin="round" opacity={opacity} />
                  <circle r={4} fill={successColor} opacity={0.9}>
                    <animateMotion dur="2.2s" repeatCount="indefinite" path={fullD} />
                  </circle>
                </>
              )}
              {/* Dead-end marker at last node of failed path */}
              {isFailed && deadCoord && (isActive || !hasSelection) && (
                <g transform={`translate(${deadCoord.cx + NODE_R - 6}, ${deadCoord.cy - NODE_R + 6})`}>
                  <circle r={9} fill="rgba(20,20,30,0.92)" stroke={`${failedSegColor}cc`} strokeWidth={1.5} />
                  <text
                    x={0} y={4}
                    textAnchor="middle"
                    fontSize={11}
                    fontWeight={700}
                    fill={failedSegColor}
                    style={{ userSelect: 'none' }}
                  >{path.path_outcome === 'partial' ? '!' : '✓'}</text>
                </g>
              )}
            </g>
          );
        })}

        {/* Node circles */}
        {nodeCoords.map(({ nodeId, cx, cy }) => {
          const node = nodes.find((n) => n.node_id === nodeId);
          if (!node) return null;

          const isUntouched = node.node_untouched === true || !nodesInAnyPath.has(nodeId);
          const status = isUntouched ? 'pending' : getNodeStatus(node);
          const colors = STATUS_COLORS[status];
          const opacity = isUntouched && !targetAssetIds.has(nodeId) && !sourceAssetIds.has(nodeId)
            ? Math.min(getNodeOpacity(nodeId), 0.30)
            : getNodeOpacity(nodeId);
          const scale = getNodeScale(nodeId);
          const isTarget = targetAssetIds.has(nodeId);
          const isSource = sourceAssetIds.has(nodeId);
          const filterId = (isTarget || isSource)
            ? (isUntouched ? 'glow-v3-untouched' : `glow-v3-${status}`)
            : undefined;

          const label = node.node_label.length > 14
            ? `${node.node_label.slice(0, 12)}…`
            : node.node_label;

          return (
            <g
              key={nodeId}
              opacity={opacity}
              transform={scale !== 1 ? `translate(${cx},${cy}) scale(${scale}) translate(${-cx},${-cy})` : undefined}
              style={{ cursor: 'pointer', transition: 'opacity 0.25s' }}
              onClick={(e) => { e.stopPropagation(); onNodeClick?.(nodeId); }}
              onMouseEnter={(e) => { e.stopPropagation(); setTooltip({ node, x: e.clientX, y: e.clientY }); }}
              onMouseMove={(e) => { e.stopPropagation(); setTooltip({ node, x: e.clientX, y: e.clientY }); }}
              onMouseLeave={(e) => { e.stopPropagation(); setTooltip(null); }}
            >
              {/* Glow for target/source nodes */}
              {filterId && (
                <circle cx={cx} cy={cy} r={NODE_R + (isTarget ? 12 : 8)}
                  fill={isUntouched ? '#9e9e9e' : colors.fill}
                  opacity={isTarget ? 0.18 : 0.10}
                  filter={`url(#${filterId})`} />
              )}

              {/* Entry point marker */}
              {node.node_is_entry_point && (
                <circle cx={cx} cy={cy} r={NODE_R + 8} fill="none"
                  stroke="#fff" strokeWidth={1.5} opacity={0.6} strokeDasharray="4 3" />
              )}

              {/* Pivot dashed ring */}
              {node.node_is_pivot && (
                <circle cx={cx} cy={cy} r={NODE_R + 5} fill="none"
                  stroke={colors.fill} strokeWidth={1.2} opacity={0.45} strokeDasharray="3 3" />
              )}

              {/* "From" arrow indicator for source machine */}
              {isSource && !isTarget && (
                <text x={cx} y={cy - NODE_R - 6} textAnchor="middle" fontSize={11}
                  fill="#ff9800" style={{ pointerEvents: 'none' }}>↑</text>
              )}

              {/* Main circle */}
              <circle
                cx={cx}
                cy={cy}
                r={NODE_R}
                fill={isUntouched ? 'rgba(30,30,46,0.7)' : 'rgba(20,20,36,0.95)'}
                stroke={isUntouched ? '#616161' : colors.fill}
                strokeWidth={isTarget ? 3.5 : isSource ? 2.5 : 2}
              />

              {/* Inner color fill ring */}
              <circle
                cx={cx}
                cy={cy}
                r={NODE_R - 7}
                fill={isUntouched ? 'rgba(97,97,97,0.12)' : `${colors.fill}${isTarget ? '28' : '18'}`}
              />

              {/* Status dot */}
              <circle
                cx={cx + NODE_R - 9}
                cy={cy - NODE_R + 9}
                r={6}
                fill={isUntouched ? '#616161' : colors.fill}
                stroke="rgba(20,20,36,1)"
                strokeWidth={1.5}
              />

              {/* Label inside */}
              <text x={cx} y={cy + 4} textAnchor="middle" fontSize={9} fontWeight={700}
                fill={isUntouched ? 'rgba(255,255,255,0.45)' : '#fff'}
                style={{ pointerEvents: 'none' }}>
                {label}
              </text>

              {/* IP below label */}
              {node.node_ip && (
                <text x={cx} y={cy + 15} textAnchor="middle" fontSize={8}
                  fill="rgba(255,255,255,0.45)" style={{ pointerEvents: 'none' }}>
                  {node.node_ip}
                </text>
              )}

              {/* Node label below circle */}
              <text x={cx} y={cy + NODE_R + 15} textAnchor="middle" fontSize={10} fontWeight={600}
                fill={isUntouched ? 'rgba(255,255,255,0.35)' : 'rgba(255,255,255,0.85)'}
                style={{ pointerEvents: 'none' }}>
                {node.node_label.length > 18 ? `${node.node_label.slice(0, 16)}…` : node.node_label}
              </text>
            </g>
          );
        })}

        {/* Path legend — moved to HTML overlay */}
      </svg>

      {/* Legend overlay — outside SVG so zoom/pan doesn't affect its size */}
      {paths.length > 0 && (
        <div style={{
          position: 'absolute',
          top: 12,
          left: 12,
          backgroundColor: 'rgba(15,15,25,0.92)',
          border: '1px solid rgba(255,255,255,0.15)',
          borderRadius: 8,
          padding: '8px 0 4px',
          minWidth: 200,
          maxWidth: 240,
          zIndex: 10,
          pointerEvents: 'auto',
        }}>
          <div style={{ fontSize: 9, fontWeight: 700, color: 'rgba(255,255,255,0.35)', letterSpacing: 1, padding: '0 10px 6px', textTransform: 'uppercase' }}>
            Attack Paths
          </div>
          {paths.map((path) => {
            const isActive = legendSelectedPathId === path.path_id;
            const isFailed = path.path_outcome === 'failed' || path.path_outcome === 'partial';
            const successColor = getPathOutcomeColor('success', outcomeIndexMap.get(path.path_id) ?? 0);
            const failedSegColor = path.path_outcome === 'partial'
              ? STATUS_COLORS.detected.fill
              : STATUS_COLORS.prevented.fill;
            const borderColor = isFailed ? failedSegColor : successColor;
            return (
              <div
                key={path.path_id}
                onClick={(e) => { e.stopPropagation(); handleLegendClick(path.path_id); }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '4px 10px',
                  cursor: 'pointer',
                  backgroundColor: isActive ? `${borderColor}22` : 'transparent',
                  borderLeft: isActive ? `3px solid ${borderColor}` : '3px solid transparent',
                  transition: 'background-color 0.15s',
                }}
                onMouseEnter={(e) => { if (!isActive) e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.05)'; }}
                onMouseLeave={(e) => { if (!isActive) e.currentTarget.style.backgroundColor = 'transparent'; }}
              >
                {/* Split swatch: red portion + dashed green/orange portion */}
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
                <span style={{
                  fontSize: 10,
                  fontWeight: isActive ? 700 : 400,
                  color: isActive ? '#fff' : 'rgba(255,255,255,0.75)',
                  flex: 1,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}>
                  {isFailed && <span style={{ color: failedSegColor, marginRight: 3 }}>{path.path_outcome === 'partial' ? '!' : '✓'}</span>}
                  {path.path_name}
                </span>
                {isActive && <span style={{ fontSize: 9, opacity: 0.5, flexShrink: 0 }}>✕</span>}
              </div>
            );
          })}
        </div>
      )}

      {/* Tooltip */}
      {tooltip && (
        <div style={{
          position: 'fixed',
          left: tooltip.x + 15,
          top: tooltip.y + 10,
          pointerEvents: 'none',
          zIndex: 9999,
          backgroundColor: 'rgba(15,15,25,0.97)',
          border: '1px solid rgba(255,255,255,0.15)',
          borderRadius: 6,
          padding: '8px 12px',
          minWidth: 200,
          maxWidth: 280,
          boxShadow: '0 4px 20px rgba(0,0,0,0.6)',
        }}>
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
          {tooltip.node.node_user_privileges && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>User</span>
              <span style={{ fontSize: 10, color: '#ff9800' }}>{tooltip.node.node_user_privileges}</span>
            </div>
          )}
          {tooltip.node.node_zone && (
            <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Zone</span>
              <span style={{ fontSize: 10, opacity: 0.75 }}>{tooltip.node.node_zone}</span>
            </div>
          )}
          {tooltip.node.node_subnet && (
            <div style={{ display: 'flex', gap: 6 }}>
              <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Subnet</span>
              <span style={{ fontSize: 10, fontFamily: 'monospace', opacity: 0.6 }}>{tooltip.node.node_subnet}</span>
            </div>
          )}
          {tooltip.node.node_is_entry_point && (
            <div style={{ marginTop: 6, fontSize: 10, color: '#ffd54f' }}>★ Entry point</div>
          )}
          {tooltip.node.node_is_pivot && (
            <div style={{ marginTop: 2, fontSize: 10, color: '#ff9800' }}>↔ Pivot node</div>
          )}
          {tooltip.node.node_untouched && (
            <div style={{ marginTop: 2, fontSize: 10, color: '#9e9e9e', fontStyle: 'italic' }}>⬡ Not attacked</div>
          )}
        </div>
      )}

      {/* Zoom controls */}
      <div style={{
        position: 'absolute',
        bottom: 16,
        right: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        backgroundColor: 'rgba(15,15,25,0.85)',
        border: '1px solid rgba(255,255,255,0.12)',
        borderRadius: 8,
        padding: 4,
      }}>
        <IconButton size="small" onClick={() => zoomFn(0.8)} sx={{ color: '#fff' }}>
          <ZoomInIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => setViewBox({ x: 0, y: 0, w: svgWidth, h: svgHeight })} sx={{ color: '#fff' }}>
          <FitIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => zoomFn(1.25)} sx={{ color: '#fff' }}>
          <ZoomOutIcon fontSize="small" />
        </IconButton>
      </div>
    </div>
  );
};

export default AttackPathGraphV3;
