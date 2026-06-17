import { type FunctionComponent, useCallback, useMemo, useRef, useState } from 'react';
import { IconButton } from '@mui/material';
import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import {
  type AttackPathEdge,
  type AttackPathNode,
  type AttackPathDefinition,
  computeLayout,
  computeEndpointLayout,
  computeSubnetLayout,
  deriveEndpointEdges,
  enrichEndpointNodes,
  getConnectedNodes,
  getNodeStatus,
  getZoneStrokeColor,
  type LayoutNode,
  type ZoneLayout,
  STATUS_COLORS,
} from './attackPathUtils';
import type { AttackPathVariantType } from '../../../../../utils/context/AttackPathVariantContext';
import AttackPathGraphV1 from './AttackPathGraphV1';
import AttackPathGraphV2 from './AttackPathGraphV2';
import AttackPathGraphV3 from './AttackPathGraphV3';
import AttackPathGraphV4 from './AttackPathGraphV4';
import AttackPathGraphV4U from './AttackPathGraphV4U';
import AttackPathGraphV4U2 from './AttackPathGraphV4U2';
import AttackPathGraphV4U3 from './AttackPathGraphV4U3';
import AttackPathGraphV4U4 from './AttackPathGraphV4U4';
import AttackPathGraphV4U5 from './AttackPathGraphV4U5';
import AttackPathGraphV5 from './AttackPathGraphV5';
import AttackPathGraphV6 from './AttackPathGraphV6';

interface AttackPathGraphProps {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  onEndpointDetail?: (nodeId: string) => void;
  /** V5: called when user clicks a path line */
  onPathClick?: (assetNodeIds: string[]) => void;
  /** V1: called when user clicks a badge on the path */
  onBadgeClick?: (destAssetId: string) => void;
  /** V3/V5: called when user selects/deselects a path from the legend widget */
  onLegendPathSelect?: (path: AttackPathDefinition | null) => void;
  variantType?: AttackPathVariantType;
  attackPathDefinitions?: AttackPathDefinition[];
  /** V4U: selected attack path ID for the attacker origin map */
  selectedPathId?: string | null;
  /** V6: external request to expand cluster + focus a specific endpoint */
  externalFocusRequest?: { endpointId: string; seq: number; findingId?: string } | null;
}

