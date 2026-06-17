import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useCallback, useMemo, useRef, useState } from 'react';
import {
  type AttackPathNode,
  type AttackPathEdge,
  type AttackPathDefinition,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

// ── Props ────────────────────────────────────────────────────────────────────

interface Props {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  paths: AttackPathDefinition[];
  selectedActionNodeId: string | null;
  onNodeClick?: (assetId: string) => void;
  height?: string;
}

// ── Kill chain phases ─────────────────────────────────────────────────────────

const PHASES = [
  'Reconnaissance',
  'Initial Access',
  'Execution',
  'Privilege Escalation',
  'Lateral Movement',
  'Credential Access',
  'Discovery',
  'Exfiltration',
] as const;

type Phase = (typeof PHASES)[number];

const PHASE_COLORS: Record<Phase, string> = {
  'Reconnaissance': '#9c27b0',
  'Initial Access': '#f44336',
  'Execution': '#ff9800',
  'Privilege Escalation': '#ff5722',
  'Lateral Movement': '#2196f3',
  'Credential Access': '#e91e63',
  'Discovery': '#00bcd4',
  'Exfiltration': '#4caf50',
};

function derivePhase(label: string, payload?: string): Phase {
  const l = (label + ' ' + (payload ?? '')).toLowerCase();
  if (l.includes('scan') || l.includes('nmap') || l.includes('recon') || l.includes('enum')) return 'Reconnaissance';
  if (l.includes('spray') || l.includes('brute') || l.includes('phish') || l.includes('exploit') || l.includes('rce') || l.includes('webshell') || l.includes('initial')) return 'Initial Access';
  if (l.includes('exec') || l.includes('powershell') || l.includes('cmd') || l.includes('wmi') || l.includes('psexec') || l.includes('payload')) return 'Execution';
  if (l.includes('privesc') || l.includes('escalat') || l.includes('uac') || l.includes('sudo') || l.includes('admin') || l.includes('token') || l.includes('printnight') || l.includes('system')) return 'Privilege Escalation';
  if (l.includes('pass-the-hash') || l.includes('pth') || l.includes('lateral') || l.includes('pivot') || l.includes('smb') || l.includes('rdp') || l.includes('ssh')) return 'Lateral Movement';
  if (l.includes('mimikatz') || l.includes('lsass') || l.includes('kerberoast') || l.includes('credential') || l.includes('hash') || l.includes('dcsync') || l.includes('ntlm') || l.includes('password')) return 'Credential Access';
  if (l.includes('discover') || l.includes('bloodhound') || l.includes('ldap') || l.includes('domain') || l.includes('map') || l.includes('spider') || l.includes('whoami')) return 'Discovery';
  if (l.includes('exfil') || l.includes('copy') || l.includes('upload') || l.includes('download') || l.includes('transfer') || l.includes('zip')) return 'Exfiltration';
  return 'Execution';
}

// ── Layout constants ──────────────────────────────────────────────────────────

const MATRIX_LEFT = 210;   // width of row-label panel
const HEADER_H = 56;       // height of phase column header row
const PHASE_W = 210;       // column width per phase
const ROW_H = 90;          // height per asset row
const CELL_W = 180;        // action cell width
const CELL_H = 52;         // action cell height
const CELL_V_GAP = 8;      // gap between stacked cells in same (phase, asset)
const CELL_PAD_X = 15;     // horizontal padding within phase column
const STATUS_BAR_W = 4;    // left-side status color bar

// ── Zone color palette for row labels ────────────────────────────────────────

const ZONE_CHIP_COLORS = [
  '#1565c0', '#2e7d32', '#6a1b9a', '#e65100',
  '#00695c', '#c62828', '#0277bd', '#4e342e',
];

function zoneColor(zone: string | undefined): string {
  if (!zone) return '#37474f';
  let h = 5381;
  for (let i = 0; i < zone.length; i++) {
    h = ((h << 5) + h) ^ zone.charCodeAt(i);
  }
  return ZONE_CHIP_COLORS[(h >>> 0) % ZONE_CHIP_COLORS.length];
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function truncate(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

function formatTime(iso?: string): string {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

// ── Placed action (layout result) ─────────────────────────────────────────────

interface PlacedAction {
  node: AttackPathNode;
  phase: Phase;
  assetId: string;
  /** Top-left corner of the cell in SVG coordinates */
  cx: number;
  cy: number;
}

// ── Component ─────────────────────────────────────────────────────────────────

const AttackPathGraphV4: FunctionComponent<Props> = ({
  nodes,
  edges,
  selectedActionNodeId,
  onNodeClick,
  height = '100%',
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 1400, h: 700 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });
  const [tooltip, setTooltip] = useState<{ node: AttackPathNode; x: number; y: number } | null>(null);

  // ── Zoom / Pan ──────────────────────────────────────────────────────────────

  const zoomFn = useCallback((factor: number) => {
    setViewBox((vb) => {
      const cx = vb.x + vb.w / 2;
      const cy = vb.y + vb.h / 2;
      const nw = Math.min(Math.max(vb.w * factor, 400), 5000);
      const nh = Math.min(Math.max(vb.h * factor, 300), 5000);
      return { x: cx - nw / 2, y: cy - nh / 2, w: nw, h: nh };
    });
  }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    zoomFn(e.deltaY > 0 ? 1.1 : 0.9);
  }, [zoomFn]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    const tag = (e.target as Element).tagName;
    if (tag === 'svg' || tag === 'rect' || tag === 'line') {
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

  // ── Build action → asset lookup ─────────────────────────────────────────────

  const actionToAsset = useMemo(() => {
    const map = new Map<string, string>();
    for (const edge of edges) {
      if (edge.edge_type === 'asset_link') {
        map.set(edge.edge_source, edge.edge_target);
      }
    }
    return map;
  }, [edges]);

  // ── Sort asset rows by zone then label ──────────────────────────────────────

  const assetRows = useMemo(() => {
    return nodes
      .filter((n) => n.node_type === 'ASSET')
      .slice()
      .sort((a, b) => {
        const zoneA = a.node_zone ?? '';
        const zoneB = b.node_zone ?? '';
        if (zoneA !== zoneB) return zoneA.localeCompare(zoneB);
        return a.node_label.localeCompare(b.node_label);
      });
  }, [nodes]);

  const assetRowIndex = useMemo(() => {
    const map = new Map<string, number>();
    assetRows.forEach((a, i) => map.set(a.node_id, i));
    return map;
  }, [assetRows]);

  // ── Assign actions to (phase, asset) cells ──────────────────────────────────

  const actionNodes = useMemo(
    () => nodes.filter((n) => n.node_type === 'ACTION'),
    [nodes],
  );

  // Determine which phases actually have actions (to avoid empty columns)
  const activePhases = useMemo(() => {
    const used = new Set<Phase>();
    for (const node of actionNodes) {
      used.add(derivePhase(node.node_label, node.node_payload_name));
    }
    // Return phases in canonical order, filtered to used ones
    return PHASES.filter((p) => used.has(p));
  }, [actionNodes]);

  const phaseColIndex = useMemo(() => {
    const map = new Map<Phase, number>();
    activePhases.forEach((p, i) => map.set(p, i));
    return map;
  }, [activePhases]);

  // Stack tracker: (phaseIdx, assetRowIdx) → count of actions placed so far
  const placedActions = useMemo((): PlacedAction[] => {
    const stackCount = new Map<string, number>();

    const result: PlacedAction[] = [];
    for (const node of actionNodes) {
      const phase = derivePhase(node.node_label, node.node_payload_name);
      const assetId = actionToAsset.get(node.node_id);
      const rowIdx = assetId !== undefined ? (assetRowIndex.get(assetId) ?? -1) : -1;
      const colIdx = phaseColIndex.get(phase) ?? 0;

      const key = `${colIdx}-${rowIdx}`;
      const stackIdx = stackCount.get(key) ?? 0;
      stackCount.set(key, stackIdx + 1);

      const colX = MATRIX_LEFT + colIdx * PHASE_W + CELL_PAD_X;
      const rowY = HEADER_H + (rowIdx >= 0 ? rowIdx : assetRows.length) * ROW_H;
      const cy = rowY + (ROW_H - CELL_H) / 2 + stackIdx * (CELL_H + CELL_V_GAP);

      result.push({
        node,
        phase,
        assetId: assetId ?? '',
        cx: colX,
        cy,
      });
    }
    return result;
  }, [actionNodes, actionToAsset, assetRowIndex, phaseColIndex, assetRows.length]);

  // Quick lookup: actionId → PlacedAction
  const placedMap = useMemo(() => {
    const map = new Map<string, PlacedAction>();
    for (const p of placedActions) map.set(p.node.node_id, p);
    return map;
  }, [placedActions]);

  // ── Selected node chain highlight ───────────────────────────────────────────

  const highlightedChain = useMemo((): Set<string> | null => {
    if (!selectedActionNodeId) return null;
    const chain = new Set<string>([selectedActionNodeId]);
    // Walk upstream
    const queue = [selectedActionNodeId];
    while (queue.length > 0) {
      const cur = queue.shift()!;
      for (const e of edges) {
        if (e.edge_type === 'chain_flow' && e.edge_target === cur && !chain.has(e.edge_source)) {
          chain.add(e.edge_source);
          queue.push(e.edge_source);
        }
      }
    }
    // Walk downstream
    const queue2 = [selectedActionNodeId];
    while (queue2.length > 0) {
      const cur = queue2.shift()!;
      for (const e of edges) {
        if (e.edge_type === 'chain_flow' && e.edge_source === cur && !chain.has(e.edge_target)) {
          chain.add(e.edge_target);
          queue2.push(e.edge_target);
        }
      }
    }
    return chain;
  }, [selectedActionNodeId, edges]);

  // ── SVG canvas dimensions ───────────────────────────────────────────────────

  const svgWidth = MATRIX_LEFT + activePhases.length * PHASE_W + 20;
  const svgHeight = HEADER_H + assetRows.length * ROW_H + 40;

  // Fit-to-canvas reset
  const handleFit = useCallback(() => {
    setViewBox({ x: 0, y: 0, w: svgWidth, h: svgHeight });
  }, [svgWidth, svgHeight]);

  // ── Chain flow arrows ───────────────────────────────────────────────────────

  const chainEdges = useMemo(
    () => edges.filter((e) => e.edge_type === 'chain_flow'),
    [edges],
  );

  // ── Render ──────────────────────────────────────────────────────────────────

  const totalRows = assetRows.length;

  return (
    <div
      style={{
        position: 'relative',
        width: '100%',
        height,
        background: '#0d1117',
        overflow: 'hidden',
        userSelect: 'none',
      }}
    >
      {/* Legend */}
      <div
        style={{
          position: 'absolute',
          top: 10,
          right: 16,
          display: 'flex',
          gap: 10,
          alignItems: 'center',
          zIndex: 10,
          background: 'rgba(22,27,34,0.85)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 6,
          padding: '5px 10px',
          fontSize: 11,
          color: '#aaa',
        }}
      >
        {(['prevented', 'detected', 'undetected', 'pending'] as const).map((s) => (
          <span key={s} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              borderRadius: 2,
              background: STATUS_COLORS[s].fill,
            }} />
            {s.charAt(0).toUpperCase() + s.slice(1)}
          </span>
        ))}
      </div>

      {/* SVG matrix */}
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        style={{ cursor: isPanning ? 'grabbing' : 'grab', display: 'block' }}
      >
        <defs>
          <marker id="v4-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
            <path d="M0,0 L0,6 L8,3 z" fill="rgba(255,255,255,0.4)" />
          </marker>
          {(['prevented', 'detected', 'undetected', 'pending'] as const).map((s) => (
            <marker
              key={s}
              id={`v4-arrow-${s}`}
              markerWidth="8"
              markerHeight="8"
              refX="6"
              refY="3"
              orient="auto"
            >
              <path d="M0,0 L0,6 L8,3 z" fill={STATUS_COLORS[s].fill} />
            </marker>
          ))}
        </defs>

        {/* ── Background: alternating row stripes ── */}
        {assetRows.map((_, rowIdx) => (
          <rect
            key={`row-bg-${rowIdx}`}
            x={0}
            y={HEADER_H + rowIdx * ROW_H}
            width={svgWidth}
            height={ROW_H}
            fill={rowIdx % 2 === 0 ? 'rgba(255,255,255,0.02)' : 'rgba(255,255,255,0.00)'}
          />
        ))}

        {/* ── Row labels panel background ── */}
        <rect
          x={0}
          y={HEADER_H}
          width={MATRIX_LEFT}
          height={totalRows * ROW_H}
          fill="rgba(13,17,23,0.95)"
        />
        <line
          x1={MATRIX_LEFT}
          y1={HEADER_H}
          x2={MATRIX_LEFT}
          y2={HEADER_H + totalRows * ROW_H}
          stroke="rgba(255,255,255,0.12)"
          strokeWidth={1}
        />

        {/* ── Phase column headers ── */}
        <rect x={0} y={0} width={svgWidth} height={HEADER_H} fill="rgba(13,17,23,0.98)" />
        <line x1={0} y1={HEADER_H} x2={svgWidth} y2={HEADER_H} stroke="rgba(255,255,255,0.12)" strokeWidth={1} />

        {activePhases.map((phase, colIdx) => {
          const x = MATRIX_LEFT + colIdx * PHASE_W;
          const color = PHASE_COLORS[phase];
          return (
            <g key={phase}>
              {/* Vertical divider */}
              {colIdx > 0 && (
                <line
                  x1={x}
                  y1={0}
                  x2={x}
                  y2={HEADER_H + totalRows * ROW_H}
                  stroke="rgba(255,255,255,0.06)"
                  strokeWidth={1}
                  strokeDasharray="4 4"
                />
              )}
              {/* Phase chip */}
              <rect
                x={x + 8}
                y={10}
                width={PHASE_W - 16}
                height={34}
                rx={6}
                fill={`${color}22`}
                stroke={`${color}66`}
                strokeWidth={1}
              />
              <text
                x={x + PHASE_W / 2}
                y={32}
                textAnchor="middle"
                fontSize={11}
                fontWeight={600}
                fill={color}
                fontFamily="monospace"
              >
                {phase}
              </text>
            </g>
          );
        })}

        {/* ── Row labels ── */}
        {assetRows.map((asset, rowIdx) => {
          const y = HEADER_H + rowIdx * ROW_H;
          const isHighlighted = selectedActionNodeId
            ? highlightedChain?.has(asset.node_id)
            : true;
          const opacity = isHighlighted ? 1 : 0.3;
          const chipColor = zoneColor(asset.node_zone);

          return (
            <g
              key={`row-label-${asset.node_id}`}
              opacity={opacity}
              style={{ cursor: 'pointer' }}
              onClick={() => onNodeClick?.(asset.node_id)}
            >
              {/* Hostname */}
              <text
                x={12}
                y={y + ROW_H / 2 - 10}
                fontSize={12}
                fontWeight={600}
                fill="#e0e0e0"
                fontFamily="monospace"
              >
                {truncate(asset.node_hostname ?? asset.node_label, 22)}
              </text>
              {/* IP */}
              {asset.node_ip && (
                <text
                  x={12}
                  y={y + ROW_H / 2 + 5}
                  fontSize={10}
                  fill="#888"
                  fontFamily="monospace"
                >
                  {asset.node_ip}
                </text>
              )}
              {/* Zone chip */}
              {asset.node_zone && (
                <g>
                  <rect
                    x={12}
                    y={y + ROW_H / 2 + 14}
                    width={Math.min(asset.node_zone.length * 6.5 + 10, MATRIX_LEFT - 24)}
                    height={14}
                    rx={4}
                    fill={`${chipColor}33`}
                    stroke={`${chipColor}88`}
                    strokeWidth={1}
                  />
                  <text
                    x={17}
                    y={y + ROW_H / 2 + 24}
                    fontSize={9}
                    fill={chipColor}
                    fontFamily="monospace"
                  >
                    {truncate(asset.node_zone, 26)}
                  </text>
                </g>
              )}
              {/* Row separator */}
              <line
                x1={0}
                y1={y + ROW_H}
                x2={svgWidth}
                y2={y + ROW_H}
                stroke="rgba(255,255,255,0.05)"
                strokeWidth={1}
              />
            </g>
          );
        })}

        {/* ── Chain flow arrows ── */}
        {chainEdges.map((edge) => {
          const src = placedMap.get(edge.edge_source);
          const tgt = placedMap.get(edge.edge_target);
          if (!src || !tgt) return null;

          const isHighlighted = highlightedChain
            ? highlightedChain.has(edge.edge_source) && highlightedChain.has(edge.edge_target)
            : true;
          const opacity = isHighlighted ? 0.75 : 0.12;

          const srcStatus = getNodeStatus(src.node);
          const color = STATUS_COLORS[srcStatus].fill;
          const markerId = `v4-arrow-${srcStatus}`;

          // Source: right-center of source cell; Target: left-center of target cell
          const x1 = src.cx + CELL_W;
          const y1 = src.cy + CELL_H / 2;
          const x2 = tgt.cx;
          const y2 = tgt.cy + CELL_H / 2;

          const midX = (x1 + x2) / 2;
          const dy = y2 - y1;
          const curveMag = Math.abs(dy) > 20 ? Math.min(Math.abs(dy) * 0.4, 80) : 0;

          const d = curveMag > 0
            ? `M${x1},${y1} C${x1 + 30},${y1} ${x2 - 30},${y2} ${x2},${y2}`
            : `M${x1},${y1} L${midX},${y1} L${midX},${y2} L${x2},${y2}`;

          return (
            <path
              key={edge.edge_id}
              d={d}
              fill="none"
              stroke={color}
              strokeWidth={isHighlighted ? 2 : 1}
              strokeDasharray={isHighlighted ? undefined : '4 3'}
              opacity={opacity}
              markerEnd={`url(#${markerId})`}
            />
          );
        })}

        {/* ── Action cells ── */}
        {placedActions.map((placed) => {
          const { node, cx, cy } = placed;
          const status = getNodeStatus(node);
          const colors = STATUS_COLORS[status];
          const isSelected = node.node_id === selectedActionNodeId;
          const isInChain = highlightedChain ? highlightedChain.has(node.node_id) : true;
          const opacity = isInChain ? 1 : 0.25;

          return (
            <g
              key={node.node_id}
              opacity={opacity}
              style={{ cursor: 'pointer' }}
              onMouseEnter={(e) => setTooltip({ node, x: e.clientX, y: e.clientY })}
              onMouseLeave={() => setTooltip(null)}
              onMouseMove={(e) => setTooltip((t) => t ? { ...t, x: e.clientX, y: e.clientY } : null)}
              onClick={() => {
                const assetId = actionToAsset.get(node.node_id);
                if (assetId) onNodeClick?.(assetId);
              }}
            >
              {/* Cell background */}
              <rect
                x={cx}
                y={cy}
                width={CELL_W}
                height={CELL_H}
                rx={6}
                fill={colors.bg}
                stroke={isSelected ? colors.fill : `${colors.stroke}55`}
                strokeWidth={isSelected ? 2 : 1}
              />
              {/* Status bar */}
              <rect
                x={cx}
                y={cy}
                width={STATUS_BAR_W}
                height={CELL_H}
                rx={3}
                fill={colors.fill}
              />
              {/* Action label */}
              <text
                x={cx + STATUS_BAR_W + 8}
                y={cy + 20}
                fontSize={11}
                fontWeight={600}
                fill="#e0e0e0"
                fontFamily="monospace"
              >
                {truncate(node.node_label, 22)}
              </text>
              {/* Payload / time sub-label */}
              <text
                x={cx + STATUS_BAR_W + 8}
                y={cy + 36}
                fontSize={9}
                fill="#777"
                fontFamily="monospace"
              >
                {truncate(
                  node.node_payload_name
                    ? node.node_payload_name
                    : formatTime(node.node_executed_at),
                  26,
                )}
              </text>
              {/* Time (when payload shown) */}
              {node.node_payload_name && node.node_executed_at && (
                <text
                  x={cx + CELL_W - 8}
                  y={cy + CELL_H - 8}
                  fontSize={9}
                  fill="#555"
                  textAnchor="end"
                  fontFamily="monospace"
                >
                  {formatTime(node.node_executed_at)}
                </text>
              )}
            </g>
          );
        })}

        {/* ── "Not attacked" label for asset rows with no actions ── */}
        {assetRows.map((asset, rowIdx) => {
          const hasActions = placedActions.some((p) => p.assetId === asset.node_id);
          if (hasActions) return null;
          const y = HEADER_H + rowIdx * ROW_H;
          return (
            <g key={`untouched-${asset.node_id}`} opacity={0.3}>
              <rect
                x={MATRIX_LEFT + 8}
                y={y + (ROW_H - 22) / 2}
                width={activePhases.length * PHASE_W - 16}
                height={22}
                rx={4}
                fill="none"
                stroke="rgba(255,255,255,0.08)"
                strokeWidth={1}
                strokeDasharray="6 4"
              />
              <text
                x={MATRIX_LEFT + 20}
                y={y + ROW_H / 2 + 5}
                fontSize={10}
                fill="#555"
                fontFamily="monospace"
              >
                Not attacked
              </text>
            </g>
          );
        })}
      </svg>

      {/* ── Hover tooltip ── */}
      {tooltip && (
        <div
          style={{
            position: 'fixed',
            left: tooltip.x + 14,
            top: tooltip.y + 14,
            pointerEvents: 'none',
            zIndex: 999,
            background: 'rgba(22,27,34,0.97)',
            border: `1px solid ${STATUS_COLORS[getNodeStatus(tooltip.node)].stroke}`,
            borderRadius: 8,
            padding: '10px 14px',
            minWidth: 200,
            maxWidth: 320,
            fontSize: 12,
            color: '#e0e0e0',
            boxShadow: '0 4px 24px rgba(0,0,0,0.5)',
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: 4, color: '#fff', fontSize: 13 }}>
            {tooltip.node.node_label}
          </div>
          {tooltip.node.node_payload_name && (
            <div style={{ color: '#aaa', marginBottom: 2 }}>
              <span style={{ color: '#666' }}>Payload: </span>
              {tooltip.node.node_payload_name}
            </div>
          )}
          <div style={{ marginBottom: 2 }}>
            <span style={{ color: '#666' }}>Phase: </span>
            <span style={{ color: PHASE_COLORS[derivePhase(tooltip.node.node_label, tooltip.node.node_payload_name)] }}>
              {derivePhase(tooltip.node.node_label, tooltip.node.node_payload_name)}
            </span>
          </div>
          <div style={{ marginBottom: 2 }}>
            <span style={{ color: '#666' }}>Status: </span>
            <span style={{ color: STATUS_COLORS[getNodeStatus(tooltip.node)].fill }}>
              {getNodeStatus(tooltip.node)}
            </span>
          </div>
          {tooltip.node.node_executed_at && (
            <div style={{ color: '#aaa', marginBottom: 2 }}>
              <span style={{ color: '#666' }}>Executed: </span>
              {new Date(tooltip.node.node_executed_at).toLocaleString()}
            </div>
          )}
          {tooltip.node.node_hostname && (
            <div style={{ color: '#aaa', marginBottom: 2 }}>
              <span style={{ color: '#666' }}>Host: </span>
              {tooltip.node.node_hostname}
              {tooltip.node.node_ip && ` (${tooltip.node.node_ip})`}
            </div>
          )}
          {(tooltip.node.node_credentials_found?.length ?? 0) > 0 && (
            <div style={{ color: '#e91e63', marginTop: 4, fontSize: 11 }}>
              🔑 {tooltip.node.node_credentials_found!.length} credential(s) found
            </div>
          )}
          {(tooltip.node.node_accessed_files?.length ?? 0) > 0 && (
            <div style={{ color: '#ff9800', marginTop: 2, fontSize: 11 }}>
              📄 {tooltip.node.node_accessed_files!.length} file(s) accessed
            </div>
          )}
        </div>
      )}

      {/* ── Zoom controls ── */}
      <div
        style={{
          position: 'absolute',
          bottom: 16,
          right: 16,
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
          zIndex: 10,
          background: 'rgba(22,27,34,0.85)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 8,
          padding: 4,
        }}
      >
        <IconButton size="small" onClick={() => zoomFn(0.8)} sx={{ color: '#aaa', '&:hover': { color: '#fff' } }}>
          <ZoomInIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={handleFit} sx={{ color: '#aaa', '&:hover': { color: '#fff' } }}>
          <FitIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => zoomFn(1.25)} sx={{ color: '#aaa', '&:hover': { color: '#fff' } }}>
          <ZoomOutIcon fontSize="small" />
        </IconButton>
      </div>
    </div>
  );
};

export default AttackPathGraphV4;
