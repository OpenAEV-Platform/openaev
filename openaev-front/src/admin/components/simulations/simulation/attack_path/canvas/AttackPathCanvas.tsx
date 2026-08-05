import { AddOutlined, CenterFocusStrongOutlined, RemoveOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import graphTooltipSlotProps from '../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import { AP_FLOW_NODE_TYPE, type AttackPathFlowEdge, type AttackPathFlowNode, type AttackPathFlowNodeData } from '../attack-path-flow-helpers';
import { AP_NODE_ENTER_CLASS } from '../attack-path-styles';
import AttackPathConnectors from './AttackPathConnectors';
import AttackPathMiniMap from './AttackPathMiniMap';
import { type CanvasRect, computeCardRects, computeContentBounds, computeEdgeGeometry } from './canvas-geometry';
import ActionCard from './cards/ActionCard';
import EndpointClusterCard from './cards/EndpointClusterCard';
import FindingCard from './cards/FindingCard';
import FindingClusterCard from './cards/FindingClusterCard';
import FindingTypeCard from './cards/FindingTypeCard';
import TargetCard from './cards/TargetCard';

export interface AttackPathFocusRequest {
  nodeId: string;
  nonce: number;
}

export interface AttackPathPursuitRequest {
  nodeIds: readonly string[];
  nonce: number;
}

interface AttackPathCanvasProps {
  nodes: AttackPathFlowNode[];
  edges: AttackPathFlowEdge[];
  enterNodeIds?: Set<string>;
  onEndpointClick?: (nodeId: string, ref?: string, label?: string) => void;
  onClusterClick?: (injectorId: string, kind: 'header' | 'overflow') => void;
  onEndpointClusterClick?: (clusterId: string) => void;
  onFindingClusterClick?: (clusterId: string, typeFindings: string | undefined, injectorId: string | undefined, endpointRef: string | undefined, kind: 'header' | 'overflow' | 'typeOverflow') => void;
  onFindingSelect?: (nodeId: string, type?: string, value?: string, assetNodeId?: string) => void;
  onInjectorSelect?: (injectorId: string, label?: string) => void;
  onBackgroundClick?: () => void;
  /** Center the camera on a node (cross-focus); nonce re-fires on repeat. */
  focusRequest?: AttackPathFocusRequest | null;
  /** Fit the whole graph; nonce re-fires on repeat. */
  fitRequest?: number;
  /**
   * Live pursuit: pan (at the CURRENT zoom) to center the nodes the latest delta introduced, instead
   * of re-fitting the whole graph. Skipped while the user recently panned/zoomed/fitted manually.
   */
  pursuitRequest?: AttackPathPursuitRequest | null;
  /**
   * While true the canvas never snap-fits itself when the world grows — the camera is driven only by
   * pursuit and by explicit fit/focus requests, so a live run stays framed on the action.
   */
  pursuitActive?: boolean;
  showMiniMap?: boolean;
  /** Overlay rendered in the bottom-right stack, under the minimap (the graph legend). */
  legend?: ReactNode;
}

interface Camera {
  zoom: number;
  x: number;
  y: number;
}

const MIN_ZOOM = 0.15;
const MAX_ZOOM = 2.5;
const FIT_PADDING = 56;
// Initial/fit zoom is clamped to this range (Logic PanZoom's behaviour): a tiny chain is not blown
// up, and a huge one stays readable — anchored on its start — instead of shrinking into a speck.
const INITIAL_MIN_ZOOM = 0.6;
const INITIAL_MAX_ZOOM = 1.1;
const ZOOM_STEP = 1.15;
const DRAG_THRESHOLD = 4;
const CULL_MARGIN = 240;
// After a manual pan/zoom/fit, live pursuit stays hands-off for this long before following again,
// so the user can inspect (or hold the full overview) without the camera being snatched away.
const PURSUIT_MANUAL_PAUSE_MS = 6000;

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/**
 * Custom pan/zoom canvas for the attack-path graph, modelled on the chaining Logic view's PanZoom:
 * a fixed logical coordinate space rendered through one CSS transform, fit-to-content, Ctrl/Cmd +
 * wheel zoom (never hijacks plain scroll), drag-to-pan, and explicit zoom/fit controls placed
 * bottom-left exactly like the Logic canvas. No graph library.
 *
 * On top of that it keeps every attack-path feature: animated camera for click-to-focus and
 * fit-all, off-screen culling for large graphs, a click-to-navigate minimap, and an entrance fade
 * for the nodes a live delta just introduced.
 */
const AttackPathCanvas = ({
  nodes,
  edges,
  enterNodeIds,
  onEndpointClick,
  onClusterClick,
  onEndpointClusterClick,
  onFindingClusterClick,
  onFindingSelect,
  onInjectorSelect,
  onBackgroundClick,
  focusRequest,
  fitRequest,
  pursuitRequest,
  pursuitActive = false,
  showMiniMap = true,
  legend,
}: AttackPathCanvasProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const containerRef = useRef<HTMLDivElement>(null);
  const [camera, setCamera] = useState<Camera>({
    zoom: 1,
    x: 0,
    y: 0,
  });
  const [panning, setPanning] = useState(false);
  const [size, setSize] = useState({
    w: 0,
    h: 0,
  });

  // Card rectangles (world coords) normalised so the content bounds start at (0,0).
  const { rects, worldWidth, worldHeight, edgeGeometries } = useMemo(() => {
    const raw = computeCardRects(nodes);
    const bounds = computeContentBounds(raw);
    const normalised = new Map<string, CanvasRect>();
    raw.forEach((r, id) => {
      normalised.set(id, {
        x: r.x - bounds.x,
        y: r.y - bounds.y,
        width: r.width,
        height: r.height,
      });
    });
    return {
      rects: normalised,
      worldWidth: bounds.width,
      worldHeight: bounds.height,
      edgeGeometries: computeEdgeGeometry(edges, normalised),
    };
  }, [nodes, edges]);

  const nodeById = useMemo(() => {
    const m = new Map<string, AttackPathFlowNode>();
    nodes.forEach(n => m.set(n.id, n));
    return m;
  }, [nodes]);

  const clampZoom = useCallback((z: number) => clamp(z, MIN_ZOOM, MAX_ZOOM), []);

  // requestAnimationFrame tween to a target camera (used by focus/fit); a ref cancels an in-flight
  // animation when a new one starts or the user grabs the canvas.
  const animRef = useRef<number | null>(null);
  const stopAnim = useCallback(() => {
    if (animRef.current !== null) {
      cancelAnimationFrame(animRef.current);
      animRef.current = null;
    }
  }, []);
  const animateTo = useCallback((target: Camera, duration = 480) => {
    stopAnim();
    const start = performance.now();
    setCamera((from) => {
      const step = (now: number) => {
        const p = Math.min(1, (now - start) / duration);
        const e = 1 - (1 - p) ** 3; // ease-out cubic
        setCamera({
          zoom: from.zoom + (target.zoom - from.zoom) * e,
          x: from.x + (target.x - from.x) * e,
          y: from.y + (target.y - from.y) * e,
        });
        if (p < 1) {
          animRef.current = requestAnimationFrame(step);
        } else {
          animRef.current = null;
        }
      };
      animRef.current = requestAnimationFrame(step);
      return from;
    });
  }, [stopAnim]);
  useEffect(() => stopAnim, [stopAnim]);

  // Camera that frames the content: full fit when it stays readable, otherwise the readable
  // minimum zoom anchored on the content's start (the chain begins on the left).
  const fitCamera = useCallback((w: number, h: number): Camera => {
    const availW = w - FIT_PADDING * 2;
    const availH = h - FIT_PADDING * 2;
    if (availW <= 0 || availH <= 0) {
      return {
        zoom: 1,
        x: 0,
        y: 0,
      };
    }
    const raw = Math.min(availW / worldWidth, availH / worldHeight);
    const zoom = clampZoom(Math.min(INITIAL_MAX_ZOOM, Math.max(INITIAL_MIN_ZOOM, raw)));
    return {
      zoom,
      // Wider than the viewport at this zoom: anchor on the left edge so the first actions show.
      x: worldWidth * zoom > w ? FIT_PADDING : (w - worldWidth * zoom) / 2,
      y: worldHeight * zoom > h ? FIT_PADDING : (h - worldHeight * zoom) / 2,
    };
  }, [worldWidth, worldHeight, clampZoom]);

  // Fit once the container has a real size, and re-fit when the graph structure changes size. A ref
  // keeps the last fitted world signature so pan/zoom is preserved across unrelated re-renders.
  // Under pursuit the growth-driven re-fit is suppressed (the signature is still consumed): the
  // camera follows the newest nodes instead of re-framing the whole graph on every live delta.
  const pursuitActiveRef = useRef(pursuitActive);
  pursuitActiveRef.current = pursuitActive;
  const fittedSig = useRef<string>('');
  const worldSig = `${Math.round(worldWidth)}x${Math.round(worldHeight)}`;
  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return undefined;
    }
    const measure = () => {
      const w = el.clientWidth;
      const h = el.clientHeight;
      setSize({
        w,
        h,
      });
      if (w > 0 && h > 0 && fittedSig.current !== worldSig) {
        const firstFit = fittedSig.current === '';
        fittedSig.current = worldSig;
        if (firstFit || !pursuitActiveRef.current) {
          setCamera(fitCamera(w, h));
        }
      }
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [worldSig, fitCamera]);

  // Timestamp of the last MANUAL camera interaction (drag-pan, wheel/button zoom, fit click,
  // minimap jump). Live pursuit backs off while it is fresh, so the user keeps control.
  const lastManualAt = useRef(0);
  const markManual = useCallback(() => {
    lastManualAt.current = performance.now();
  }, []);

  // Ctrl/Cmd + wheel zoom around the cursor (native listener so we can preventDefault).
  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return undefined;
    }
    const onWheel = (e: WheelEvent) => {
      if (!e.ctrlKey && !e.metaKey) {
        return;
      }
      e.preventDefault();
      stopAnim();
      markManual();
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      const factor = e.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
      setCamera((prev) => {
        const next = clampZoom(prev.zoom * factor);
        return {
          zoom: next,
          x: cx - ((cx - prev.x) / prev.zoom) * next,
          y: cy - ((cy - prev.y) / prev.zoom) * next,
        };
      });
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [clampZoom, stopAnim, markManual]);

  const zoomByButton = useCallback((factor: number) => {
    stopAnim();
    markManual();
    const el = containerRef.current;
    if (!el) {
      return;
    }
    const cx = el.clientWidth / 2;
    const cy = el.clientHeight / 2;
    setCamera((prev) => {
      const next = clampZoom(prev.zoom * factor);
      return {
        zoom: next,
        x: cx - ((cx - prev.x) / prev.zoom) * next,
        y: cy - ((cy - prev.y) / prev.zoom) * next,
      };
    });
  }, [clampZoom, stopAnim, markManual]);

  const fitAll = useCallback(() => {
    const el = containerRef.current;
    if (el) {
      animateTo(fitCamera(el.clientWidth, el.clientHeight));
    }
  }, [animateTo, fitCamera]);

  // Center the camera on one node at a comfortable zoom (click-to-focus / cross-focus).
  const centerOnNode = useCallback((nodeId: string) => {
    const el = containerRef.current;
    const r = rects.get(nodeId);
    if (!el || !r) {
      return;
    }
    const zoom = clampZoom(Math.max(0.8, Math.min(1.4, camera.zoom)));
    const worldCx = r.x + r.width / 2;
    const worldCy = r.y + r.height / 2;
    animateTo({
      zoom,
      x: el.clientWidth / 2 - worldCx * zoom,
      y: el.clientHeight / 2 - worldCy * zoom,
    });
  }, [rects, camera.zoom, clampZoom, animateTo]);

  // Re-fires on nonce only (repeat focus on the same node re-centers without re-running on every
  // camera change, hence the ref-carried callback).
  const centerOnNodeRef = useRef(centerOnNode);
  centerOnNodeRef.current = centerOnNode;
  useEffect(() => {
    if (focusRequest?.nodeId) {
      centerOnNodeRef.current(focusRequest.nodeId);
    }
  }, [focusRequest?.nodeId, focusRequest?.nonce]);

  const fitAllRef = useRef(fitAll);
  fitAllRef.current = fitAll;
  useEffect(() => {
    if (fitRequest) {
      // Let the new nodes lay out before framing them.
      const id = window.setTimeout(() => fitAllRef.current(), 60);
      return () => window.clearTimeout(id);
    }
    return undefined;
  }, [fitRequest]);

  // Live pursuit: pan to center the bounding box of the delta's new nodes, keeping the CURRENT zoom
  // (that is the whole point — the run stops "unzooming" and the camera chases the action instead).
  // Backs off while a manual interaction is fresh, so a user inspecting the graph — or holding the
  // full overview after a fit — is not fought over the camera.
  const pursueNodes = useCallback((nodeIds: readonly string[]) => {
    const el = containerRef.current;
    if (!el || performance.now() - lastManualAt.current < PURSUIT_MANUAL_PAUSE_MS) {
      return;
    }
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    nodeIds.forEach((id) => {
      const r = rects.get(id);
      if (!r) {
        return;
      }
      minX = Math.min(minX, r.x);
      minY = Math.min(minY, r.y);
      maxX = Math.max(maxX, r.x + r.width);
      maxY = Math.max(maxY, r.y + r.height);
    });
    // None of the delta's nodes exist in this view (e.g. clustered away): leave the camera alone.
    if (minX === Infinity) {
      return;
    }
    const zoom = camera.zoom;
    animateTo({
      zoom,
      x: el.clientWidth / 2 - ((minX + maxX) / 2) * zoom,
      y: el.clientHeight / 2 - ((minY + maxY) / 2) * zoom,
    });
  }, [rects, camera.zoom, animateTo]);
  const pursueNodesRef = useRef(pursueNodes);
  pursueNodesRef.current = pursueNodes;
  useEffect(() => {
    if (pursuitRequest && pursuitRequest.nodeIds.length > 0) {
      // Let the delta's new cards lay out before chasing them.
      const ids = pursuitRequest.nodeIds;
      const id = window.setTimeout(() => pursueNodesRef.current(ids), 60);
      return () => window.clearTimeout(id);
    }
    return undefined;
    // Keyed on the nonce alone: nodeIds travel with it, and the ref keeps pursueNodes fresh.
  }, [pursuitRequest?.nonce]);

  // Drag-to-pan on the empty canvas: a move past the threshold pans; a click without movement
  // clears the selection (background click). Cards stop propagation of their own pointerdown.
  const drag = useRef<{
    startX: number;
    startY: number;
    camX: number;
    camY: number;
    moved: boolean;
    pointerId: number;
  } | null>(null);
  const handlePointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) {
      return;
    }
    stopAnim();
    drag.current = {
      startX: e.clientX,
      startY: e.clientY,
      camX: camera.x,
      camY: camera.y,
      moved: false,
      pointerId: e.pointerId,
    };
  };
  const handlePointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const state = drag.current;
    if (!state) {
      return;
    }
    const dx = e.clientX - state.startX;
    const dy = e.clientY - state.startY;
    if (!state.moved && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
      state.moved = true;
      setPanning(true);
      markManual();
      containerRef.current?.setPointerCapture(state.pointerId);
    }
    if (state.moved) {
      markManual();
      setCamera(prev => ({
        ...prev,
        x: state.camX + dx,
        y: state.camY + dy,
      }));
    }
  };
  const handlePointerUp = () => {
    const state = drag.current;
    drag.current = null;
    if (!state) {
      return;
    }
    if (state.moved) {
      containerRef.current?.releasePointerCapture?.(state.pointerId);
      setPanning(false);
    } else {
      onBackgroundClick?.();
    }
  };

  const dispatchNodeClick = useCallback((node: AttackPathFlowNode) => {
    const data = node.data;
    if (node.type === AP_FLOW_NODE_TYPE.asset) {
      onEndpointClick?.(node.id, data.ref, data.label);
    } else if (node.type === AP_FLOW_NODE_TYPE.endpointCluster && data.injectorId) {
      onClusterClick?.(data.injectorId, data.clusterKind === 'overflow' ? 'overflow' : 'header');
    } else if (node.type === AP_FLOW_NODE_TYPE.endpointCluster && data.clusterId) {
      onEndpointClusterClick?.(data.clusterId);
    } else if (node.type === AP_FLOW_NODE_TYPE.findingCluster && data.clusterId) {
      const findingClusterKind = data.clusterKind === 'overflow' || data.clusterKind === 'typeOverflow'
        ? data.clusterKind
        : 'header';
      onFindingClusterClick?.(data.clusterId, data.typeFindings, data.injectorId, data.endpointRef, findingClusterKind);
    } else if (node.type === AP_FLOW_NODE_TYPE.finding) {
      onFindingSelect?.(node.id, data.typeFindings, data.label, data.assetNodeId);
    } else if (node.type === AP_FLOW_NODE_TYPE.injector) {
      onInjectorSelect?.(node.id, data.label);
    }
  }, [onEndpointClick, onClusterClick, onEndpointClusterClick, onFindingClusterClick, onFindingSelect, onInjectorSelect]);

  // Cull off-screen cards: with hundreds of nodes only mount those whose screen rect intersects the
  // viewport (expanded by a margin so cards just outside slide in without a pop).
  const visibleNodes = useMemo(() => {
    if (size.w === 0) {
      return nodes;
    }
    const left = -CULL_MARGIN;
    const top = -CULL_MARGIN;
    const right = size.w + CULL_MARGIN;
    const bottom = size.h + CULL_MARGIN;
    return nodes.filter((n) => {
      const r = rects.get(n.id);
      if (!r) {
        return false;
      }
      const sx = r.x * camera.zoom + camera.x;
      const sy = r.y * camera.zoom + camera.y;
      const sw = r.width * camera.zoom;
      const sh = r.height * camera.zoom;
      return sx + sw >= left && sx <= right && sy + sh >= top && sy <= bottom;
    });
  }, [nodes, rects, camera, size]);

  const controlButtonSx = {
    'padding': 0.75,
    'color': theme.palette.primary.main,
    'borderRadius': 0,
    'borderBottom': `1px solid ${theme.palette.divider}`,
    '&:last-of-type': { borderBottom: 'none' },
    '&:hover': { backgroundColor: theme.palette.action.hover },
  };

  const viewport: CanvasRect = {
    x: -camera.x / camera.zoom,
    y: -camera.y / camera.zoom,
    width: size.w / camera.zoom,
    height: size.h / camera.zoom,
  };

  const renderCard = (node: AttackPathFlowNode) => {
    const data = node.data as AttackPathFlowNodeData;
    const selected = node.selected ?? false;
    switch (node.type) {
      case AP_FLOW_NODE_TYPE.asset:
        return <TargetCard data={data} selected={selected} />;
      case AP_FLOW_NODE_TYPE.injector:
        return <ActionCard data={data} selected={selected} />;
      case AP_FLOW_NODE_TYPE.finding:
        return <FindingCard data={data} selected={selected} />;
      case AP_FLOW_NODE_TYPE.findingType:
        return <FindingTypeCard data={data} selected={selected} />;
      case AP_FLOW_NODE_TYPE.endpointCluster:
        return <EndpointClusterCard data={data} selected={selected} />;
      case AP_FLOW_NODE_TYPE.findingCluster:
        return <FindingClusterCard data={data} selected={selected} />;
      default:
        return null;
    }
  };

  return (
    <Box
      ref={containerRef}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerUp}
      sx={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        cursor: panning ? 'grabbing' : 'grab',
        touchAction: 'none',
        backgroundColor: theme.palette.background.default,
        // Faint dot grid in screen space (the Logic canvas' paper texture, same metrics).
        backgroundImage: `radial-gradient(${theme.palette.divider} 1px, transparent 1px)`,
        backgroundSize: '28px 28px',
        backgroundPosition: '-1px -1px',
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: worldWidth,
          height: worldHeight,
          transform: `translate(${camera.x}px, ${camera.y}px) scale(${camera.zoom})`,
          transformOrigin: '0 0',
        }}
      >
        <AttackPathConnectors geometries={edgeGeometries} width={worldWidth} height={worldHeight} />
        {visibleNodes.map((node) => {
          const r = rects.get(node.id);
          if (!r) {
            return null;
          }
          const isEntering = enterNodeIds?.has(node.id) ?? false;
          return (
            <Box
              key={node.id}
              className={isEntering ? AP_NODE_ENTER_CLASS : undefined}
              onPointerDown={e => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                const n = nodeById.get(node.id);
                if (n) {
                  dispatchNodeClick(n);
                }
              }}
              sx={{
                position: 'absolute',
                left: r.x,
                top: r.y,
                width: r.width,
                height: r.height,
              }}
            >
              {renderCard(node)}
            </Box>
          );
        })}
      </Box>

      <Box
        onPointerDown={e => e.stopPropagation()}
        sx={{
          position: 'absolute',
          bottom: theme.spacing(2),
          left: theme.spacing(2),
          display: 'flex',
          flexDirection: 'column',
          borderRadius: 1,
          overflow: 'hidden',
          border: `1px solid ${theme.palette.divider}`,
          backgroundColor: theme.palette.background.paper,
          boxShadow: theme.shadows[3],
        }}
      >
        <Tooltip title={t('Zoom in')} placement="right" slotProps={graphTooltipSlotProps}>
          <IconButton size="small" aria-label={t('Zoom in')} sx={controlButtonSx} onClick={() => zoomByButton(ZOOM_STEP)}>
            <AddOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Zoom out')} placement="right" slotProps={graphTooltipSlotProps}>
          <IconButton size="small" aria-label={t('Zoom out')} sx={controlButtonSx} onClick={() => zoomByButton(1 / ZOOM_STEP)}>
            <RemoveOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Fit to view')} placement="right" slotProps={graphTooltipSlotProps}>
          <IconButton
            size="small"
            aria-label={t('Fit to view')}
            sx={controlButtonSx}
            onClick={() => {
              // A user asking for the big picture holds it: pursuit backs off for the manual pause.
              markManual();
              fitAll();
            }}
          >
            <CenterFocusStrongOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Bottom-right overlay stack: overview map on top, then the legend. */}
      <Box
        onPointerDown={e => e.stopPropagation()}
        sx={{
          position: 'absolute',
          bottom: theme.spacing(2),
          right: theme.spacing(2),
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-end',
          gap: 1,
        }}
      >
        {showMiniMap && size.w > 0 && (
          <AttackPathMiniMap
            rects={[...rects.values()]}
            worldWidth={worldWidth}
            worldHeight={worldHeight}
            viewport={viewport}
            onNavigate={(wx, wy) => {
              const el = containerRef.current;
              if (!el) {
                return;
              }
              markManual();
              animateTo({
                zoom: camera.zoom,
                x: el.clientWidth / 2 - wx * camera.zoom,
                y: el.clientHeight / 2 - wy * camera.zoom,
              });
            }}
          />
        )}
        {legend}
      </Box>
    </Box>
  );
};

export default AttackPathCanvas;