const AttackPathGraph: FunctionComponent<AttackPathGraphProps> = ({
  nodes,
  edges,
  selectedNodeId,
  onSelectNode,
  onEndpointDetail,
  onPathClick,
  onBadgeClick,
  onLegendPathSelect,
  variantType = 'action',
  attackPathDefinitions,
  selectedPathId,
  externalFocusRequest,
}) => {
  // V1: Non-Intersecting Finding Groups — based on V4.3, each finding category is its own exclusive section
  if (variantType === 'v1') {
    return (
      <AttackPathGraphV1
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => { onSelectNode(assetId ?? ''); }}
        onDetailClick={onEndpointDetail}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  // V2: annotated path map + all actions on each node (Updated Variant 1)
  if (variantType === 'v2') {
    return (
      <AttackPathGraphV2
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onActionChipClick={(actionNodeId) => onSelectNode(actionNodeId)}
        onLegendPathSelect={onLegendPathSelect}
      />
    );
  }

  // V3: delegate entirely to the organic network map component
  if (variantType === 'v3') {
    return (
      <AttackPathGraphV3
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onLegendPathSelect={onLegendPathSelect}
      />
    );
  }

  // V4: delegate entirely to the Kill Chain Matrix component
  if (variantType === 'v4') {
    return (
      <AttackPathGraphV4
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
      />
    );
  }

  // V4U: Attacker Origin Map — injector actions on separate attacker node, recon arrows on demand
  if (variantType === 'v4u') {
    return (
      <AttackPathGraphV4U
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  // V4U2: Attacker Origin Map Updated — recon arrows hidden by default, shown on endpoint/path click
  if (variantType === 'v4u2') {
    return (
      <AttackPathGraphV4U2
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  // V4U3: Variant 4.2 — same as V4U but injector contract → endpoint links always visible
  if (variantType === 'v4u3') {
    return (
      <AttackPathGraphV4U3
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  // V4U4: Variant 4.3 — intersection sets: endpoints can belong to multiple finding groups
  if (variantType === 'v4u4') {
    return (
      <AttackPathGraphV4U4
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId ?? null);
          // No onEndpointDetail here — clicking a node highlights paths only.
          // Drawer is opened via the tooltip Details button (onDetailClick).
        }}
        onDetailClick={onEndpointDetail}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  if (variantType === 'v4u5') {
    return (
      <AttackPathGraphV4U5
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId ?? null);
        }}
        onDetailClick={onEndpointDetail}
        onPathClick={onPathClick}
        onBadgeClick={onBadgeClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
      />
    );
  }

  // V5: organic network map with per-segment failure rendering
  if (variantType === 'v5') {
    return (
      <AttackPathGraphV5
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId);
          onEndpointDetail?.(assetId);
        }}
        onPathClick={onPathClick}
        onLegendPathSelect={onLegendPathSelect}
      />
    );
  }

  // V6: flat organic layout — no zone/subnet grouping, force-directed placement
  if (variantType === 'v6') {
    return (
      <AttackPathGraphV6
        nodes={nodes}
        edges={edges}
        paths={attackPathDefinitions ?? []}
        selectedActionNodeId={selectedNodeId}
        onNodeClick={(assetId) => {
          onSelectNode(assetId ?? null);
          // No onEndpointDetail here — drawer opens via tooltip Details button
        }}
        onDetailClick={onEndpointDetail}
        onPathClick={onPathClick}
        onLegendPathSelect={onLegendPathSelect}
        selectedPathId={selectedPathId}
        externalFocusRequest={externalFocusRequest}
      />
    );
  }

  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 1200, h: 600 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  // Hover tooltip state (for Variant-2 endpoint hover)
  const [hoveredNodeId, setHoveredNodeId] = useState<string | null>(null);
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 });

  // Variant-2: derive endpoint→endpoint compromise edges and enrich ASSET node metadata
  const derivedEdges = useMemo(
    () => (variantType === 'endpoint' ? deriveEndpointEdges(edges) : edges),
    [edges, variantType],
  );
  const enrichedNodes = useMemo(
    () => (variantType === 'endpoint' ? enrichEndpointNodes(nodes, edges) : nodes),
    [nodes, edges, variantType],
  );

  const subnetLayout = useMemo(
    () => variantType === 'endpoint'
      ? computeSubnetLayout(enrichedNodes, derivedEdges)
      : null,
    [enrichedNodes, derivedEdges, variantType],
  );

  const layoutNodes = useMemo(
    () => variantType === 'endpoint'
      ? (subnetLayout?.nodeLayouts ?? computeEndpointLayout(enrichedNodes, derivedEdges))
      : computeLayout(nodes, edges),
    [nodes, edges, enrichedNodes, derivedEdges, variantType, subnetLayout],
  );

  // For connected-node highlight, use derived edges in endpoint variant
  const connectedNodes = useMemo(
    () => (selectedNodeId
      ? getConnectedNodes(selectedNodeId, variantType === 'endpoint' ? derivedEdges : edges)
      : null),
    [selectedNodeId, edges, derivedEdges, variantType],
  );

  const nodeMap = useMemo(() => {
    const map = new Map<string, LayoutNode>();
    for (const node of layoutNodes) map.set(node.node_id, node);
    return map;
  }, [layoutNodes]);

  const getNodeOpacity = useCallback(
    (nodeId: string) => {
      if (!connectedNodes) return 1;
      return connectedNodes.has(nodeId) ? 1 : 0.15;
    },
    [connectedNodes],
  );

  const getEdgeOpacity = useCallback(
    (edge: AttackPathEdge) => {
      if (!connectedNodes) return 1;
      return connectedNodes.has(edge.edge_source) && connectedNodes.has(edge.edge_target) ? 1 : 0.15;
    },
    [connectedNodes],
  );

  const handleNodeClick = useCallback(
    (nodeId: string) => {
      onSelectNode(selectedNodeId === nodeId ? null : nodeId);
      if (variantType === 'endpoint') {
        onEndpointDetail?.(nodeId);
      }
    },
    [selectedNodeId, onSelectNode, variantType, onEndpointDetail],
  );

  const handleNodeHover = useCallback(
    (nodeId: string | null, clientX: number, clientY: number) => {
      if (!nodeId) {
        setHoveredNodeId(null);
        return;
      }
      setTooltipPos({ x: clientX + 14, y: clientY - 10 });
      setHoveredNodeId(nodeId);
    },
    [],
  );

  const handleBgClick = useCallback(() => {
    onSelectNode(null);
  }, [onSelectNode]);

  // Zoom controls
  const zoom = useCallback((factor: number) => {
    setViewBox((vb) => {
      const cx = vb.x + vb.w / 2;
      const cy = vb.y + vb.h / 2;
      const nw = vb.w * factor;
      const nh = vb.h * factor;
      return { x: cx - nw / 2, y: cy - nh / 2, w: nw, h: nh };
    });
  }, []);

  const fitToScreen = useCallback(() => {
    if (variantType === 'endpoint' && subnetLayout && subnetLayout.zoneLayouts.length > 0) {
      const zones = subnetLayout.zoneLayouts;
      const minX = Math.min(...zones.map((z) => z.zone_x)) - 40;
      const minY = Math.min(...zones.map((z) => z.zone_y)) - 40;
      const maxX = Math.max(...zones.map((z) => z.zone_x + z.zone_width)) + 40;
      const maxY = Math.max(...zones.map((z) => z.zone_y + z.zone_height)) + 40;
      setViewBox({ x: minX, y: minY, w: maxX - minX, h: maxY - minY });
      return;
    }
    if (layoutNodes.length === 0) return;
    const visibleNodes = layoutNodes.filter((n) => n.x > -9000);
    if (visibleNodes.length === 0) return;
    const xs = visibleNodes.map((n) => n.x);
    const ys = visibleNodes.map((n) => n.y);
    const minX = Math.min(...xs) - 80;
    const minY = Math.min(...ys) - 80;
    const maxX = Math.max(...xs) + 240;
    const maxY = Math.max(...ys) + 200;
    setViewBox({ x: minX, y: minY, w: maxX - minX, h: maxY - minY });
  }, [layoutNodes, variantType, subnetLayout]);

  // Pan via mouse drag
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.target === svgRef.current || (e.target as SVGElement).tagName === 'rect') {
      setIsPanning(true);
      setPanStart({ x: e.clientX, y: e.clientY });
    }
  }, []);

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (!isPanning) return;
      const dx = (e.clientX - panStart.x) * (viewBox.w / (svgRef.current?.clientWidth ?? 1));
      const dy = (e.clientY - panStart.y) * (viewBox.h / (svgRef.current?.clientHeight ?? 1));
      setViewBox((vb) => ({ ...vb, x: vb.x - dx, y: vb.y - dy }));
      setPanStart({ x: e.clientX, y: e.clientY });
    },
    [isPanning, panStart, viewBox],
  );

  const handleMouseUp = useCallback(() => setIsPanning(false), []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const factor = e.deltaY > 0 ? 1.1 : 0.9;
    zoom(factor);
  }, [zoom]);

  return (
    <div ref={containerRef} style={{ position: 'relative', flex: 1, height: '100%', overflow: 'hidden' }}>
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        style={{ cursor: isPanning ? 'grabbing' : 'grab' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      >
        {/* Arrow marker definitions */}
        <defs>
          <marker id="arrow-chain" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="#64b5f6" />
          </marker>
          <marker id="arrow-compromise" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="#e91e63" />
          </marker>
          <filter id="glow-selected">
            <feGaussianBlur stdDeviation="3" result="coloredBlur" />
            <feMerge>
              <feMergeNode in="coloredBlur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Background (click to deselect) */}
        <rect
          x={viewBox.x}
          y={viewBox.y}
          width={viewBox.w}
          height={viewBox.h}
          fill="transparent"
          onClick={handleBgClick}
        />

        {variantType === 'endpoint'
          ? <SubnetEndpointGraph
              layoutNodes={layoutNodes}
              edges={derivedEdges}
              nodeMap={nodeMap}
              selectedNodeId={selectedNodeId}
              getNodeOpacity={getNodeOpacity}
              getEdgeOpacity={getEdgeOpacity}
              onNodeClick={handleNodeClick}
              onNodeHover={handleNodeHover}
              zoneLayouts={subnetLayout?.zoneLayouts ?? []}
            />
          : <ActionGraph
              layoutNodes={layoutNodes}
              edges={edges}
              nodeMap={nodeMap}
              selectedNodeId={selectedNodeId}
              getNodeOpacity={getNodeOpacity}
              getEdgeOpacity={getEdgeOpacity}
              onNodeClick={handleNodeClick}
            />
        }
      </svg>

      {/* Hover tooltip for endpoint nodes */}
      {hoveredNodeId && variantType === 'endpoint' && (() => {
        const hNode = layoutNodes.find((n) => n.node_id === hoveredNodeId);
        if (!hNode) return null;
        return (
          <div style={{
            position: 'fixed',
            left: tooltipPos.x,
            top: tooltipPos.y,
            pointerEvents: 'none',
            zIndex: 9999,
            backgroundColor: 'rgba(15, 15, 25, 0.97)',
            border: '1px solid rgba(255,255,255,0.15)',
            borderRadius: 6,
            padding: '8px 12px',
            minWidth: 200,
            maxWidth: 280,
            boxShadow: '0 4px 20px rgba(0,0,0,0.6)',
          }}>
            <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6, color: '#fff' }}>
              {hNode.node_hostname ?? hNode.node_label}
            </div>
            {hNode.node_ip && (
              <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
                <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>IP</span>
                <span style={{ fontSize: 10, fontFamily: 'monospace', color: '#64b5f6' }}>{hNode.node_ip}</span>
              </div>
            )}
            {hNode.node_platform && (
              <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
                <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Platform</span>
                <span style={{ fontSize: 10, opacity: 0.8 }}>{hNode.node_platform}</span>
              </div>
            )}
            {hNode.node_user_privileges && (
              <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
                <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>User</span>
                <span style={{ fontSize: 10, color: '#ff9800' }}>{hNode.node_user_privileges}</span>
              </div>
            )}
            {hNode.node_zone && (
              <div style={{ display: 'flex', gap: 6, marginBottom: 3 }}>
                <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Zone</span>
                <span style={{ fontSize: 10, opacity: 0.75 }}>{hNode.node_zone}</span>
              </div>
            )}
            {hNode.node_subnet && (
              <div style={{ display: 'flex', gap: 6 }}>
                <span style={{ fontSize: 10, opacity: 0.45, minWidth: 68 }}>Subnet</span>
                <span style={{ fontSize: 10, fontFamily: 'monospace', opacity: 0.6 }}>{hNode.node_subnet}</span>
              </div>
            )}
            {hNode.node_is_entry_point && (
              <div style={{ marginTop: 6, fontSize: 10, color: '#ffd54f' }}>★ Entry point</div>
            )}
            {hNode.node_is_pivot && (
              <div style={{ marginTop: 2, fontSize: 10, color: '#ff9800' }}>↔ Pivot node</div>
            )}
            {hNode.node_untouched && (
              <div style={{ marginTop: 2, fontSize: 10, color: '#9e9e9e', fontStyle: 'italic' }}>⬡ Not attacked</div>
            )}
          </div>
        );
      })()}

      {/* Zoom controls */}
      <div style={{
        position: 'absolute',
        bottom: 16,
        right: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        backgroundColor: 'rgba(30, 30, 46, 0.85)',
        borderRadius: 8,
        padding: 4,
      }}>
        <IconButton size="small" onClick={() => zoom(0.8)} sx={{ color: '#fff' }}>
          <ZoomInIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={() => zoom(1.25)} sx={{ color: '#fff' }}>
          <ZoomOutIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={fitToScreen} sx={{ color: '#fff' }}>
          <FitIcon fontSize="small" />
        </IconButton>
      </div>
    </div>
  );
};

