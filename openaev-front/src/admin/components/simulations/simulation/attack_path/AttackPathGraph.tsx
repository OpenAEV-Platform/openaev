import { type FunctionComponent, useCallback, useMemo, useRef, useState } from 'react';
import { IconButton } from '@mui/material';
import { Add as ZoomInIcon, FitScreen as FitIcon, Remove as ZoomOutIcon } from '@mui/icons-material';
import {
  type AttackPathEdge,
  type AttackPathNode,
  type AttackStepStatus,
  computeLayout,
  getConnectedNodes,
  getNodeStatus,
  type LayoutNode,
  STATUS_COLORS,
} from './attackPathUtils';

interface AttackPathGraphProps {
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
}

const AttackPathGraph: FunctionComponent<AttackPathGraphProps> = ({
  nodes,
  edges,
  selectedNodeId,
  onSelectNode,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const [viewBox, setViewBox] = useState({ x: 0, y: 0, w: 1200, h: 600 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  const layoutNodes = useMemo(() => computeLayout(nodes, edges), [nodes, edges]);
  const connectedNodes = useMemo(
    () => (selectedNodeId ? getConnectedNodes(selectedNodeId, edges) : null),
    [selectedNodeId, edges],
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
    },
    [selectedNodeId, onSelectNode],
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
    if (layoutNodes.length === 0) return;
    const xs = layoutNodes.map((n) => n.x);
    const ys = layoutNodes.map((n) => n.y);
    const minX = Math.min(...xs) - 80;
    const minY = Math.min(...ys) - 80;
    const maxX = Math.max(...xs) + 240;
    const maxY = Math.max(...ys) + 200;
    setViewBox({ x: minX, y: minY, w: maxX - minX, h: maxY - minY });
  }, [layoutNodes]);

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
    <div style={{ position: 'relative', flex: 1, height: '100%', overflow: 'hidden' }}>
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
        {/* Background (click to deselect) */}
        <rect
          x={viewBox.x}
          y={viewBox.y}
          width={viewBox.w}
          height={viewBox.h}
          fill="transparent"
          onClick={handleBgClick}
        />

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
                markerEnd={isChainFlow ? 'url(#arrow)' : undefined}
              />
              {/* Animated particle on chain_flow edges */}
              {isChainFlow && (
                <circle r={3} fill="#64b5f6">
                  <animateMotion
                    dur="2s"
                    repeatCount="indefinite"
                    path={`M ${sx} ${sy} Q ${mx} ${sy} ${mx} ${my} Q ${mx} ${ty} ${tx} ${ty}`}
                  />
                </circle>
              )}
              {/* Edge label */}
              {edge.edge_label && (
                <text
                  x={mx}
                  y={my - 8}
                  textAnchor="middle"
                  fontSize={10}
                  fill="rgba(255,255,255,0.7)"
                  style={{ pointerEvents: 'none' }}
                >
                  {edge.edge_label}
                </text>
              )}
            </g>
          );
        })}

        {/* Arrow marker definition */}
        <defs>
          <marker id="arrow" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="#64b5f6" />
          </marker>
        </defs>

        {/* Nodes */}
        {layoutNodes.map((node) => {
          const opacity = getNodeOpacity(node.node_id);
          const isSelected = selectedNodeId === node.node_id;

          if (node.node_type === 'ASSET') {
            return (
              <g
                key={node.node_id}
                opacity={opacity}
                style={{ cursor: 'pointer', transition: 'opacity 0.3s' }}
                onClick={() => handleNodeClick(node.node_id)}
              >
                <rect
                  x={node.x}
                  y={node.y}
                  width={node.width}
                  height={node.height}
                  rx={6}
                  fill="rgba(30, 30, 46, 0.9)"
                  stroke={isSelected ? '#fff' : '#555'}
                  strokeWidth={isSelected ? 2.5 : 1.5}
                />
                <text
                  x={node.x + node.width / 2}
                  y={node.y + 28}
                  textAnchor="middle"
                  fontSize={12}
                  fontWeight={600}
                  fill="#fff"
                >
                  {node.node_label}
                </text>
                {node.node_ip && (
                  <text
                    x={node.x + node.width / 2}
                    y={node.y + 46}
                    textAnchor="middle"
                    fontSize={10}
                    fill="rgba(255,255,255,0.6)"
                  >
                    {node.node_ip}
                  </text>
                )}
                {node.node_platform && (
                  <text
                    x={node.x + node.width / 2}
                    y={node.y + 62}
                    textAnchor="middle"
                    fontSize={9}
                    fill="rgba(255,255,255,0.4)"
                  >
                    {node.node_platform}
                  </text>
                )}
              </g>
            );
          }

          // ACTION node (circle)
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
              onClick={() => handleNodeClick(node.node_id)}
            >
              <circle
                cx={cx}
                cy={cy}
                r={r + 4}
                fill="none"
                stroke={isSelected ? '#fff' : 'transparent'}
                strokeWidth={2}
              />
              <circle
                cx={cx}
                cy={cy}
                r={r}
                fill={colors.fill}
                stroke={colors.stroke}
                strokeWidth={2}
              />
              {/* Payload name below the circle */}
              <text
                x={cx}
                y={cy + r + 16}
                textAnchor="middle"
                fontSize={10}
                fill="rgba(255,255,255,0.85)"
                style={{ pointerEvents: 'none' }}
              >
                {node.node_label.length > 20 ? `${node.node_label.slice(0, 18)}…` : node.node_label}
              </text>
            </g>
          );
        })}
      </svg>

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

export default AttackPathGraph;
