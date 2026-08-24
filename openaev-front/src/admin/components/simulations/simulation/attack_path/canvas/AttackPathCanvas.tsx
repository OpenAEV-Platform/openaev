import { AddOutlined, CenterFocusStrongOutlined, RemoveOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { toBlob } from 'html-to-image';
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

/** Keep the view on one node across a layout change the user caused (expanding a cluster). */
export interface AttackPathAnchorRequest {
  nodeId: string;
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
  /**
   * Expanding a cluster: hold the view on the node that was clicked instead of re-fitting the whole
   * graph. The growth-driven fit below cannot tell a user expansion from the graph changing shape on
   * its own, and re-framing everything threw the user back to the graph's entrance. Pans at the
   * CURRENT zoom, because the surrounding layout reflows around the newly revealed nodes, so holding
   * the camera still would not hold the clicked node still.
   */
  anchorRequest?: AttackPathAnchorRequest | null;
  showMiniMap?: boolean;
  /** Overlay rendered in the bottom-right stack, under the minimap (the graph legend). */
  legend?: ReactNode;
  /**
   * Capture the WHOLE graph as a PNG; nonce re-fires on repeat. The capture is driven from here
   * rather than from the parent because only the canvas knows the world geometry and can lift the
   * off-screen culling that keeps the DOM small — culled cards would be missing from the image.
   */
  exportRequest?: number;
  /** Result of an {@link AttackPathCanvasProps#exportRequest}: null when the capture failed. */
  onExportDone?: (png: Blob | null) => void;
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
// Margin kept around the graph in an exported PNG, so no card touches the image edge.
const EXPORT_PADDING = 48;
// Browsers refuse canvases beyond ~16k on a side or ~2^28 pixels, and answer with a blank image
// rather than an error: a huge graph is captured at a lower density instead.
const MAX_EXPORT_SIDE = 8192;
const MAX_EXPORT_PIXELS = 32_000_000;
const exportPixelRatio = (width: number, height: number) => Math.min(
  2,
  window.devicePixelRatio || 1,
  MAX_EXPORT_SIDE / width,
  MAX_EXPORT_SIDE / height,
  Math.sqrt(MAX_EXPORT_PIXELS / (width * height)),
);
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
  anchorRequest,
  showMiniMap = true,
  legend,
  exportRequest,
  onExportDone,
}: AttackPathCanvasProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const containerRef = useRef<HTMLDivElement>(null);
  const worldRef = useRef<HTMLDivElement>(null);
  // While true the whole world is mounted (no culling) and entrance animations are off, so a PNG
  // capture sees a complete, settled graph.
  const [exporting, setExporting] = useState(false);
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
  const { rects, worldWidth, worldHeight } = useMemo(() => {
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
    };
  }, [nodes]);

  // Manual drag offsets, keyed by node id (world units, applied on top of the auto-layout rect).
  // Purely a rendering-time adjustment: the layout/compaction pass above never sees it, so a
  // dragged node still snaps back onto its column/row once the auto layout itself changes (a new
  // node arrives, the graph is re-fit, etc.) — the user is nudging the same computed layout, not
  // replacing it.
  const [dragOffsets, setDragOffsets] = useState<Map<string, {
    dx: number;
    dy: number;
  }>>(new Map());

  const effectiveRects = useMemo(() => {
    if (dragOffsets.size === 0) {
      return rects;
    }
    const merged = new Map(rects);
    dragOffsets.forEach((off, id) => {
      const r = rects.get(id);
      if (r) {
        merged.set(id, {
          ...r,
          x: r.x + off.dx,
          y: r.y + off.dy,
        });
      }
    });
    return merged;
  }, [rects, dragOffsets]);

  const edgeGeometries = useMemo(
    () => computeEdgeGeometry(edges, effectiveRects),
    [edges, effectiveRects],
  );

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
  // Set while an anchored expansion is in flight: the world change it causes is adopted WITHOUT a
  // re-fit, and the camera is then panned onto the anchor instead (see the anchorRequest effect).
  const holdForAnchorRef = useRef(false);
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
        if (firstFit || (!pursuitActiveRef.current && !holdForAnchorRef.current)) {
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
  const markManualRef = useRef(markManual);
  markManualRef.current = markManual;

  // Mouse-wheel / trackpad zoom around the cursor (native, non-passive listener so we can
  // preventDefault). Plain scroll zooms - no modifier required - and Ctrl/Cmd still works. The delta
  // is normalized across wheel modes (pixel / line / page) and mapped through exp() so a notched
  // mouse and a fine-grained trackpad both zoom by a sensible, consistent amount.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return undefined;
    }
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      stopAnim();
      markManual();
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left;
      const cy = e.clientY - rect.top;
      let unit = 1;
      if (e.deltaMode === 1) unit = 16;
      else if (e.deltaMode === 2) unit = el.clientHeight;
      const factor = Math.exp(-e.deltaY * unit * 0.0015);
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

  /**
   * Frame the highlighted path, anchored on the node that was just selected.
   *
   * <p>The zoom tries to contain the whole highlighted path (the nodes still lit, i.e. not dimmed)
   * but never drops below the readable floor used by the initial fit, and the camera centres on the
   * anchor rather than on the box centre — so the clicked finding is always the visual subject even
   * when its chain is too long to fit and overflows.
   *
   * <p>Centring alone (the previous {@link centerOnNode}) kept the current zoom, which is what made
   * a selection unreadable: zoomed out the finding was centred but tiny, zoomed in its connectors
   * stretched off-screen.
   */
  const focusOnPath = useCallback((anchorNodeId: string) => {
    const el = containerRef.current;
    const anchor = effectiveRects.get(anchorNodeId);
    if (!el || !anchor) {
      return;
    }
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    nodes.forEach((n) => {
      if (n.data?.dimmed) {
        return;
      }
      const r = effectiveRects.get(n.id);
      if (!r) {
        return;
      }
      minX = Math.min(minX, r.x);
      minY = Math.min(minY, r.y);
      maxX = Math.max(maxX, r.x + r.width);
      maxY = Math.max(maxY, r.y + r.height);
    });
    // Nothing lit (or no rects yet): fall back to framing the anchor alone.
    if (minX === Infinity) {
      minX = anchor.x;
      minY = anchor.y;
      maxX = anchor.x + anchor.width;
      maxY = anchor.y + anchor.height;
    }
    const availW = el.clientWidth - FIT_PADDING * 2;
    const availH = el.clientHeight - FIT_PADDING * 2;
    if (availW <= 0 || availH <= 0) {
      return;
    }
    const raw = Math.min(availW / Math.max(maxX - minX, 1), availH / Math.max(maxY - minY, 1));
    const zoom = clampZoom(Math.min(INITIAL_MAX_ZOOM, Math.max(INITIAL_MIN_ZOOM, raw)));
    animateTo({
      zoom,
      x: el.clientWidth / 2 - (anchor.x + anchor.width / 2) * zoom,
      y: el.clientHeight / 2 - (anchor.y + anchor.height / 2) * zoom,
    });
  }, [nodes, effectiveRects, clampZoom, animateTo]);

  // Re-fires on nonce only (repeat focus on the same node re-frames without re-running on every
  // camera change, hence the ref-carried callback).
  const focusOnPathRef = useRef(focusOnPath);
  focusOnPathRef.current = focusOnPath;
  useEffect(() => {
    if (focusRequest?.nodeId) {
      // Let the focused layout settle before measuring it.
      const id = window.setTimeout(() => focusOnPathRef.current(focusRequest.nodeId), 80);
      return () => window.clearTimeout(id);
    }
    return undefined;
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

  // Pan to center a set of nodes, keeping the CURRENT zoom. Unconditional: the caller decides whether
  // the camera may move (live pursuit backs off, a user expansion does not).
  const centerOnNodes = useCallback((nodeIds: readonly string[]) => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    nodeIds.forEach((id) => {
      const r = effectiveRects.get(id);
      if (!r) {
        return;
      }
      minX = Math.min(minX, r.x);
      minY = Math.min(minY, r.y);
      maxX = Math.max(maxX, r.x + r.width);
      maxY = Math.max(maxY, r.y + r.height);
    });
    // None of the nodes exist in this view (e.g. clustered away): leave the camera alone.
    if (minX === Infinity) {
      return;
    }
    const zoom = camera.zoom;
    animateTo({
      zoom,
      x: el.clientWidth / 2 - ((minX + maxX) / 2) * zoom,
      y: el.clientHeight / 2 - ((minY + maxY) / 2) * zoom,
    });
  }, [effectiveRects, camera.zoom, animateTo]);

  // Live pursuit: chase the nodes the latest delta introduced (that is the whole point — the run stops
  // "unzooming" and the camera follows the action instead). Backs off while a manual interaction is
  // fresh, so a user inspecting the graph — or holding the full overview after a fit — is not fought
  // over the camera.
  const pursueNodes = useCallback((nodeIds: readonly string[]) => {
    if (performance.now() - lastManualAt.current < PURSUIT_MANUAL_PAUSE_MS) {
      return;
    }
    centerOnNodes(nodeIds);
  }, [centerOnNodes]);
  const pursueNodesRef = useRef(pursueNodes);
  pursueNodesRef.current = pursueNodes;
  const centerOnNodesRef = useRef(centerOnNodes);
  centerOnNodesRef.current = centerOnNodes;
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
  // Anchored expansion: raise the hold BEFORE the expanded nodes land (this effect runs on the same
  // commit as the click, the bigger world arrives on the next one), then re-center the anchor at the
  // current zoom once the layout has settled, and drop the hold. Uses centerOnNodes, not pursueNodes:
  // the user just asked for this, so it is not subject to pursuit's manual-interaction back-off. It
  // counts as a manual move itself, so a live delta does not immediately yank the camera off it.
  useEffect(() => {
    if (!anchorRequest?.nodeId) {
      return undefined;
    }
    holdForAnchorRef.current = true;
    const nodeId = anchorRequest.nodeId;
    const id = window.setTimeout(() => {
      centerOnNodesRef.current([nodeId]);
      markManualRef.current();
      holdForAnchorRef.current = false;
    }, 80);
    return () => {
      window.clearTimeout(id);
      holdForAnchorRef.current = false;
    };
  }, [anchorRequest?.nodeId, anchorRequest?.nonce]);

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

  // Per-card drag, mirroring the canvas-level pan: a pointer-down on a card starts tracking, a move
  // past DRAG_THRESHOLD commits to a drag (captures the pointer, records the offset in world units
  // so it stays correct at any zoom), and a pointer-up either drops the card (drag) or is treated as
  // a plain click on the card (no movement) — a genuine drag never also fires the click callback.
  const nodeDrag = useRef<{
    id: string;
    startX: number;
    startY: number;
    baseDx: number;
    baseDy: number;
    moved: boolean;
    pointerId: number;
  } | null>(null);
  const justDraggedIds = useRef<Set<string>>(new Set());

  const handleCardPointerDown = useCallback((e: ReactPointerEvent<HTMLDivElement>, nodeId: string) => {
    if (e.button !== 0) {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    const current = dragOffsets.get(nodeId);
    nodeDrag.current = {
      id: nodeId,
      startX: e.clientX,
      startY: e.clientY,
      baseDx: current?.dx ?? 0,
      baseDy: current?.dy ?? 0,
      moved: false,
      pointerId: e.pointerId,
    };
  }, [dragOffsets]);

  const handleCardPointerMove = useCallback((e: ReactPointerEvent<HTMLDivElement>) => {
    const state = nodeDrag.current;
    if (!state) {
      return;
    }
    const dx = e.clientX - state.startX;
    const dy = e.clientY - state.startY;
    if (!state.moved && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
      state.moved = true;
      stopAnim();
      markManual();
    }
    if (state.moved) {
      const worldDx = state.baseDx + dx / camera.zoom;
      const worldDy = state.baseDy + dy / camera.zoom;
      setDragOffsets((prev) => {
        const next = new Map(prev);
        next.set(state.id, {
          dx: worldDx,
          dy: worldDy,
        });
        return next;
      });
    }
  }, [camera.zoom, stopAnim, markManual]);

  const handleCardPointerUp = useCallback((e: ReactPointerEvent<HTMLDivElement>) => {
    const state = nodeDrag.current;
    nodeDrag.current = null;
    if (!state) {
      return;
    }
    if (state.moved) {
      e.currentTarget.releasePointerCapture?.(state.pointerId);
      justDraggedIds.current.add(state.id);
    } else {
      e.currentTarget.releasePointerCapture?.(state.pointerId);
    }
  }, []);

  const handleCardPointerCancel = useCallback((e: ReactPointerEvent<HTMLDivElement>) => {
    const state = nodeDrag.current;
    nodeDrag.current = null;
    if (!state) {
      return;
    }
    e.currentTarget.releasePointerCapture?.(state.pointerId);
  }, []);

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
    // A PNG export captures the DOM: everything must be mounted, culled or not.
    if (size.w === 0 || exporting) {
      return nodes;
    }
    const left = -CULL_MARGIN;
    const top = -CULL_MARGIN;
    const right = size.w + CULL_MARGIN;
    const bottom = size.h + CULL_MARGIN;
    return nodes.filter((n) => {
      const r = effectiveRects.get(n.id);
      if (!r) {
        return false;
      }
      const sx = r.x * camera.zoom + camera.x;
      const sy = r.y * camera.zoom + camera.y;
      const sw = r.width * camera.zoom;
      const sh = r.height * camera.zoom;
      return sx + sw >= left && sx <= right && sy + sh >= top && sy <= bottom;
    });
  }, [nodes, effectiveRects, camera, size, exporting]);

  // Frame of an exported PNG: the auto-layout world, widened to also contain cards the user dragged
  // outside of it, plus a margin — so nothing is cropped whatever the current pan/zoom is.
  const exportFrame = useCallback(() => {
    let minX = 0;
    let minY = 0;
    let maxX = worldWidth;
    let maxY = worldHeight;
    effectiveRects.forEach((r) => {
      minX = Math.min(minX, r.x);
      minY = Math.min(minY, r.y);
      maxX = Math.max(maxX, r.x + r.width);
      maxY = Math.max(maxY, r.y + r.height);
    });
    return {
      width: maxX - minX + EXPORT_PADDING * 2,
      height: maxY - minY + EXPORT_PADDING * 2,
      offsetX: EXPORT_PADDING - minX,
      offsetY: EXPORT_PADDING - minY,
    };
  }, [effectiveRects, worldWidth, worldHeight]);
  const exportFrameRef = useRef(exportFrame);
  exportFrameRef.current = exportFrame;
  const onExportDoneRef = useRef(onExportDone);
  onExportDoneRef.current = onExportDone;

  // An export first switches the canvas into export mode (see `exporting`), because the culled
  // cards have to be mounted before the DOM can be captured. The nonce is tracked so a REMOUNT (a
  // trip through the table view, say) does not replay the last capture.
  const handledExportRef = useRef(exportRequest ?? 0);
  useEffect(() => {
    if (exportRequest && exportRequest !== handledExportRef.current) {
      handledExportRef.current = exportRequest;
      setExporting(true);
    }
  }, [exportRequest]);

  // Effects run after the commit, so by now every card is in the DOM; one frame then lets the
  // browser lay them out before their computed styles are read.
  useEffect(() => {
    if (!exporting) {
      return undefined;
    }
    let settled = false;
    const done = (png: Blob | null) => {
      if (settled) {
        return;
      }
      settled = true;
      onExportDoneRef.current?.(png);
      setExporting(false);
    };
    const frame = requestAnimationFrame(() => {
      const world = worldRef.current;
      if (!world) {
        done(null);
        return;
      }
      const { width, height, offsetX, offsetY } = exportFrameRef.current();
      toBlob(world, {
        width,
        height,
        pixelRatio: exportPixelRatio(width, height),
        backgroundColor: theme.palette.background.default,
        // Applied to the clone only: it replaces the live camera transform, so the capture frames
        // the whole world whatever the user's current pan/zoom is.
        style: {
          transform: `translate(${offsetX}px, ${offsetY}px)`,
          transformOrigin: '0 0',
        },
      })
        .then(png => done(png))
        .catch(() => done(null));
    });
    return () => {
      cancelAnimationFrame(frame);
      // Torn down before the capture produced anything: answer, so the caller is not left waiting
      // on a blob that will never come. A capture that already answered ignores this.
      done(null);
    };
  }, [exporting, theme.palette.background.default]);

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
      // A pan is a pointer gesture, but the browser will happily reinterpret a press-and-drag that
      // begins on an inner <img> (an endpoint/finding/injector glyph or its broken-image
      // placeholder) or a run of selectable text as a native HTML5 drag / text selection. That
      // hijack stops pointermove firing mid-gesture, so the pan silently dies and the cursor turns
      // into the "no-drop" (forbidden) sign. dragstart bubbles, so cancelling it once here disarms
      // every draggable descendant at once (same fix as the Logic canvas' PanZoom).
      onDragStart={e => e.preventDefault()}
      sx={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        cursor: panning ? 'grabbing' : 'grab',
        touchAction: 'none',
        userSelect: 'none',
        WebkitUserSelect: 'none',
        backgroundColor: theme.palette.background.default,
        // Faint dot grid in screen space (the Logic canvas' paper texture, same metrics).
        backgroundImage: `radial-gradient(${theme.palette.divider} 1px, transparent 1px)`,
        backgroundSize: '28px 28px',
        backgroundPosition: '-1px -1px',
      }}
    >
      <Box
        ref={worldRef}
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
          const r = effectiveRects.get(node.id);
          if (!r) {
            return null;
          }
          // A card mid-entrance is half faded: freeze the animation off while capturing a PNG.
          const isEntering = !exporting && (enterNodeIds?.has(node.id) ?? false);
          return (
            <Box
              key={node.id}
              className={isEntering ? AP_NODE_ENTER_CLASS : undefined}
              onPointerDown={e => handleCardPointerDown(e, node.id)}
              onPointerMove={handleCardPointerMove}
              onPointerUp={handleCardPointerUp}
              onPointerCancel={handleCardPointerCancel}
              onClick={(e) => {
                e.stopPropagation();
                if (justDraggedIds.current.has(node.id)) {
                  justDraggedIds.current.delete(node.id);
                  return;
                }
                const n = nodeById.get(node.id);
                if (n) {
                  dispatchNodeClick(n);
                }
              }}
              sx={{
                'position': 'absolute',
                'left': r.x,
                'top': r.y,
                'width': r.width,
                'height': r.height,
                'cursor': 'grab',
                '&:active': { cursor: 'grabbing' },
                'touchAction': 'none',
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
            rects={[...effectiveRects.values()]}
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