// ── Variant-1: Action graph (circles per action, rectangles for prevented/detected) ──

interface SubGraphProps {
  layoutNodes: LayoutNode[];
  edges: AttackPathEdge[];
  nodeMap: Map<string, LayoutNode>;
  selectedNodeId: string | null;
  getNodeOpacity: (id: string) => number;
  getEdgeOpacity: (edge: AttackPathEdge) => number;
  onNodeClick: (id: string) => void;
}

const ActionGraph: FunctionComponent<SubGraphProps> = ({
  layoutNodes, edges, nodeMap, selectedNodeId, getNodeOpacity, getEdgeOpacity, onNodeClick,
}) => (
  <>
    {/* Edges */}
    {edges.map((edge) => {
      const source = nodeMap.get(edge.edge_source);
      const target = nodeMap.get(edge.edge_target);
      if (!source || !target) return null;
      const sx = source.x + source.width / 2;
      const sy = source.y + source.height / 2;
      const tx = target.x + target.width / 2;
      const ty = target.y + target.height / 2;
      const mx = (sx + tx) / 2;
      const my = (sy + ty) / 2;
      const isChainFlow = edge.edge_type === 'chain_flow';
      const opacity = getEdgeOpacity(edge);

      return (
        <g key={edge.edge_id} opacity={opacity}>
          <path
            d={`M ${sx} ${sy} Q ${mx} ${sy} ${mx} ${my} Q ${mx} ${ty} ${tx} ${ty}`}
            fill="none"
            stroke={isChainFlow ? '#64b5f6' : '#666'}
            strokeWidth={isChainFlow ? 2 : 1.5}
            strokeDasharray={isChainFlow ? undefined : '4 3'}
            markerEnd={isChainFlow ? 'url(#arrow-chain)' : undefined}
          />
          {isChainFlow && (
            <circle r={3} fill="#64b5f6">
              <animateMotion
                dur="2s"
                repeatCount="indefinite"
                path={`M ${sx} ${sy} Q ${mx} ${sy} ${mx} ${my} Q ${mx} ${ty} ${tx} ${ty}`}
              />
            </circle>
          )}
          {edge.edge_label && (
            <text x={mx} y={my - 8} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.7)" style={{ pointerEvents: 'none' }}>
              {edge.edge_label}
            </text>
          )}
        </g>
      );
    })}

    {/* Nodes */}
    {layoutNodes.map((node) => {
      const opacity = getNodeOpacity(node.node_id);
      const isSelected = selectedNodeId === node.node_id;

      if (node.node_type === 'ASSET') {
        // Larger ASSET rectangle with icon area
        const status = getNodeStatus(node);
        const colors = STATUS_COLORS[status];
        const W = node.width;
        const H = node.height;

        return (
          <g
            key={node.node_id}
            opacity={opacity}
            style={{ cursor: 'pointer', transition: 'opacity 0.3s' }}
            onClick={() => onNodeClick(node.node_id)}
          >
            {/* Glow for selected */}
            {isSelected && (
              <rect x={node.x - 4} y={node.y - 4} width={W + 8} height={H + 8} rx={10}
                fill="none" stroke={colors.fill} strokeWidth={2} opacity={0.4} filter="url(#glow-selected)" />
            )}
            <rect
              x={node.x} y={node.y} width={W} height={H} rx={8}
              fill="rgba(30, 30, 46, 0.95)"
              stroke={isSelected ? colors.fill : '#555'}
              strokeWidth={isSelected ? 2.5 : 1.5}
            />
            {/* Colored top bar */}
            <rect x={node.x} y={node.y} width={W} height={6} rx={4} fill={colors.fill} />
            {/* Hostname */}
            <text x={node.x + W / 2} y={node.y + 28} textAnchor="middle" fontSize={12} fontWeight={600} fill="#fff">
              {node.node_label.length > 22 ? `${node.node_label.slice(0, 20)}…` : node.node_label}
            </text>
            {node.node_ip && (
              <text x={node.x + W / 2} y={node.y + 44} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.6)">
                {node.node_ip}
              </text>
            )}
            {node.node_platform && (
              <text x={node.x + W / 2} y={node.y + 58} textAnchor="middle" fontSize={9} fill="rgba(255,255,255,0.4)">
                {node.node_platform}
              </text>
            )}
            {node.node_user_privileges && (
              <text x={node.x + W / 2} y={node.y + 72} textAnchor="middle" fontSize={9} fill="rgba(255,152,0,0.8)">
                👤 {node.node_user_privileges}
              </text>
            )}
          </g>
        );
      }

      // ACTION node: circle
      const status = getNodeStatus(node);
      const colors = STATUS_COLORS[status];
      const cx = node.x + node.width / 2;
      const cy = node.y + node.height / 2;
      const r = 32;

      return (
        <g
          key={node.node_id}
          opacity={opacity}
          style={{ cursor: 'pointer', transition: 'opacity 0.3s' }}
          onClick={() => onNodeClick(node.node_id)}
        >
          {isSelected && (
            <circle cx={cx} cy={cy} r={r + 8} fill={colors.fill} opacity={0.15} filter="url(#glow-selected)" />
          )}
          <circle cx={cx} cy={cy} r={r + 4} fill="none"
            stroke={isSelected ? colors.fill : 'transparent'} strokeWidth={2} />
          <circle cx={cx} cy={cy} r={r} fill={colors.fill} stroke={colors.stroke} strokeWidth={2} />
          {/* Status text in circle */}
          <text x={cx} y={cy + 4} textAnchor="middle" fontSize={9} fontWeight={600} fill="rgba(255,255,255,0.9)" style={{ pointerEvents: 'none' }}>
            {status.toUpperCase()}
          </text>
          {/* Label below */}
          <text x={cx} y={cy + r + 16} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.85)" style={{ pointerEvents: 'none' }}>
            {node.node_label.length > 20 ? `${node.node_label.slice(0, 18)}…` : node.node_label}
          </text>
        </g>
      );
    })}
  </>
);

