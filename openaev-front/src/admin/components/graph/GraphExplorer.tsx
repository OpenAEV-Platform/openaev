import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogTitle,
  IconButton,
  Menu,
  MenuContent,
  MenuItem,
  MenuLabel,
  MenuSeparator,
  MenuSub,
  MenuSubContent,
  MenuSubTrigger,
  MenuTrigger,
  Paper,
  SearchField,
  Slider,
  Spinner,
  Switch,
  Text,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
import {
  AccountTreeOutlined,
  ArrowDropDownOutlined,
  AutorenewOutlined,
  BubbleChartOutlined,
  CenterFocusStrongOutlined,
  CloseOutlined,
  DeleteOutlined,
  DonutLargeOutlined,
  DownloadOutlined,
  ExpandLessOutlined,
  ExpandMoreOutlined,
  FitScreenOutlined,
  GestureOutlined,
  KeyboardOutlined,
  LinkOutlined,
  OpenInFullOutlined,
  OpenInNewOutlined,
  PushPinOutlined,
  RouteOutlined,
  SelectAllOutlined,
  UndoOutlined,
  VisibilityOffOutlined,
  VisibilityOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@mui/icons-material';
import { Box, Drawer, List, ListItemButton, ListItemText, Stack } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
  Background,
  type Edge,
  getNodesBounds,
  getViewportForBounds,
  MarkerType,
  MiniMap,
  type NodeChange,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
} from '@xyflow/react';
import { toPng } from 'html-to-image';
import { type KeyboardEvent, type MouseEvent, type PointerEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { fetchGraphNeighbourhood, fetchGraphPath } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import { resolveCounterparts } from '../../../components/xtm/counterpartStore';
import { OPENCTI_PRODUCT } from '../../../components/xtm/useCounterpart';
import useCounterpartMatches, { type CounterpartRef, counterpartRefId } from '../../../components/xtm/useCounterpartMatches';
import OpenCrqPivot, { OPENCRQ_PRODUCT, OpenCrqMark } from '../../../components/xtm/XtmOpenCrq';
import OpenCtiPivot, { ProductMark } from '../../../components/xtm/XtmPivot';
import { toHttpUrl } from '../../../utils/url-helper';
import { NODE_LABEL_OFFSET, NODE_WIDTH } from './graph-edge-geometry';
import { type GroupedNode, groupNeighbours, isGroupId, miniView } from './graph-grouping';
import NODE_ICON from './graph-icons';
import { LASSO_MIN_LENGTH, nodesInLasso, pathLength } from './graph-lasso';
import { entering, type GraphLayout, MINI_CARD_PADDING, miniCardHeight, moveAt, planMove, type Point } from './graph-layout';
import {
  ALL_EDGE_TYPES,
  ALL_NODE_TYPES,
  ALL_RESULTS,
  EDGE_TYPE_LABEL,
  EDGE_TYPES_BY_NODE_TYPE,
  type GraphEdgeType,
  graphHref,
  type GraphNodeOutput,
  type GraphNodeType,
  type GraphOutput,
  type GraphResult,
  NODE_TYPE_LABEL,
  NODE_TYPE_PLURAL,
  nodeCounterpart,
  nodeHref,
  RESULT_LABEL,
} from './graph-model';
import {
  DEFAULT_FILTERS,
  expandSlice,
  failedHighlights,
  firstRing,
  fromSlice,
  type GraphFilters,
  type GraphState,
  hideNode,
  hideNodes,
  mergeSlice,
  pathEdges,
  pinNodes,
  removeNodes,
  showHidden,
  togglePin,
  ungroup,
  visibleGraph,
} from './graph-state';
import { dateBounds, followBounds, TIME_RANGE_STEP, type TimeRange } from './graph-time-range';
import GraphEntityNode, { type GraphEntityNodeType } from './GraphEntityNode';
import GraphFloatingEdge from './GraphFloatingEdge';

/** The ids of the nodes whose counterpart key is among the matched ones of a product. */
const matchedNodeIds = (refs: Map<string, CounterpartRef>, found: Set<string>) => new Set(Array.from(refs.entries())
  .filter(([, ref]) => found.has(counterpartRefId(ref)))
  .map(([nodeId]) => nodeId));

const NODE_BOX_HEIGHT = NODE_LABEL_OFFSET + 20;
const PANEL_WIDTH = 340;
/** Below this many nodes the whole graph fits on screen: the mini-map would only hide part of it. */
const MINI_MAP_MIN_NODES = 40;
/** A static layout glides to its place in this time; the force layout plays its frames in about twice it. */
const MOVE_MS = 400;
const FRAME_MS = 24;
// Lowest zoom of the mini graph's fit: node labels are 10 px at zoom 1, so they never shrink below it.
const MINI_MIN_ZOOM = 1;
// Highest zoom of a fit on the page and the embedded graph, like OpenCTI's framing: a focus with one or
// two nodes stays at a readable size instead of filling the canvas.
const FIT_MAX_ZOOM = 1.25;

const nodeTypes = { entity: GraphEntityNode };
const edgeTypes = { floating: GraphFloatingEdge };

const proOptions = {
  account: 'paid-pro',
  hideAttribution: true,
};

/**
 * An action a host adds on a node of the graph (the detail panel lists them). The OpenCTI x OpenAEV
 * pivots (dev:octi-oaev-deep-integration) plug in here, without the explorer knowing them.
 */
export interface GraphNodeAction {
  key: string;
  label: string;
  icon?: ReactNode;
  appliesTo: (node: GraphNodeOutput) => boolean;
  onClick: (node: GraphNodeOutput) => void;
}

export interface GraphExplorerProps {
  /** The slice to start from: the landing view or a focused neighbourhood. */
  initial: GraphOutput;
  /** Embedded on an entity tab, or full page; compact hides the side tools. */
  variant?: 'page' | 'embedded' | 'mini';
  height?: number | string;
  nodeActions?: GraphNodeAction[];
  /** Called with the focus whenever the user changes it, for the shareable URL. */
  onFocusChange?: (focus: string | null) => void;
  /** Mini only: where a click on the card goes. */
  onOpen?: () => void;
  initialLayout?: GraphLayout;
  onLayoutChange?: (layout: GraphLayout) => void;
  /**
   * Page only: the fit never zooms out below this, so a landing story keeps its labels legible at
   * the first frame instead of shrinking to fit (#9447). The user still zooms out by hand.
   */
  fitMinZoom?: number;
  /**
   * Open the detail panel on the slice's focus node at the first frame (default). The landing story
   * passes false: its focus only anchors the layout, and an open panel would hide a third of the
   * story (#9447).
   */
  selectFocus?: boolean;
}

interface History { past: GraphState[] }

/** FDS chip severities of a selected result filter: their contrast pairs pass in both themes. */
const RESULT_CHIP_SEVERITY: Record<GraphResult, 'critical' | 'low' | 'medium'> = {
  FAILED: 'critical',
  SUCCESS: 'low',
  PENDING: 'medium',
};

const LAYOUT_ICON: Record<GraphLayout, ReactNode> = {
  force: <BubbleChartOutlined fontSize="small" />,
  hierarchical: <AccountTreeOutlined fontSize="small" />,
  radial: <DonutLargeOutlined fontSize="small" />,
};

/** What each layout button does, said in its tooltip (Sam, 19 Nov 2026: no button without one). */
const LAYOUT_LABEL: Record<GraphLayout, string> = {
  force: 'Force layout: nodes repel each other, links pull them together. Click again to re-run it.',
  hierarchical: 'Hierarchical layout: scenarios on top, findings at the bottom',
  radial: 'Radial layout: rings of neighbours around the selected node',
};

/** The lasso button's tooltip and its shortcut line (OpenCTI's LassoSelection). */
const LASSO_LABEL = 'Lasso selection: draw around nodes to select them';

/** The keyboard shortcuts of the page graph, listed by the shortcuts dialog (as in OpenCTI). */
const SHORTCUTS: [string, string][] = [
  ['E', 'Expand the selected node'],
  ['P', 'Pin or unpin the selected node'],
  ['H', 'Hide the selected node'],
  ['F', 'Fit to screen'],
  ['L', 'Re-run the layout'],
  ['N', 'Select the neighbours of the selection'],
  ['Delete', 'Remove the selection from the view'],
  ['Shift+drag', 'Select an area'],
  ['S', LASSO_LABEL],
  ['Ctrl+click', 'Add a node to the selection'],
  ['Esc', 'Clear the selection'],
  ['Ctrl+Z', 'Undo'],
  ['?', 'Show this list'],
];

/** Where the context menu opens (in the coordinates of the canvas) and what it was opened on. */
interface ContextMenuAnchor {
  x: number;
  y: number;
  nodeId?: string;
  edgeId?: string;
  /** Opened on the multi-selection: its actions run on every selected node. */
  selection?: boolean;
}

const prefersReducedMotion = () => typeof window !== 'undefined'
  && typeof window.matchMedia === 'function'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const NO_HIGHLIGHT = {
  nodeIds: new Set<string>(),
  edgeIds: new Set<string>(),
};

const GraphExplorerInner = ({
  initial,
  variant = 'page',
  height = '100%',
  nodeActions = [],
  onFocusChange,
  onOpen,
  initialLayout = 'force',
  onLayoutChange,
  fitMinZoom,
  selectFocus = true,
}: GraphExplorerProps) => {
  const theme = useTheme();
  const { t, fd } = useFormatter();
  const { fitView, screenToFlowPosition, zoomIn, zoomOut } = useReactFlow();
  const wrapper = useRef<HTMLDivElement>(null);
  const emptyPane = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const [state, setState] = useState<GraphState>(() => fromSlice(initial));
  const [history, setHistory] = useState<History>({ past: [] });
  const [filters, setFilters] = useState<GraphFilters>(DEFAULT_FILTERS);
  const [layout, setLayout] = useState<GraphLayout>(initialLayout);
  const [selected, setSelected] = useState<string | null>(selectFocus ? initial.graph_focus ?? null : null);
  const [pathFrom, setPathFrom] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [showFailed, setShowFailed] = useState(true);
  const [hovered, setHovered] = useState<string | null>(null);
  const [menu, setMenu] = useState<ContextMenuAnchor | null>(null);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  // The multi-selection, as OpenCTI's graph: Shift+drag draws a rectangle, Ctrl+click adds a node; the
  // selection's actions (expand, pin, hide, remove, select neighbours) run on every node of it.
  const [selection, setSelection] = useState<string[]>([]);
  // The lasso (OpenCTI's LassoSelection): while on, a drag on the canvas draws a free-hand outline and
  // the nodes it surrounds become the selection (Shift adds them to it). The points are client pixels,
  // drawn relative to the canvas.
  const [lassoOn, setLassoOn] = useState(false);
  const [lasso, setLasso] = useState<Point[] | null>(null);
  // Bumped to lay the whole graph out again (a layout button, Re-run the layout, a new initial slice);
  // every other change (an expand, a hide, a group opened) extends the layout on screen in place.
  const [layoutRun, setLayoutRun] = useState(0);
  const interactive = variant !== 'mini';
  // On the page and the embedded graph the open side panel covers the right of the canvas: the fit
  // keeps the graph left of it, so the focus never lands under the panel.
  const fitBesidePanel = interactive && Boolean(selected);
  // The mini graph never fits below MINI_MIN_ZOOM: its labels stay readable (10 px at zoom 1) and an
  // outer ring that does not fit is clipped by the card, the full graph being one click away.
  // Its padding is a fixed margin in pixels, so the top and bottom rows never touch the card's edges
  // (#9448, UX round 2 B3); the card itself is as tall as its layout needs at zoom 1.
  const fitOptions = useMemo(() => (variant === 'mini'
    ? {
        padding: `${MINI_CARD_PADDING}px` as const,
        minZoom: MINI_MIN_ZOOM,
        maxZoom: MINI_MIN_ZOOM,
      }
    : {
        padding: fitBesidePanel
          ? {
              top: '10%' as const,
              bottom: '10%' as const,
              left: '10%' as const,
              right: `${PANEL_WIDTH + 48}px` as const,
            }
          : 0.15,
        maxZoom: FIT_MAX_ZOOM,
        ...(fitMinZoom ? { minZoom: fitMinZoom } : {}),
      }), [variant, fitMinZoom, fitBesidePanel]);

  useEffect(() => {
    setState(fromSlice(initial));
    setHistory({ past: [] });
    setSelected(selectFocus ? initial.graph_focus ?? null : null);
    setLayoutRun(run => run + 1);
  }, [initial, selectFocus]);

  // State and history change in the event, never one inside the other's updater: React runs an updater
  // during the render, and a setState there is the console's "Cannot update a component while rendering
  // a different component" (#9572). The latest state is read through a ref for that reason.
  const stateRef = useRef(state);
  stateRef.current = state;
  const historyRef = useRef(history);
  historyRef.current = history;
  const commit = useCallback((next: (s: GraphState) => GraphState) => {
    const current = stateRef.current;
    const updated = next(current);
    stateRef.current = updated;
    historyRef.current = { past: [...historyRef.current.past.slice(-29), current] };
    setHistory(historyRef.current);
    setState(updated);
  }, []);

  const undo = () => {
    const { past } = historyRef.current;
    if (past.length === 0) {
      return;
    }
    const previous = past[past.length - 1];
    historyRef.current = { past: past.slice(0, -1) };
    stateRef.current = previous;
    setHistory(historyRef.current);
    setState(previous);
  };

  // The time range (OpenCTI's GraphToolbarTimeRange) spans the dates of every node loaded; a range the
  // user never narrowed follows new dates as expansions bring them, a narrowed one is clamped to them.
  const bounds = useMemo(() => dateBounds(Object.values(state.nodes)), [state.nodes]);
  const previousBounds = useRef<TimeRange | undefined>(bounds);
  useEffect(() => {
    const previous = previousBounds.current;
    previousBounds.current = bounds;
    if (previous?.[0] === bounds?.[0] && previous?.[1] === bounds?.[1]) {
      return;
    }
    setFilters((f) => {
      if (!f.timeRange) {
        return f;
      }
      return {
        ...f,
        timeRange: followBounds(f.timeRange, previous, bounds),
      };
    });
  }, [bounds]);
  const setTimeRange = (value: number[]) => {
    if (!bounds || value.length !== 2) {
      return;
    }
    const whole = value[0] <= bounds[0] && value[1] >= bounds[1];
    setFilters({
      ...filters,
      timeRange: whole ? null : [value[0], value[1]],
    });
  };

  const { nodes: shownNodes, edges: shownEdges, matches } = useMemo(() => visibleGraph(state, filters), [state, filters]);
  // Cross-platform marks, as on the list rows: the shown nodes that carry a real identifier (CVE
  // findings) are looked up in one batch per kind and per connected product (nothing is asked of a
  // product that is not connected); the mini graph card skips it.
  const counterpartRefs = useMemo(() => {
    const refs = new Map<string, CounterpartRef>();
    if (interactive) {
      shownNodes.forEach((n) => {
        const ref = nodeCounterpart(n);
        if (ref) {
          refs.set(n.node_id, ref);
        }
      });
    }
    return refs;
  }, [interactive, shownNodes]);
  const counterpartRefList = useMemo(() => Array.from(counterpartRefs.values()), [counterpartRefs]);
  const openCtiMatches = useCounterpartMatches(counterpartRefList, OPENCTI_PRODUCT);
  const openCrqMatches = useCounterpartMatches(counterpartRefList, OPENCRQ_PRODUCT);
  const inOpenCti = useMemo(() => matchedNodeIds(counterpartRefs, openCtiMatches), [counterpartRefs, openCtiMatches]);
  const inOpenCrq = useMemo(() => matchedNodeIds(counterpartRefs, openCrqMatches), [counterpartRefs, openCrqMatches]);
  // The context menu pivots: the deep links of the node under the menu, read from the cache the
  // marks just filled, rendered as links (never a scripted navigation). Only an http(s) URL is kept:
  // a counterpart URL comes from another platform.
  const [menuLinks, setMenuLinks] = useState<Record<string, string>>({});
  const menuNode = menu?.nodeId ? state.nodes[menu.nodeId] : undefined;
  const menuRef = menuNode ? nodeCounterpart(menuNode) : null;
  const menuRefId = menuRef ? counterpartRefId(menuRef) : '';
  useEffect(() => {
    setMenuLinks({});
    if (!menuRef) {
      return undefined;
    }
    let alive = true;
    resolveCounterparts(menuRef.kind, menuRef.key).then((answers) => {
      if (!alive) {
        return;
      }
      const links: Record<string, string> = {};
      answers.forEach((a) => {
        const url = a.counterpart_status === 'match' ? toHttpUrl(a.counterpart_url) : undefined;
        if (url && a.counterpart_product) {
          links[a.counterpart_product] = url;
        }
      });
      setMenuLinks(links);
    }).catch(() => {
      // A hint only: no link, no pivot item.
    });
    return () => {
      alive = false;
    };
  }, [menuRefId]);
  const failed = useMemo(() => {
    if (!showFailed) {
      return NO_HIGHLIGHT;
    }
    return failedHighlights(shownEdges);
  }, [shownEdges, showFailed]);
  const onPath = useMemo(() => pathEdges(state.path, shownEdges), [state.path, shownEdges]);
  const pathNodes = useMemo(() => new Set(state.path), [state.path]);
  // What is drawn: the mini card's ranked two-hop view, or the graph with the leaves of a node folded
  // by type into group nodes ("+14 Injects", as OpenCTI's graph collapse) unless the user opened them.
  const drawn = useMemo(() => {
    if (variant === 'mini') {
      return state.focus
        ? miniView(state.focus, shownNodes, shownEdges)
        : {
            nodes: shownNodes as GroupedNode[],
            edges: shownEdges,
          };
    }
    const keep = new Set<string>([...state.pinned, ...state.path, ...(state.focus ? [state.focus] : []), ...(selected ? [selected] : [])]);
    return groupNeighbours(shownNodes, shownEdges, state.ungrouped, keep);
  }, [variant, shownNodes, shownEdges, state.focus, state.pinned, state.path, state.ungrouped, selected]);
  const drawnNodes = drawn.nodes as GroupedNode[];
  const drawnEdges = drawn.edges;
  // The positions on screen, frame by frame while a move plays.
  const shown = useRef<Map<string, Point>>(new Map());
  // Where every node goes: the target of the last move committed, or where a drag dropped a node. An
  // expand extends THIS placement, never the mid-move positions, so a re-render during a layout run
  // (a click, a selection, the URL write) keeps the run's target and the move ends where it should.
  const settled = useRef<Map<string, Point>>(new Map());
  // The layout run (layout|run) whose target is on screen or on its way. Written in the commit phase
  // only: React may call a render twice (StrictMode) or restart it (a transition), so a render that
  // wrote it would make its own replay skip the layout (#9572).
  const laidOut = useRef<string>('');
  // Set when the view must frame the graph once the move has landed (a load, a layout, new nodes).
  const fitPending = useRef(true);
  // The frames toward the current layout. A whole layout (the force simulation's steps, or one static
  // placement) runs on a layout button, Re-run the layout and a new slice; an expand, a hide or a group
  // opened keeps every node where it is and places the newcomers next to their neighbour (an OpenCTI
  // investigation never jumps). The mini card draws its own compact two-hop block.
  // Pure: it reads the refs, never writes them (the animation effect does, once committed).
  const { frames, runKey, fit } = useMemo(
    () => planMove({
      variant,
      runKey: `${layout}|${layoutRun}`,
      laidOut: laidOut.current,
      settled: settled.current,
      layout,
      nodes: drawnNodes,
      edges: drawnEdges,
      focus: state.focus,
    }),
    [variant, layout, layoutRun, drawnNodes, drawnEdges, state.focus],
  );
  const target = frames[frames.length - 1];
  // What is on screen. A layout switch, an expand or a hide moves the nodes from where they are to
  // where they go: the force layout plays its simulation frames (the forces settle visibly), a static
  // layout glides; a node entering the graph grows out of its neighbour already on screen.
  const [positions, setPositions] = useState<Map<string, Point>>(target);
  const frame = useCallback(() => {
    if (!fitPending.current) {
      return;
    }
    fitPending.current = false;
    // After the frame that drew the last positions, so the fit measures the settled graph.
    window.requestAnimationFrame(() => fitView({
      ...fitOptions,
      duration: 300,
    }));
  }, [fitView, fitOptions]);
  useEffect(() => {
    laidOut.current = runKey;
    settled.current = target;
    if (fit) {
      fitPending.current = true;
    }
    const from = entering(shown.current, target, drawnEdges);
    const steps = frames.length > 1 ? frames : [target];
    const duration = frames.length > 1 ? Math.max(MOVE_MS, steps.length * FRAME_MS) : MOVE_MS;
    if (prefersReducedMotion() || variant === 'mini' || shown.current.size === 0) {
      shown.current = target;
      setPositions(target);
      frame();
      return undefined;
    }
    let raf = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const next = moveAt(from, steps, (now - start) / duration);
      const progress = Math.min(1, Math.max(0, (now - start) / duration));
      shown.current = next;
      setPositions(next);
      if (progress < 1) {
        raf = window.requestAnimationFrame(tick);
      } else {
        frame();
      }
    };
    raf = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(raf);
  }, [frames, target, runKey, fit, drawnEdges, variant, frame]);
  // The mini card frames its graph again when the window is resized (the card's width follows the
  // page), so no node stays cut or off-centre after a resize (#9448, UX round 2 B3).
  useEffect(() => {
    if (variant !== 'mini') {
      return undefined;
    }
    const refit = () => window.requestAnimationFrame(() => fitView(fitOptions));
    window.addEventListener('resize', refit);
    return () => window.removeEventListener('resize', refit);
  }, [variant, fitView, fitOptions]);
  const miniHeight = variant === 'mini' ? miniCardHeight(target, NODE_BOX_HEIGHT) : 0;
  // Hover dimming: the hovered node's first ring stays, the rest fades.
  const ring = useMemo(() => (hovered ? firstRing(hovered, drawnEdges) : null), [hovered, drawnEdges]);

  const flowNodes: GraphEntityNodeType[] = drawnNodes.map(n => ({
    id: n.node_id,
    type: 'entity',
    // Declared size (circle and label): the mini-map draws a node only once it has one, and the
    // nodes are controlled here without the measurement round-trip of onNodesChange.
    width: NODE_WIDTH,
    height: NODE_BOX_HEIGHT,
    position: positions.get(n.node_id) ?? {
      x: 0,
      y: 0,
    },
    selected: selection.includes(n.node_id),
    data: {
      node: n,
      focused: n.node_id === state.focus || n.node_id === selected || selection.includes(n.node_id),
      pinned: state.pinned.includes(n.node_id),
      matched: matches.has(n.node_id),
      dimmed: (matches.size > 0 && !matches.has(n.node_id))
        || (state.path.length > 0 && !pathNodes.has(n.node_id))
        || (ring !== null && !ring.nodeIds.has(n.node_id)),
      highlighted: (failed.nodeIds.has(n.node_id) || (showFailed && Boolean(n.node_group_members) && n.node_result === 'FAILED')) && n.node_type !== 'INJECT',
      truncated: state.truncatedAt.includes(n.node_id),
      groupCount: n.node_group_members?.length,
      inOpenCti: inOpenCti.has(n.node_id),
      inOpenCrq: inOpenCrq.has(n.node_id),
    },
  }));

  const flowEdges: Edge[] = drawnEdges.map((e) => {
    const isFailed = failed.edgeIds.has(e.edge_id) || (showFailed && e.edge_result === 'FAILED' && (isGroupId(e.edge_source) || isGroupId(e.edge_target)));
    const isPath = onPath.has(e.edge_id);
    let stroke = theme.palette.text.disabled;
    if (isPath) {
      stroke = theme.palette.primary.main;
    } else if (isFailed) {
      stroke = theme.palette.error.main;
    } else if (e.edge_result === 'SUCCESS') {
      stroke = theme.palette.success.main;
    } else if (e.edge_type === 'ATTACK_STEP') {
      stroke = theme.palette.warning.main;
    }
    // An attack-path step (#9447) is drawn dashed so the chain reads apart from the estate links.
    const isStep = e.edge_type === 'ATTACK_STEP';
    return {
      id: e.edge_id,
      type: 'floating',
      source: e.edge_source,
      target: e.edge_target,
      // The relation name only where it is read: on a path, around the selected node and on hover (a
      // failed edge already says it by its colour; a label on each of them piles up between close nodes).
      label: interactive && (isPath
        || (selected !== null && (e.edge_source === selected || e.edge_target === selected))
        || (hovered !== null && (e.edge_source === hovered || e.edge_target === hovered)))
        ? t(EDGE_TYPE_LABEL[e.edge_type])
        : undefined,
      animated: isPath,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: stroke,
      },
      style: {
        stroke,
        strokeWidth: isPath || isFailed || isStep ? 2 : 1,
        strokeDasharray: isStep ? '6 4' : undefined,
        opacity: (state.path.length > 0 && !isPath) || (ring !== null && !ring.edgeIds.has(e.edge_id)) ? 0.15 : 1,
        transition: 'opacity 0.2s',
      },
    };
  });

  // The nodes are controlled: a drag moves the node where it is dropped (the next expand extends the
  // layout from there), a rectangle or a Ctrl+click changes the multi-selection.
  const onNodesChange = (changes: NodeChange[]) => {
    let moved: Map<string, Point> | null = null;
    let picked: Set<string> | null = null;
    for (const change of changes) {
      if (change.type === 'position' && change.position) {
        moved = moved ?? new Map(shown.current);
        moved.set(change.id, change.position);
      } else if (change.type === 'select' && !isGroupId(change.id)) {
        picked = picked ?? new Set(selection);
        if (change.selected) {
          picked.add(change.id);
        } else {
          picked.delete(change.id);
        }
      }
    }
    if (moved) {
      shown.current = moved;
      settled.current = moved;
      setPositions(moved);
    }
    if (picked) {
      setSelection(Array.from(picked));
    }
  };

  /** Adds the neighbours of every selected node to the selection (OpenCTI: Select neighbours). */
  const selectNeighbours = (ids: string[] = selection) => {
    const next = new Set(ids);
    drawnEdges.forEach((e) => {
      if (ids.includes(e.edge_source) && !isGroupId(e.edge_target)) next.add(e.edge_target);
      if (ids.includes(e.edge_target) && !isGroupId(e.edge_source)) next.add(e.edge_source);
    });
    setSelection(Array.from(next));
  };

  const removeFromView = (ids: string[]) => {
    commit(s => removeNodes(s, ids));
    setSelection([]);
    if (selected && ids.includes(selected) && selected !== state.focus) {
      setSelected(null);
    }
  };

  const fitToScreen = () => fitView({
    ...fitOptions,
    duration: 300,
  });

  // The lasso's pointer handlers: the outline is kept in client coordinates, drawn relative to the
  // canvas, and turned into flow coordinates once closed to pick the nodes whose circle it surrounds.
  const lassoStart = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return;
    }
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setMenu(null);
    setLasso([{
      x: event.clientX,
      y: event.clientY,
    }]);
  };
  const lassoMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!lasso) {
      return;
    }
    setLasso([...lasso, {
      x: event.clientX,
      y: event.clientY,
    }]);
  };
  const lassoEnd = (event: PointerEvent<HTMLDivElement>) => {
    if (!lasso) {
      return;
    }
    const outline = lasso;
    setLasso(null);
    if (pathLength(outline) < LASSO_MIN_LENGTH) {
      return;
    }
    const polygon = outline.map(p => screenToFlowPosition(p));
    const picked = nodesInLasso(polygon, shown.current).filter(id => !isGroupId(id) && state.nodes[id]);
    setSelection(event.shiftKey ? Array.from(new Set([...selection, ...picked])) : picked);
  };
  const lassoOutline = () => {
    const box = wrapper.current?.getBoundingClientRect();
    if (!lasso || !box) {
      return '';
    }
    return lasso.map(p => `${p.x - box.left},${p.y - box.top}`).join(' ');
  };

  const relayout = (next: GraphLayout = layout) => {
    setLayout(next);
    setLayoutRun(run => run + 1);
    if (next !== layout) {
      onLayoutChange?.(next);
    }
  };

  /**
   * Expands a node in place (OpenCTI investigation): its neighbours join the graph on screen next to
   * it, every node already there keeps its place, the node becomes the focus. A relation or an entity
   * type narrows the expansion; Undo rolls it back.
   */
  const expand = async (node: GraphNodeOutput, edges: GraphEdgeType[] = [], limit = 50, nodeType?: GraphNodeType) => {
    setLoading(true);
    try {
      const slice = await fetchGraphNeighbourhood(node.node_type, node.node_entity_id, edges, limit);
      if (slice.graph_nodes.every(n => n.node_id === node.node_id || state.nodes[n.node_id])) {
        // Nothing new around this node: say so instead of a click with no visible effect.
        setNotice(t('Nothing more to expand around this node'));
      }
      commit(s => expandSlice(s, slice, node.node_id, nodeType));
      setSelected(node.node_id);
      onFocusChange?.(node.node_id);
    } finally {
      setLoading(false);
    }
  };

  const findPath = async (from: GraphNodeOutput, to: GraphNodeOutput) => {
    setLoading(true);
    try {
      const slice = await fetchGraphPath(from.node_type, from.node_entity_id, to.node_type, to.node_entity_id);
      if (slice.graph_path.length === 0) {
        setNotice(t('No path found between these two nodes within six hops'));
      } else {
        commit(s => mergeSlice({
          ...s,
          path: [],
        }, slice));
      }
    } finally {
      setPathFrom(null);
      setLoading(false);
    }
  };

  const exportPng = () => {
    const viewport = wrapper.current?.querySelector('.react-flow__viewport') as HTMLElement | null;
    if (!viewport) {
      return;
    }
    const bounds = getNodesBounds(flowNodes);
    const width = 1600;
    const imageHeight = 1000;
    const transform = getViewportForBounds(bounds, width, imageHeight, 0.2, 2, 0.1);
    toPng(viewport, {
      backgroundColor: theme.palette.background.default,
      width,
      height: imageHeight,
      style: {
        width: `${width}px`,
        height: `${imageHeight}px`,
        transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.zoom})`,
      },
    }).then((url) => {
      const a = document.createElement('a');
      a.download = 'openaev-graph.png';
      a.href = url;
      a.click();
    });
  };

  const copyLink = (node?: GraphNodeOutput) => {
    const focusNode = node ?? (state.focus ? state.nodes[state.focus] : undefined);
    const href = focusNode ? graphHref(focusNode.node_type, focusNode.node_entity_id) : '/admin/graph';
    const url = new URL(href, window.location.origin);
    url.searchParams.set('layout', layout);
    navigator.clipboard?.writeText(url.toString());
    setNotice(t('Link copied to the clipboard'));
  };

  // A hidden node has no side panel: hiding it (alone, in a selection, by its key) closes the panel.
  const selectedNode = selected && !state.hidden.includes(selected) ? state.nodes[selected] : undefined;
  const neighbours = useMemo(() => {
    if (!selectedNode) {
      return [] as {
        node: GraphNodeOutput;
        type: GraphEdgeType;
        result?: GraphResult | null;
      }[];
    }
    // One row per neighbour and relation: a membership read from both ends (a group expanded, then
    // one of its assets) is two edges of opposite direction and one relation (#9572: duplicate keys).
    const rows = new Map<string, { node: GraphNodeOutput; type: GraphEdgeType; result?: GraphResult | null }>();
    Object.values(state.edges)
      .filter(e => e.edge_source === selectedNode.node_id || e.edge_target === selectedNode.node_id)
      .forEach((e) => {
        const other = e.edge_source === selectedNode.node_id ? e.edge_target : e.edge_source;
        const node = state.nodes[other];
        const key = `${e.edge_type}-${other}`;
        if (node && !rows.has(key)) {
          rows.set(key, {
            node,
            type: e.edge_type,
            result: e.edge_result,
          });
        }
      });
    return Array.from(rows.values());
  }, [selectedNode, state]);

  const toggle = <T, >(list: T[], value: T) => (list.includes(value) ? list.filter(v => v !== value) : [...list, value]);

  const resultColour: Record<GraphResult, string> = {
    FAILED: theme.palette.error.main,
    PENDING: theme.palette.warning.main,
    SUCCESS: theme.palette.success.main,
  };

  // The legend, as OpenCTI's GraphLegend: the entity types present in the graph with their count, a
  // click shows or hides the type (the Entities filter, in sync), then the result colours.
  const typeCounts = useMemo(() => {
    const counts = new Map<GraphNodeType, number>();
    Object.values(state.nodes).filter(n => !state.hidden.includes(n.node_id)).forEach(n => counts.set(n.node_type, (counts.get(n.node_type) ?? 0) + 1));
    return counts;
  }, [state.nodes, state.hidden]);
  const visibleCount = Array.from(typeCounts.values()).reduce((sum, count) => sum + count, 0);
  // As OpenCTI's GraphLegend, a card floating in the canvas (bottom left), one line per type present
  // with its count, collapsed by its header; open on the page, closed on the smaller embedded graph.
  const [legendOpen, setLegendOpen] = useState(variant === 'page');
  const legendEntries = (
    <>
      {ALL_NODE_TYPES.filter(type => typeCounts.has(type)).map((type) => {
        const Icon = NODE_ICON[type];
        const on = filters.nodeTypes.includes(type);
        return (
          <Tooltip key={type}>
            <TooltipTrigger asChild>
              <button
                type="button"
                aria-pressed={on}
                onClick={() => setFilters({
                  ...filters,
                  nodeTypes: toggle(filters.nodeTypes, type),
                })}
                className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 hover:bg-[var(--background-default-secondary)]"
                style={{
                  opacity: on ? 1 : 0.4,
                  textDecoration: on ? 'none' : 'line-through',
                  border: 0,
                  background: 'transparent',
                  color: 'inherit',
                }}
              >
                <Icon />
                <Text variant="content-caption">{`${t(NODE_TYPE_LABEL[type])} (${typeCounts.get(type)})`}</Text>
              </button>
            </TooltipTrigger>
            <TooltipContent>{on ? t('Hide this type') : t('Show this type')}</TooltipContent>
          </Tooltip>
        );
      })}
      <span aria-hidden className="my-1 h-px w-full bg-[var(--border-default-primary)]" />
      {ALL_RESULTS.map(result => (
        <span key={result} className="inline-flex items-center gap-1 px-1">
          <span
            aria-hidden
            style={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              border: `2px solid ${resultColour[result]}`,
            }}
          />
          <Text variant="content-caption">{t(RESULT_LABEL[result])}</Text>
        </span>
      ))}
      <span className="inline-flex items-center gap-1 px-1">
        <span
          aria-hidden
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            border: `2px dashed ${theme.palette.text.secondary}`,
          }}
        />
        <Text variant="content-caption">{t('Group of neighbours, double-click to open')}</Text>
      </span>
    </>
  );
  const renderLegend = () => (
    <div
      data-testid="graph-legend"
      className="absolute flex flex-col rounded"
      style={{
        left: 12,
        bottom: 12,
        zIndex: 4,
        maxWidth: 240,
        maxHeight: 'calc(100% - 24px)',
        overflowY: 'auto',
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.divider}`,
      }}
    >
      <button
        type="button"
        aria-expanded={legendOpen}
        onClick={() => setLegendOpen(!legendOpen)}
        className="inline-flex items-center justify-between gap-2 px-2 py-1 hover:bg-[var(--background-default-secondary)]"
        style={{
          border: 0,
          background: 'transparent',
          color: 'inherit',
        }}
      >
        <Text variant="content-caption">{t('Legend')}</Text>
        {legendOpen ? <ExpandMoreOutlined fontSize="small" /> : <ExpandLessOutlined fontSize="small" />}
      </button>
      {legendOpen && (
        <div className="flex flex-col items-start gap-1 px-1 pb-1" role="group" aria-label={t('Legend')}>
          {legendEntries}
        </div>
      )}
    </div>
  );

  /** A multi-select of the FDS menu: a trigger that names the count, a checked item per value. */
  const multiFilter = <T extends string>(label: string, all: T[], values: T[], names: Record<T, string>, onChange: (next: T[]) => void) => (
    <Menu>
      <MenuTrigger asChild>
        <Button type="button" priority="secondary" endIcon={<ArrowDropDownOutlined fontSize="small" />} aria-label={label}>
          {`${label} (${values.length}/${all.length})`}
        </Button>
      </MenuTrigger>
      <MenuContent align="start">
        {all.map(value => (
          <MenuItem
            key={value}
            selected={values.includes(value)}
            onSelect={(event) => {
              // Keep the menu open: several values are picked in a row.
              event.preventDefault();
              onChange(toggle(values, value));
            }}
          >
            {t(names[value])}
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );

  const iconAction = (label: string, icon: ReactNode, onClick: () => void, disabled = false, active = false) => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>
          <IconButton priority="tertiary" icon={icon} aria-label={label} onClick={onClick} disabled={disabled} active={active} aria-pressed={active || undefined} />
        </span>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );

  const toolbar = (
    <Paper elevation={1} padding={16}>
      <div className="flex flex-wrap items-center gap-3">
        <SearchField
          aria-label={t('Search in the graph')}
          placeholder={t('Search in the graph')}
          value={filters.search}
          onChange={e => setFilters({
            ...filters,
            search: e.target.value,
          })}
          onClear={() => setFilters({
            ...filters,
            search: '',
          })}
          clearLabel={t('Clear')}
        />
        {multiFilter(t('Entities'), ALL_NODE_TYPES, filters.nodeTypes, NODE_TYPE_LABEL, nodeTypes => setFilters({
          ...filters,
          nodeTypes,
        }))}
        {multiFilter(t('Relations'), ALL_EDGE_TYPES, filters.edgeTypes, EDGE_TYPE_LABEL, edgeTypes => setFilters({
          ...filters,
          edgeTypes,
        }))}
        <div className="flex items-center gap-1" role="group" aria-label={t('Result')}>
          {ALL_RESULTS.map(result => (
            <Chip
              key={result}
              label={t(RESULT_LABEL[result])}
              severity={filters.results.includes(result) ? RESULT_CHIP_SEVERITY[result] : 'neutral'}
              aria-pressed={filters.results.includes(result)}
              onClick={() => setFilters({
                ...filters,
                results: toggle(filters.results, result),
              })}
            />
          ))}
        </div>
        <Switch
          label={t('Focus mode')}
          checked={filters.focusMode}
          onCheckedChange={checked => setFilters({
            ...filters,
            focusMode: checked === true,
          })}
        />
        <Switch
          label={t('Highlight failed paths')}
          checked={showFailed}
          onCheckedChange={checked => setShowFailed(checked === true)}
        />
        {bounds && (
          <div style={{ width: 240 }} data-testid="graph-time-range">
            <Slider
              aria-label={t('Time range')}
              thumbLabels={[t('Start of the time range'), t('End of the time range')]}
              min={bounds[0]}
              max={bounds[1]}
              step={TIME_RANGE_STEP}
              minStepsBetweenThumbs={0}
              value={filters.timeRange ?? bounds}
              onValueChange={setTimeRange}
              showBounds
              minLabel={fd((filters.timeRange ?? bounds)[0])}
              maxLabel={fd((filters.timeRange ?? bounds)[1])}
            />
          </div>
        )}
      </div>
    </Paper>
  );

  // The graph actions, as OpenCTI's GraphToolbar: ONE icon bar on the canvas (bottom right, left of
  // the open side panel, clear of the legend card) opened by the count of entities shown. Each
  // layout button runs its layout from scratch (a second click on the active one re-runs it), then
  // the view is framed; every button says what it does on hover. The embedded graph keeps the count, the zoom and the fit.
  const canvasToolbar = (offset: number) => (
    <div
      data-testid="graph-canvas-toolbar"
      role="toolbar"
      aria-label={t('Graph layout')}
      className="absolute flex items-center gap-1 rounded px-2"
      style={{
        bottom: 12,
        right: 12 + offset,
        zIndex: 4,
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.divider}`,
      }}
    >
      <Text variant="content-caption" className="px-1 text-default-secondary">{`${t('Entities')}: ${visibleCount}`}</Text>
      <span aria-hidden className="mx-1 h-4 w-px bg-[var(--border-default-primary)]" />
      {iconAction(t('Zoom in'), <ZoomInOutlined fontSize="small" />, () => zoomIn())}
      {iconAction(t('Zoom out'), <ZoomOutOutlined fontSize="small" />, () => zoomOut())}
      {iconAction(t('Fit to screen'), <FitScreenOutlined fontSize="small" />, fitToScreen)}
      {variant === 'page' && (
        <>
          <span aria-hidden className="mx-1 h-4 w-px bg-[var(--border-default-primary)]" />
          {(['force', 'hierarchical', 'radial'] as GraphLayout[]).map(value => (
            <span key={value}>{iconAction(t(LAYOUT_LABEL[value]), LAYOUT_ICON[value], () => relayout(value), false, layout === value)}</span>
          ))}
          {iconAction(t('Re-run the layout'), <AutorenewOutlined fontSize="small" />, () => relayout())}
          <span aria-hidden className="mx-1 h-4 w-px bg-[var(--border-default-primary)]" />
          {iconAction(t(LASSO_LABEL), <GestureOutlined fontSize="small" />, () => setLassoOn(!lassoOn), false, lassoOn)}
          {iconAction(t('Show hidden nodes'), <VisibilityOutlined fontSize="small" />, () => commit(showHidden), state.hidden.length === 0)}
          {iconAction(t('Undo'), <UndoOutlined fontSize="small" />, undo, history.past.length === 0)}
          <span aria-hidden className="mx-1 h-4 w-px bg-[var(--border-default-primary)]" />
          {iconAction(t('Export as PNG'), <DownloadOutlined fontSize="small" />, exportPng)}
          {iconAction(t('Copy a link to this view'), <LinkOutlined fontSize="small" />, () => copyLink())}
          {iconAction(t('Keyboard shortcuts'), <KeyboardOutlined fontSize="small" />, () => setShortcutsOpen(true))}
        </>
      )}
    </div>
  );

  const panelOpen = Boolean(selectedNode && interactive);
  const selectedCounterpart = selectedNode ? nodeCounterpart(selectedNode) : null;

  const panel = selectedNode && interactive && (
    <Drawer
      anchor="right"
      variant="persistent"
      open={panelOpen}
      slotProps={{
        paper: {
          sx: {
            position: 'absolute',
            width: PANEL_WIDTH,
            p: 2,
          },
        },
      }}
      sx={{
        '& .MuiDrawer-paper': {
          height: '100%',
          boxSizing: 'border-box',
        },
      }}
    >
      <div className="flex items-center gap-2">
        {NODE_ICON[selectedNode.node_type]()}
        <Text variant="title-sm" as="div" className="min-w-0 truncate" style={{ flexGrow: 1 }}>
          {selectedNode.node_label}
        </Text>
        <IconButton priority="tertiary" icon={<CloseOutlined fontSize="small" />} onClick={() => setSelected(null)} aria-label={t('Close')} />
      </div>
      <Text variant="content-compact" as="div" className="text-default-secondary">
        {t(NODE_TYPE_LABEL[selectedNode.node_type])}
        {selectedNode.node_subtype ? ` · ${selectedNode.node_subtype}` : ''}
      </Text>
      <div className="mt-3 flex flex-wrap gap-2">
        <Chip severity={selectedNode.node_failed_count > 0 ? 'critical' : 'neutral'} label={`${t('Failed expectations')}: ${selectedNode.node_failed_count}`} />
        <Chip label={`${t('Findings')}: ${selectedNode.node_finding_count}`} />
      </div>
      <div className="mt-4 flex flex-col gap-2">
        {/* The node's counterparts, as on the entity's own screen: "Open in", "Not in" or the
            connect call to action, per product. */}
        {selectedCounterpart && <OpenCtiPivot kind={selectedCounterpart.kind} objectKey={selectedCounterpart.key} size="md" />}
        {selectedCounterpart && <OpenCrqPivot kind={selectedCounterpart.kind} objectKey={selectedCounterpart.key} size="md" />}
        {nodeHref(selectedNode) && (
          <Button asChild priority="secondary">
            <Link to={nodeHref(selectedNode) as string}>
              <OpenInNewOutlined fontSize="small" />
              {t('Open')}
            </Link>
          </Button>
        )}
        <Button type="button" priority="secondary" startIcon={<CenterFocusStrongOutlined fontSize="small" />} onClick={() => expand(selectedNode)}>
          {t('Expand')}
        </Button>
        <Button
          type="button"
          priority="secondary"
          startIcon={<RouteOutlined fontSize="small" />}
          onClick={() => setPathFrom(selectedNode.node_id)}
          disabled={pathFrom === selectedNode.node_id}
        >
          {pathFrom === selectedNode.node_id ? t('Select the destination node') : t('Find a path from here')}
        </Button>
        <div className="flex gap-2">
          <Button type="button" priority="tertiary" startIcon={<PushPinOutlined fontSize="small" />} onClick={() => commit(s => togglePin(s, selectedNode.node_id))}>
            {state.pinned.includes(selectedNode.node_id) ? t('Unpin') : t('Pin')}
          </Button>
          <Button
            type="button"
            priority="tertiary"
            startIcon={<VisibilityOffOutlined fontSize="small" />}
            onClick={() => {
              commit(s => hideNode(s, selectedNode.node_id));
              setSelected(null);
            }}
          >
            {t('Hide')}
          </Button>
        </div>
        {nodeActions.filter(a => a.appliesTo(selectedNode)).map(action => (
          <Button key={action.key} type="button" priority="secondary" startIcon={action.icon} onClick={() => action.onClick(selectedNode)}>
            {action.label}
          </Button>
        ))}
      </div>
      <Text variant="content-compact-bold" as="div" className="mt-4">{t('Expand by relation')}</Text>
      <div className="mt-1 flex flex-wrap gap-1">
        {EDGE_TYPES_BY_NODE_TYPE[selectedNode.node_type].map(type => (
          <Chip key={type} label={t(EDGE_TYPE_LABEL[type])} onClick={() => expand(selectedNode, [type])} />
        ))}
      </div>
      {state.truncatedAt.includes(selectedNode.node_id) && (
        <div className="mt-2">
          <Button type="button" priority="tertiary" onClick={() => expand(selectedNode, [], 200)}>{t('Load more')}</Button>
        </div>
      )}
      <Text variant="content-compact-bold" as="div" className="mt-4">{t('Neighbours')}</Text>
      <List dense sx={{ overflowY: 'auto' }}>
        {neighbours.map(({ node, type, result }) => (
          <ListItemButton
            key={`${type}-${node.node_id}`}
            onClick={() => setSelected(node.node_id)}
            onMouseEnter={() => setHovered(node.node_id)}
            onMouseLeave={() => setHovered(null)}
          >
            <ListItemText
              primary={node.node_label}
              secondary={`${t(EDGE_TYPE_LABEL[type])} · ${t(NODE_TYPE_LABEL[node.node_type])}${result ? ` · ${t(RESULT_LABEL[result])}` : ''}`}
            />
          </ListItemButton>
        ))}
      </List>
    </Drawer>
  );

  // The right-click menu, as OpenCTI's GraphContextMenu: the actions that apply to what is under the
  // pointer (a node, a group, a relation, the empty canvas), grouped under a label naming it.
  const openMenu = (event: MouseEvent | globalThis.MouseEvent, on: Pick<ContextMenuAnchor, 'nodeId' | 'edgeId' | 'selection'>) => {
    event.preventDefault();
    const box = wrapper.current?.getBoundingClientRect();
    if (on.nodeId && !isGroupId(on.nodeId)) {
      setSelected(on.nodeId);
    }
    setMenu({
      x: event.clientX - (box?.left ?? 0),
      y: event.clientY - (box?.top ?? 0),
      ...on,
    });
  };
  const closeMenu = () => setMenu(null);
  const menuItem = (key: string, label: string, icon: ReactNode, onSelect: () => void, disabled = false) => (
    <MenuItem key={key} startIcon={icon} disabled={disabled} onSelect={onSelect}>{label}</MenuItem>
  );
  const linkItem = (key: string, label: string, icon: ReactNode, href: string | undefined) => {
    if (!href) {
      return [];
    }
    return [(
      <MenuItem key={key} startIcon={icon} asChild>
        <a href={href} target="_blank" rel="noopener noreferrer">{label}</a>
      </MenuItem>
    )];
  };
  const nodeMenu = (node: GraphNodeOutput) => {
    const href = nodeHref(node);
    const present = ALL_NODE_TYPES.filter(type => type !== node.node_type);
    return [
      <MenuLabel key="label">{`${t(NODE_TYPE_LABEL[node.node_type])}: ${node.node_label}`}</MenuLabel>,
      menuItem('expand', t('Expand'), <CenterFocusStrongOutlined fontSize="small" />, () => expand(node)),
      <MenuSub key="by-relation">
        <MenuSubTrigger>{t('Expand by relation')}</MenuSubTrigger>
        <MenuSubContent>
          {EDGE_TYPES_BY_NODE_TYPE[node.node_type].map(type => menuItem(type, t(EDGE_TYPE_LABEL[type]), null, () => expand(node, [type])))}
        </MenuSubContent>
      </MenuSub>,
      <MenuSub key="by-type">
        <MenuSubTrigger>{t('Expand by entity type')}</MenuSubTrigger>
        <MenuSubContent>
          {present.map(type => menuItem(type, t(NODE_TYPE_PLURAL[type]), null, () => expand(node, [], 200, type)))}
        </MenuSubContent>
      </MenuSub>,
      ...(state.truncatedAt.includes(node.node_id) ? [menuItem('more', t('Load more'), null, () => expand(node, [], 200))] : []),
      <MenuSeparator key="sep-1" />,
      menuItem('path', t('Find a path from here'), <RouteOutlined fontSize="small" />, () => setPathFrom(node.node_id)),
      menuItem('pin', state.pinned.includes(node.node_id) ? t('Unpin') : t('Pin'), <PushPinOutlined fontSize="small" />, () => commit(s => togglePin(s, node.node_id))),
      menuItem('hide', t('Hide'), <VisibilityOffOutlined fontSize="small" />, () => {
        commit(s => hideNode(s, node.node_id));
        setSelected(null);
      }),
      menuItem('neighbours', t('Select neighbours'), <SelectAllOutlined fontSize="small" />, () => selectNeighbours([node.node_id])),
      menuItem('remove', t('Remove from view'), <DeleteOutlined fontSize="small" />, () => removeFromView([node.node_id]), node.node_id === state.focus),
      ...(href ? [menuItem('open', t('Open'), <OpenInNewOutlined fontSize="small" />, () => navigate(href))] : []),
      ...(inOpenCti.has(node.node_id) ? linkItem('opencti', t('Open in OpenCTI'), <ProductMark size={16} />, menuLinks[OPENCTI_PRODUCT]) : []),
      ...(inOpenCrq.has(node.node_id) ? linkItem('opencrq', t('Open in OpenCRQ'), <OpenCrqMark size={16} />, menuLinks[OPENCRQ_PRODUCT]) : []),
      menuItem('copy-link', t('Copy a link to this view'), <LinkOutlined fontSize="small" />, () => copyLink(node)),
      ...nodeActions.filter(a => a.appliesTo(node)).map(a => menuItem(a.key, a.label, a.icon, () => a.onClick(node))),
    ];
  };
  const selectionMenu = () => {
    const nodes = selection.map(id => state.nodes[id]).filter((n): n is GraphNodeOutput => Boolean(n));
    const ids = nodes.map(n => n.node_id);
    return [
      <MenuLabel key="label">{`${t('Selection')}: ${nodes.length}`}</MenuLabel>,
      menuItem('expand-all', t('Expand'), <CenterFocusStrongOutlined fontSize="small" />, () => {
        nodes.forEach(n => expand(n));
      }),
      menuItem('neighbours', t('Select neighbours'), <SelectAllOutlined fontSize="small" />, () => selectNeighbours(ids)),
      menuItem('pin', t('Pin'), <PushPinOutlined fontSize="small" />, () => commit(s => pinNodes(s, ids))),
      menuItem('hide', t('Hide'), <VisibilityOffOutlined fontSize="small" />, () => {
        commit(s => hideNodes(s, ids));
        setSelection([]);
        setSelected(current => (current && ids.includes(current) ? null : current));
      }),
      menuItem('remove', t('Remove from view'), <DeleteOutlined fontSize="small" />, () => removeFromView(ids)),
      <MenuSeparator key="sep" />,
      menuItem('clear', t('Clear the selection'), <CloseOutlined fontSize="small" />, () => setSelection([])),
    ];
  };
  const graphMenu = () => [
    <MenuLabel key="label">{t('Graph')}</MenuLabel>,
    menuItem('relayout', t('Re-run the layout'), <AutorenewOutlined fontSize="small" />, () => relayout()),
    menuItem('fit', t('Fit to screen'), <FitScreenOutlined fontSize="small" />, fitToScreen),
    menuItem('show', t('Show hidden nodes'), <VisibilityOutlined fontSize="small" />, () => commit(showHidden), state.hidden.length === 0),
    menuItem('undo', t('Undo'), <UndoOutlined fontSize="small" />, undo, history.past.length === 0),
    menuItem('shortcuts', t('Keyboard shortcuts'), <KeyboardOutlined fontSize="small" />, () => setShortcutsOpen(true)),
  ];
  const menuSections = (): ReactNode[] => {
    if (!menu) {
      return [];
    }
    if (menu.selection || (menu.nodeId && selection.length > 1 && selection.includes(menu.nodeId))) {
      return selectionMenu();
    }
    if (menu.nodeId && isGroupId(menu.nodeId)) {
      const groupNode = drawnNodes.find(n => n.node_id === menu.nodeId);
      return [
        <MenuLabel key="label">{groupNode ? `+${groupNode.node_group_members?.length ?? 0} ${t(NODE_TYPE_PLURAL[groupNode.node_type])}` : t('Group')}</MenuLabel>,
        menuItem('ungroup', t('Ungroup'), <OpenInFullOutlined fontSize="small" />, () => commit(s => ungroup(s, menu.nodeId as string))),
      ];
    }
    if (menu.nodeId && state.nodes[menu.nodeId]) {
      return nodeMenu(state.nodes[menu.nodeId]);
    }
    if (menu.edgeId) {
      const edge = drawnEdges.find(e => e.edge_id === menu.edgeId);
      const ends = edge ? [state.nodes[edge.edge_source], state.nodes[edge.edge_target]].filter((n): n is GraphNodeOutput => Boolean(n)) : [];
      return [
        <MenuLabel key="label">{edge ? t(EDGE_TYPE_LABEL[edge.edge_type]) : t('Relations')}</MenuLabel>,
        ...ends.map(n => menuItem(`expand-${n.node_id}`, `${t('Expand')}: ${n.node_label}`, <CenterFocusStrongOutlined fontSize="small" />, () => expand(n))),
        ...ends.map(n => menuItem(`select-${n.node_id}`, `${t('Select')}: ${n.node_label}`, null, () => setSelected(n.node_id))),
      ];
    }
    return graphMenu();
  };
  const contextMenu = menu && (
    <Menu
      open
      modal={false}
      onOpenChange={(open) => {
        if (!open) {
          closeMenu();
        }
      }}
    >
      {/* The library Menu opens from a trigger only: a hidden one sits at the pointer, as in OpenCTI. */}
      <MenuTrigger asChild>
        <span
          aria-hidden
          tabIndex={-1}
          style={{
            position: 'absolute',
            left: menu.x,
            top: menu.y,
            width: 1,
            height: 1,
            pointerEvents: 'none',
          }}
        />
      </MenuTrigger>
      <MenuContent align="start" side="bottom" aria-label={t('Graph actions')} collisionPadding={8} data-testid="graph-context-menu">
        {menuSections()}
      </MenuContent>
    </Menu>
  );

  const shortcutsDialog = (
    <Dialog open={shortcutsOpen} onOpenChange={setShortcutsOpen}>
      <DialogContent size="sm">
        <DialogTitle>{t('Keyboard shortcuts')}</DialogTitle>
        <DialogBody>
          <table className="w-full">
            <tbody>
              {SHORTCUTS.map(([keys, action]) => (
                <tr key={keys}>
                  <td className="py-1 pr-4"><kbd>{keys}</kbd></td>
                  <td className="py-1"><Text variant="content-compact">{t(action)}</Text></td>
                </tr>
              ))}
            </tbody>
          </table>
        </DialogBody>
      </DialogContent>
    </Dialog>
  );

  // The shortcuts act on the graph while the focus is in it (a field of the toolbar keeps its keys).
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    if (!interactive || target.closest('input, textarea, [role="menu"], [role="dialog"]')) {
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      undo();
      return;
    }
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key === '?') {
      setShortcutsOpen(true);
    } else if (key === 'f') {
      fitToScreen();
    } else if (key === 'l') {
      relayout();
    } else if (key === 's') {
      setLassoOn(!lassoOn);
    } else if (key === 'escape' && (selection.length > 0 || lassoOn)) {
      setSelection([]);
      setLassoOn(false);
      setLasso(null);
    } else if (key === 'n' && (selection.length > 0 || selectedNode)) {
      selectNeighbours(selection.length > 0 ? selection : [selectedNode?.node_id as string]);
    } else if ((key === 'delete' || key === 'backspace') && (selection.length > 0 || selectedNode)) {
      removeFromView(selection.length > 0 ? selection : [selectedNode?.node_id as string]);
    } else if (selectedNode && key === 'e') {
      expand(selectedNode);
    } else if (selectedNode && key === 'p') {
      commit(s => togglePin(s, selectedNode.node_id));
    } else if (selectedNode && key === 'h') {
      commit(s => hideNode(s, selectedNode.node_id));
      setSelected(null);
    } else {
      return;
    }
    event.preventDefault();
  };

  const empty = drawnNodes.length === 0;
  // Every node hidden is not an empty graph: the toolbar stays (Show hidden nodes, Undo) and the empty
  // canvas offers to show them again, so hiding never leaves the user without a way back.
  const allHidden = empty && state.hidden.length > 0;
  // The menu item that hid the last node is gone with the menu: the focus moves to the empty canvas, so
  // Ctrl+Z (handled on the wrapper) still undoes the hide.
  useEffect(() => {
    if (allHidden && interactive) {
      emptyPane.current?.focus();
    }
  }, [allHidden, interactive]);
  // The controls and the mini-map move left of the open side panel instead of sitting under it.
  const sideOffset = panelOpen ? PANEL_WIDTH + 8 : 0;

  const canvas = empty
    ? (
        <div
          ref={emptyPane}
          data-testid="graph-empty"
          tabIndex={allHidden && interactive ? 0 : undefined}
          className="flex h-full flex-col items-center justify-center gap-3 outline-none"
        >
          <Text variant="content-compact" className="text-default-secondary">
            {allHidden ? t('Every node of the graph is hidden') : t('Nothing to show in the graph yet')}
          </Text>
          {allHidden && (
            <Button type="button" priority="secondary" size="sm" onClick={() => commit(showHidden)}>
              {`${t('Show hidden nodes')} (${state.hidden.length})`}
            </Button>
          )}
        </div>
      )
    : (
        <ReactFlow
          nodes={flowNodes}
          edges={flowEdges}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          proOptions={proOptions}
          colorMode={theme.palette.mode}
          nodesDraggable={interactive}
          nodesConnectable={false}
          nodesFocusable={interactive}
          edgesFocusable={false}
          elementsSelectable={interactive}
          panOnDrag={interactive}
          zoomOnScroll={interactive}
          zoomOnPinch={interactive}
          zoomOnDoubleClick={false}
          preventScrolling={interactive}
          minZoom={0.1}
          // Set on the mini graph too: React Flow gives a node pointer events only when it has a
          // handler, and the node's tooltip needs the hover (the pane itself stays inert).
          onNodesChange={interactive ? onNodesChange : undefined}
          // Shift+drag draws a selection rectangle, Ctrl or Cmd+click adds a node (OpenCTI's graph);
          // the Delete key is ours (Remove from view), never React Flow's own deletion.
          selectionKeyCode={interactive ? 'Shift' : null}
          multiSelectionKeyCode={interactive ? ['Meta', 'Control'] : null}
          deleteKeyCode={null}
          onSelectionContextMenu={interactive ? event => openMenu(event, { selection: true }) : undefined}
          onNodeMouseEnter={(_, n) => setHovered(n.id)}
          onNodeMouseLeave={() => setHovered(null)}
          onNodeClick={interactive
            ? (_, n) => {
                setMenu(null);
                if (isGroupId(n.id)) {
                  return;
                }
                const node = state.nodes[n.id];
                if (pathFrom && pathFrom !== n.id) {
                  findPath(state.nodes[pathFrom], node);
                  return;
                }
                setSelected(n.id);
              }
            : undefined}
          onNodeDoubleClick={interactive
            ? (_, n) => (isGroupId(n.id) ? commit(s => ungroup(s, n.id)) : expand(state.nodes[n.id]))
            : undefined}
          onNodeContextMenu={interactive ? (event, n) => openMenu(event, { nodeId: n.id }) : undefined}
          onEdgeContextMenu={interactive ? (event, e) => openMenu(event, { edgeId: e.id }) : undefined}
          onPaneContextMenu={interactive ? event => openMenu(event, {}) : undefined}
          onPaneClick={() => {
            setMenu(null);
            setSelection([]);
          }}
          panOnScroll={false}
          fitView
          fitViewOptions={fitOptions}
        >
          <Background />
          {variant === 'page' && drawnNodes.length > MINI_MAP_MIN_NODES && (
            <MiniMap
              pannable
              zoomable
              style={{ marginRight: 8 + sideOffset }}
              bgColor={theme.palette.background.paper}
              maskColor={alpha(theme.palette.background.default, 0.6)}
              nodeColor={n => ((n.data as GraphEntityNodeType['data']).node.node_failed_count > 0 ? theme.palette.error.main : theme.palette.primary.main)}
              nodeStrokeWidth={0}
              nodeBorderRadius={12}
            />
          )}
        </ReactFlow>
      );

  // The mini graph of an Overview is a read-only preview whose only action is to open the graph: a
  // link with a name, in the tab order, opened by Enter as any link. The pane takes no pointer event
  // (a wheel scrolls the page, a drag does nothing); the nodes alone take the hover for their tooltip,
  // and a click on a node bubbles to the link.
  if (variant === 'mini') {
    const focusNode = state.focus ? state.nodes[state.focus] : undefined;
    const href = focusNode ? graphHref(focusNode.node_type, focusNode.node_entity_id) : '/admin/graph';
    return (
      <Box
        component={Link}
        to={href}
        onClick={onOpen ? (event: MouseEvent) => {
          event.preventDefault();
          onOpen();
        } : undefined}
        aria-label={focusNode ? `${t('Explore in graph')}: ${focusNode.node_label}` : t('Explore in graph')}
        sx={{
          'display': 'block',
          'height': miniHeight,
          'borderRadius': 1,
          'color': 'inherit',
          'textDecoration': 'none',
          // The global `:focus { outline: 0 }` would hide the keyboard focus: the ring is drawn here.
          '&:focus-visible': {
            outline: `2px solid ${theme.palette.primary.main}`,
            outlineOffset: 2,
          },
        }}
      >
        <Box
          sx={{
            'position': 'relative',
            'height': '100%',
            'minHeight': 200,
            'overflow': 'hidden',
            'borderRadius': 1,
            'border': `1px solid ${theme.palette.divider}`,
            'cursor': 'pointer',
            '& .react-flow, & .react-flow__pane': { pointerEvents: 'none' },
            '& .react-flow__node': { pointerEvents: 'all' },
          }}
        >
          {canvas}
        </Box>
      </Box>
    );
  }

  return (
    <Stack spacing={1.5} sx={{ height }}>
      {variant === 'page' && toolbar}
      {interactive && shortcutsDialog}
      {notice && (
        <Alert
          severity="info"
          title={notice}
          action={<Button type="button" priority="secondary" size="sm" onClick={() => setNotice(null)}>{t('Dismiss')}</Button>}
        />
      )}
      {pathFrom && <Alert severity="info" title={t('Select the destination node to find the shortest path')} />}
      <Box
        ref={wrapper}
        onKeyDown={onKeyDown}
        sx={{
          position: 'relative',
          flexGrow: 1,
          minHeight: 420,
          overflow: 'hidden',
          borderRadius: 1,
          border: `1px solid ${theme.palette.divider}`,
        }}
      >
        {canvas}
        {interactive && lassoOn && !empty && (
          <div
            data-testid="graph-lasso"
            aria-label={t(LASSO_LABEL)}
            className="absolute inset-0"
            style={{
              cursor: 'crosshair',
              zIndex: 5,
              touchAction: 'none',
            }}
            onPointerDown={lassoStart}
            onPointerMove={lassoMove}
            onPointerUp={lassoEnd}
            onPointerCancel={() => setLasso(null)}
          >
            {lasso && lasso.length > 1 && (
              <svg className="absolute inset-0 h-full w-full" aria-hidden style={{ pointerEvents: 'none' }}>
                <polygon
                  points={lassoOutline()}
                  fill={alpha(theme.palette.primary.main, 0.08)}
                  stroke={theme.palette.primary.main}
                  strokeWidth={1.5}
                  strokeDasharray="6 4"
                />
              </svg>
            )}
          </div>
        )}
        {loading && (
          <div
            className="absolute"
            style={{
              left: 12,
              top: 12,
            }}
            role="status"
            aria-label={t('Loading')}
          >
            <Spinner size="sm" />
          </div>
        )}
        {variant === 'embedded' && state.focus && state.nodes[state.focus] && (
          <div className="absolute right-2 top-2">
            <Tooltip>
              <TooltipTrigger asChild>
                <IconButton
                  asChild
                  priority="secondary"
                  icon={<OpenInFullOutlined fontSize="small" />}
                  aria-label={t('Open in full screen')}
                >
                  <Link to={graphHref(state.nodes[state.focus].node_type, state.nodes[state.focus].node_entity_id)} />
                </IconButton>
              </TooltipTrigger>
              <TooltipContent>{t('Open in full screen')}</TooltipContent>
            </Tooltip>
          </div>
        )}
        {!empty && renderLegend()}
        {(!empty || allHidden) && canvasToolbar(sideOffset)}
        {panel}
        {contextMenu}
      </Box>
    </Stack>
  );
};

const GraphExplorer = (props: GraphExplorerProps) => (
  <ReactFlowProvider>
    <GraphExplorerInner {...props} />
  </ReactFlowProvider>
);

export default GraphExplorer;