// ── Variant-2: Endpoint graph (circles per endpoint, compromise arrows) ──

const EndpointGraph: FunctionComponent<SubGraphProps> = ({
  layoutNodes, edges, nodeMap, selectedNodeId, getNodeOpacity, getEdgeOpacity, onNodeClick,
}) => (
  <>
    {/* Compromise edges */}
    {edges
      .filter((e) => e.edge_type === 'compromise' || e.edge_type === 'chain_flow')
      .map((edge) => {
        const source = nodeMap.get(edge.edge_source);
        const target = nodeMap.get(edge.edge_target);
        if (!source || !target) return null;

        const R = 44; // circle radius
        const sx = source.x + source.width / 2;
        const sy = source.y + source.height / 2;
        const tx = target.x + target.width / 2;
        const ty = target.y + target.height / 2;

        // Adjust endpoints to circle edge
        const angle = Math.atan2(ty - sy, tx - sx);
        const x1 = sx + R * Math.cos(angle);
        const y1 = sy + R * Math.sin(angle);
        const x2 = tx - R * Math.cos(angle);
        const y2 = ty - R * Math.sin(angle);

        const mx = (x1 + x2) / 2;
        const my = (y1 + y2) / 2 - 30;
        const opacity = getEdgeOpacity(edge);

        return (
          <g key={edge.edge_id} opacity={opacity}>
            <path
              d={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`}
              fill="none"
              stroke="#e91e63"
              strokeWidth={2.5}
              markerEnd="url(#arrow-compromise)"
            />
            {/* Animated attack particle */}
            <circle r={4} fill="#e91e63" opacity={0.8}>
              <animateMotion dur="1.8s" repeatCount="indefinite"
                path={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`} />
            </circle>
            {edge.edge_label && (
              <text x={mx} y={my - 6} textAnchor="middle" fontSize={10} fill="rgba(255,255,255,0.7)"
                style={{ pointerEvents: 'none' }}>
                {edge.edge_label}
              </text>
            )}
          </g>
        );
      })}

    {/* Endpoint circles */}
    {layoutNodes.map((node) => {
      const opacity = getNodeOpacity(node.node_id);
      const isSelected = selectedNodeId === node.node_id;
      const status = getNodeStatus(node);
      const colors = STATUS_COLORS[status];
      const cx = node.x + node.width / 2;
      const cy = node.y + node.height / 2;
      const R = 44;

      return (
        <g
          key={node.node_id}
          opacity={opacity}
          style={{ cursor: 'pointer', transition: 'opacity 0.3s' }}
          onClick={() => onNodeClick(node.node_id)}
        >
          {/* Glow ring for selected */}
          {isSelected && (
            <circle cx={cx} cy={cy} r={R + 10} fill={colors.fill} opacity={0.12} filter="url(#glow-selected)" />
          )}
          {/* Outer ring */}
          <circle cx={cx} cy={cy} r={R + 4} fill="none"
            stroke={isSelected ? colors.fill : 'rgba(255,255,255,0.15)'} strokeWidth={isSelected ? 2.5 : 1} />
          {/* Main circle */}
          <circle cx={cx} cy={cy} r={R}
            fill={`rgba(30,30,46,0.95)`}
            stroke={colors.fill}
            strokeWidth={3}
          />
          {/* Colored inner ring */}
          <circle cx={cx} cy={cy} r={R - 6} fill={`${colors.fill}18`} />

          {/* Hostname (truncated) */}
          <text x={cx} y={cy - 6} textAnchor="middle" fontSize={11} fontWeight={700} fill="#fff"
            style={{ pointerEvents: 'none' }}>
            {(node.node_hostname ?? node.node_label).length > 14
              ? `${(node.node_hostname ?? node.node_label).slice(0, 12)}…`
              : (node.node_hostname ?? node.node_label)}
          </text>
          {/* IP */}
          {node.node_ip && (
            <text x={cx} y={cy + 8} textAnchor="middle" fontSize={9} fill="rgba(255,255,255,0.6)"
              style={{ pointerEvents: 'none' }}>
              {node.node_ip}
            </text>
          )}
          {/* Status indicator dot */}
          <circle cx={cx + R - 8} cy={cy - R + 8} r={7} fill={colors.fill} stroke="rgba(30,30,46,1)" strokeWidth={2} />

          {/* Label below circle */}
          <text x={cx} y={cy + R + 18} textAnchor="middle" fontSize={11} fontWeight={600}
            fill="rgba(255,255,255,0.85)" style={{ pointerEvents: 'none' }}>
            {node.node_label.length > 22 ? `${node.node_label.slice(0, 20)}…` : node.node_label}
          </text>
          {node.node_user_privileges && (
            <text x={cx} y={cy + R + 32} textAnchor="middle" fontSize={9}
              fill="rgba(255,152,0,0.75)" style={{ pointerEvents: 'none' }}>
              {node.node_user_privileges}
            </text>
          )}
        </g>
      );
    })}
  </>
);

// ── Variant-2 Subnet: Zone-grouped endpoint graph ──

interface SubnetSubGraphProps extends SubGraphProps {
  zoneLayouts: ZoneLayout[];
  onNodeHover?: (nodeId: string | null, clientX: number, clientY: number) => void;
}

const SubnetEndpointGraph: FunctionComponent<SubnetSubGraphProps> = ({
  layoutNodes, edges, nodeMap, selectedNodeId, getNodeOpacity, getEdgeOpacity, onNodeClick, onNodeHover, zoneLayouts,
}) => {
  const R = 38; // circle radius

  // Filter out hidden ACTION nodes (placed at -9999)
  const visibleNodes = layoutNodes.filter((n) => n.x > -9000 && n.node_type === 'ASSET');

  // Edges: compromise/lateral_movement = solid thick; discovery = dashed
  const compromiseEdges = edges.filter(
    (e) => e.edge_type === 'compromise' || e.edge_type === 'lateral_movement' || e.edge_type === 'chain_flow',
  );
  const discoveryEdges = edges.filter((e) => e.edge_type === 'discovery');

  return (
    <>
      {/* Zone background boxes */}
      {zoneLayouts.map((zone, idx) => (
        <g key={zone.zone_id}>
          <rect
            x={zone.zone_x}
            y={zone.zone_y}
            width={zone.zone_width}
            height={zone.zone_height}
            rx={12}
            fill={zone.zone_color}
            stroke={getZoneStrokeColor(idx)}
            strokeWidth={1.5}
          />
          {/* Zone label */}
          <text
            x={zone.zone_x + 14}
            y={zone.zone_y + 20}
            fontSize={11}
            fontWeight={700}
            fill="rgba(255,255,255,0.65)"
            style={{ pointerEvents: 'none', fontFamily: 'monospace' }}
          >
            {zone.zone_name}
            {zone.zone_subnet ? ` · ${zone.zone_subnet}` : ''}
          </text>
        </g>
      ))}

      {/* Compromise/lateral movement edges */}
      {compromiseEdges.map((edge) => {
        const source = nodeMap.get(edge.edge_source);
        const target = nodeMap.get(edge.edge_target);
        if (!source || !target || source.x < -9000 || target.x < -9000) return null;

        const sx = source.x + source.width / 2;
        const sy = source.y + source.height / 2;
        const tx = target.x + target.width / 2;
        const ty = target.y + target.height / 2;

        const angle = Math.atan2(ty - sy, tx - sx);
        const x1 = sx + R * Math.cos(angle);
        const y1 = sy + R * Math.sin(angle);
        const x2 = tx - R * Math.cos(angle);
        const y2 = ty - R * Math.sin(angle);

        // Curved path: bend outward to avoid overlap
        const mx = (x1 + x2) / 2;
        const my = (y1 + y2) / 2 - 40;
        const opacity = getEdgeOpacity(edge);

        return (
          <g key={edge.edge_id} opacity={opacity}>
            <path
              d={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`}
              fill="none"
              stroke="#e91e63"
              strokeWidth={3}
              markerEnd="url(#arrow-compromise)"
            />
            <circle r={5} fill="#e91e63" opacity={0.9}>
              <animateMotion dur="1.6s" repeatCount="indefinite"
                path={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`} />
            </circle>
            {edge.edge_label && (
              <text x={mx} y={my - 8} textAnchor="middle" fontSize={10}
                fill="rgba(255,255,255,0.75)" style={{ pointerEvents: 'none' }}
                fontWeight={600}>
                {edge.edge_label}
              </text>
            )}
          </g>
        );
      })}

      {/* Discovery edges (dashed) */}
      {discoveryEdges.map((edge) => {
        const source = nodeMap.get(edge.edge_source);
        const target = nodeMap.get(edge.edge_target);
        if (!source || !target || source.x < -9000 || target.x < -9000) return null;

        const sx = source.x + source.width / 2;
        const sy = source.y + source.height / 2;
        const tx = target.x + target.width / 2;
        const ty = target.y + target.height / 2;

        const angle = Math.atan2(ty - sy, tx - sx);
        const x1 = sx + R * Math.cos(angle);
        const y1 = sy + R * Math.sin(angle);
        const x2 = tx - R * Math.cos(angle);
        const y2 = ty - R * Math.sin(angle);

        const mx = (x1 + x2) / 2;
        const my = (y1 + y2) / 2 + 30;
        const opacity = getEdgeOpacity(edge);

        return (
          <g key={edge.edge_id} opacity={opacity}>
            <path
              d={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`}
              fill="none"
              stroke="rgba(158,158,158,0.6)"
              strokeWidth={1.5}
              strokeDasharray="6 4"
            />
            {edge.edge_label && (
              <text x={mx} y={my + 14} textAnchor="middle" fontSize={9}
                fill="rgba(158,158,158,0.7)" style={{ pointerEvents: 'none' }}>
                {edge.edge_label}
              </text>
            )}
          </g>
        );
      })}

      {/* Endpoint circles */}
      {visibleNodes.map((node) => {
        const opacity = getNodeOpacity(node.node_id);
        const isSelected = selectedNodeId === node.node_id;
        const isUntouched = node.node_untouched === true;
        const isEntry = node.node_is_entry_point === true;
        const isPivot = node.node_is_pivot === true;

        // Untouched nodes use gray; otherwise use status color
        const status = isUntouched ? 'pending' : getNodeStatus(node);
        const colors = STATUS_COLORS[status];
        const cx = node.x + node.width / 2;
        const cy = node.y + node.height / 2;

        return (
          <g
            key={node.node_id}
            opacity={isUntouched ? Math.min(opacity, 0.35) : opacity}
            style={{ cursor: 'pointer', transition: 'opacity 0.3s', pointerEvents: 'all' }}
            onClick={() => onNodeClick(node.node_id)}
            onMouseEnter={(e) => { e.stopPropagation(); onNodeHover?.(node.node_id, e.clientX, e.clientY); }}
            onMouseMove={(e) => { e.stopPropagation(); onNodeHover?.(node.node_id, e.clientX, e.clientY); }}
            onMouseLeave={(e) => { e.stopPropagation(); onNodeHover?.(null, 0, 0); }}
          >
            {/* Selected glow */}
            {isSelected && (
              <circle cx={cx} cy={cy} r={R + 12} fill={colors.fill} opacity={0.15}
                filter="url(#glow-selected)" />
            )}

            {/* Pivot: double ring */}
            {isPivot && (
              <circle cx={cx} cy={cy} r={R + 8} fill="none"
                stroke={colors.fill} strokeWidth={1.5} opacity={0.5}
                strokeDasharray="4 3" />
            )}

            {/* Outer ring */}
            <circle cx={cx} cy={cy} r={R + 4} fill="none"
              stroke={isSelected ? colors.fill : 'rgba(255,255,255,0.15)'}
              strokeWidth={isSelected ? 2.5 : 1} />

            {/* Main circle */}
            <circle cx={cx} cy={cy} r={R}
              fill="rgba(20,20,36,0.95)"
              stroke={isEntry ? '#fff' : colors.fill}
              strokeWidth={isEntry ? 3.5 : 2.5}
            />

            {/* Colored inner fill */}
            <circle cx={cx} cy={cy} r={R - 6}
              fill={isUntouched ? 'rgba(100,100,100,0.15)' : `${colors.fill}20`} />

            {/* Entry point star icon */}
            {isEntry && (
              <text x={cx} y={cy - R + 14} textAnchor="middle" fontSize={12}
                fill="#ffd54f" style={{ pointerEvents: 'none' }}>
                ★
              </text>
            )}

            {/* Hostname */}
            <text x={cx} y={isEntry ? cy - 3 : cy + 1} textAnchor="middle"
              fontSize={10} fontWeight={700} fill={isUntouched ? 'rgba(255,255,255,0.4)' : '#fff'}
              style={{ pointerEvents: 'none' }}>
              {(node.node_hostname ?? node.node_label).length > 12
                ? `${(node.node_hostname ?? node.node_label).slice(0, 10)}…`
                : (node.node_hostname ?? node.node_label)}
            </text>

            {/* IP */}
            {node.node_ip && (
              <text x={cx} y={cy + 13} textAnchor="middle" fontSize={8}
                fill={isUntouched ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.55)'}
                style={{ pointerEvents: 'none' }}>
                {node.node_ip}
              </text>
            )}

            {/* Status dot */}
            <circle cx={cx + R - 8} cy={cy - R + 8} r={7}
              fill={colors.fill} stroke="rgba(20,20,36,1)" strokeWidth={2} />

            {/* Label below */}
            <text x={cx} y={cy + R + 18} textAnchor="middle" fontSize={10} fontWeight={600}
              fill={isUntouched ? 'rgba(255,255,255,0.4)' : 'rgba(255,255,255,0.85)'}
              style={{ pointerEvents: 'none' }}>
              {node.node_label.length > 16 ? `${node.node_label.slice(0, 14)}…` : node.node_label}
            </text>

            {/* Pivot badge */}
            {isPivot && (
              <text x={cx} y={cy + R + 31} textAnchor="middle" fontSize={8}
                fill="rgba(255,183,77,0.85)" style={{ pointerEvents: 'none' }}>
                ↔ pivot
              </text>
            )}
          </g>
        );
      })}
    </>
  );
};

export default AttackPathGraph;
