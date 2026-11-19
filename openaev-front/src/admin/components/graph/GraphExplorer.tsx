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
  DonutLargeOutlined,
  DownloadOutlined,
  FitScreenOutlined,
  KeyboardOutlined,
  LinkOutlined,
  OpenInFullOutlined,
  OpenInNewOutlined,
  PushPinOutlined,
  RouteOutlined,
  UndoOutlined,
  VisibilityOffOutlined,
  VisibilityOutlined,
} from '@mui/icons-material';
import { Box, Drawer, List, ListItemButton, ListItemText, Stack } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
  Background,
  Controls,
  type Edge,
  getNodesBounds,
  getViewportForBounds,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
} from '@xyflow/react';
import { toPng } from 'html-to-image';
import { type KeyboardEvent, type MouseEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { fetchGraphNeighbourhood, fetchGraphPath } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import { NODE_LABEL_OFFSET, NODE_WIDTH } from './graph-edge-geometry';
import { type GroupedNode, groupNeighbours, isGroupId, miniView } from './graph-grouping';
import NODE_ICON from './graph-icons';
import { entering, extendLayout, type GraphLayout, interpolate, layoutFrames, miniCardLayout, type Point } from './graph-layout';
import {
  ALL_EDGE_TYPES,
  ALL_NODE_TYPES,
  ALL_RESULTS,
  EDGE_TYPE_LABEL,
  type GraphEdgeType,
  graphHref,
  type GraphNodeOutput,
  type GraphNodeType,
  type GraphOutput,
  type GraphResult,
  NODE_TYPE_LABEL,
  NODE_TYPE_PLURAL,
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
  mergeSlice,
  pathEdges,
  showHidden,
  togglePin,
  ungroup,
  visibleGraph,
} from './graph-state';
import GraphEntityNode, { type GraphEntityNodeType } from './GraphEntityNode';
import GraphFloatingEdge from './GraphFloatingEdge';

const NODE_BOX_HEIGHT = NODE_LABEL_OFFSET + 20;
const PANEL_WIDTH = 340;
/** A static layout glides to its place in this time; the force layout plays its frames in about twice it. */
const MOVE_MS = 400;
const FRAME_MS = 24;
// Lowest zoom of the mini graph's fit: node labels are 10 px at zoom 1, so they never shrink below it.
const MINI_MIN_ZOOM = 1;

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

/** The keyboard shortcuts of the page graph, listed by the shortcuts dialog (as in OpenCTI). */
const SHORTCUTS: [string, string][] = [
  ['E', 'Expand the selected node'],
  ['P', 'Pin or unpin the selected node'],
  ['H', 'Hide the selected node'],
  ['F', 'Fit to screen'],
  ['L', 'Re-run the layout'],
  ['Ctrl+Z', 'Undo'],
  ['?', 'Show this list'],
];

/** Where the context menu opens (in the coordinates of the canvas) and what it was opened on. */
interface ContextMenuAnchor {
  x: number;
  y: number;
  nodeId?: string;
  edgeId?: string;
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
  const { t } = useFormatter();
  const { fitView } = useReactFlow();
  const wrapper = useRef<HTMLDivElement>(null);
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
  // Bumped to lay the whole graph out again (a layout button, Re-run the layout, a new initial slice);
  // every other change (an expand, a hide, a group opened) extends the layout on screen in place.
  const [layoutRun, setLayoutRun] = useState(0);
  const interactive = variant !== 'mini';
  // The mini graph never fits below MINI_MIN_ZOOM: its labels stay readable (10 px at zoom 1) and an
  // outer ring that does not fit is clipped by the card, the full graph being one click away.
  const fitOptions = useMemo(() => (variant === 'mini'
    ? {
        padding: 0.15,
        minZoom: MINI_MIN_ZOOM,
      }
    : {
        padding: 0.15,
        ...(fitMinZoom ? { minZoom: fitMinZoom } : {}),
      }), [variant, fitMinZoom]);

  useEffect(() => {
    setState(fromSlice(initial));
    setHistory({ past: [] });
    setSelected(selectFocus ? initial.graph_focus ?? null : null);
    setLayoutRun(run => run + 1);
  }, [initial, selectFocus]);

  const commit = useCallback((next: (s: GraphState) => GraphState) => {
    setState((current) => {
      setHistory(h => ({ past: [...h.past.slice(-29), current] }));
      return next(current);
    });
  }, []);

  const undo = () => {
    setHistory((h) => {
      if (h.past.length === 0) {
        return h;
      }
      setState(h.past[h.past.length - 1]);
      return { past: h.past.slice(0, -1) };
    });
  };

  const { nodes: shownNodes, edges: shownEdges, matches } = useMemo(() => visibleGraph(state, filters), [state, filters]);
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
  // The positions on screen: read by the layout to extend it in place.
  const shown = useRef<Map<string, Point>>(new Map());
  const laidOut = useRef<string>('');
  // Set when the view must frame the graph once the move has landed (a load, a layout, new nodes).
  const fitPending = useRef(true);
  // The frames toward the current layout. A whole layout (the force simulation's steps, or one static
  // placement) runs on a layout button, Re-run the layout and a new slice; an expand, a hide or a group
  // opened keeps every node where it is and places the newcomers next to their neighbour (an OpenCTI
  // investigation never jumps). The mini card draws its own compact two-hop block.
  const frames = useMemo(() => {
    if (variant === 'mini') {
      return [miniCardLayout(drawnNodes, drawnEdges, state.focus ?? '')];
    }
    const key = `${layout}|${layoutRun}`;
    if (laidOut.current !== key || shown.current.size === 0) {
      laidOut.current = key;
      fitPending.current = true;
      return layoutFrames(layout, drawnNodes, drawnEdges, state.focus);
    }
    if (drawnNodes.some(n => !shown.current.has(n.node_id))) {
      fitPending.current = true;
    }
    return [extendLayout(shown.current, drawnNodes, drawnEdges)];
  }, [variant, layout, layoutRun, drawnNodes, drawnEdges, state.focus]);
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
      const progress = Math.min(1, (now - start) / duration);
      const eased = 1 - (1 - progress) ** 3;
      // The first leg glides from the screen to the first frame, then the frames play in order.
      const legs = steps.length;
      const at = eased * legs;
      const leg = Math.min(legs - 1, Math.floor(at));
      const legFrom = leg === 0 ? from : steps[leg - 1];
      const next = interpolate(legFrom, steps[leg], at - leg);
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
  }, [frames, target, drawnEdges, variant, frame]);
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
    data: {
      node: n,
      focused: n.node_id === state.focus || n.node_id === selected,
      pinned: state.pinned.includes(n.node_id),
      matched: matches.has(n.node_id),
      dimmed: (matches.size > 0 && !matches.has(n.node_id))
        || (state.path.length > 0 && !pathNodes.has(n.node_id))
        || (ring !== null && !ring.nodeIds.has(n.node_id)),
      highlighted: (failed.nodeIds.has(n.node_id) || (showFailed && Boolean(n.node_group_members) && n.node_result === 'FAILED')) && n.node_type !== 'INJECT',
      truncated: state.truncatedAt.includes(n.node_id),
      groupCount: n.node_group_members?.length,
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

  const fitToScreen = () => fitView({
    ...fitOptions,
    duration: 300,
  });

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

  const copyLink = () => {
    const focusNode = state.focus ? state.nodes[state.focus] : undefined;
    const href = focusNode ? graphHref(focusNode.node_type, focusNode.node_entity_id) : '/admin/graph';
    const url = new URL(href, window.location.origin);
    url.searchParams.set('layout', layout);
    navigator.clipboard?.writeText(url.toString());
    setNotice(t('Link copied to the clipboard'));
  };

  const selectedNode = selected ? state.nodes[selected] : undefined;
  const neighbours = useMemo(() => {
    if (!selectedNode) {
      return [] as {
        node: GraphNodeOutput;
        type: GraphEdgeType;
        result?: GraphResult | null;
      }[];
    }
    return Object.values(state.edges)
      .filter(e => e.edge_source === selectedNode.node_id || e.edge_target === selectedNode.node_id)
      .map((e) => {
        const other = e.edge_source === selectedNode.node_id ? e.edge_target : e.edge_source;
        return {
          node: state.nodes[other],
          type: e.edge_type,
          result: e.edge_result,
        };
      })
      .filter(n => n.node);
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
  const legend = (
    <div className="flex flex-wrap items-center gap-1" role="group" aria-label={t('Legend')}>
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
      <span aria-hidden className="mx-2 h-4 w-px bg-[var(--border-default-primary)]" />
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
        <div style={{ flexGrow: 1 }} />
        {/* Each layout button runs its layout from scratch (a second click on the active one re-runs
            it, as OpenCTI's), then the view is framed; every button says what it does on hover. */}
        <div className="flex items-center" role="group" aria-label={t('Graph layout')}>
          {(['force', 'hierarchical', 'radial'] as GraphLayout[]).map(value => (
            <span key={value}>{iconAction(t(LAYOUT_LABEL[value]), LAYOUT_ICON[value], () => relayout(value), false, layout === value)}</span>
          ))}
          {iconAction(t('Re-run the layout'), <AutorenewOutlined fontSize="small" />, () => relayout())}
          {iconAction(t('Fit to screen'), <FitScreenOutlined fontSize="small" />, fitToScreen)}
        </div>
        {iconAction(t('Show hidden nodes'), <VisibilityOutlined fontSize="small" />, () => commit(showHidden), state.hidden.length === 0)}
        {iconAction(t('Undo'), <UndoOutlined fontSize="small" />, undo, history.past.length === 0)}
        {iconAction(t('Export as PNG'), <DownloadOutlined fontSize="small" />, exportPng)}
        {iconAction(t('Copy a link to this view'), <LinkOutlined fontSize="small" />, copyLink)}
        {iconAction(t('Keyboard shortcuts'), <KeyboardOutlined fontSize="small" />, () => setShortcutsOpen(true))}
      </div>
      <div className="mt-2">{legend}</div>
    </Paper>
  );

  const panelOpen = Boolean(selectedNode && interactive);

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
        {ALL_EDGE_TYPES.map(type => (
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
  const openMenu = (event: MouseEvent | globalThis.MouseEvent, on: Pick<ContextMenuAnchor, 'nodeId' | 'edgeId'>) => {
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
  const nodeMenu = (node: GraphNodeOutput) => {
    const href = nodeHref(node);
    const present = ALL_NODE_TYPES.filter(type => type !== node.node_type);
    return [
      <MenuLabel key="label">{`${t(NODE_TYPE_LABEL[node.node_type])}: ${node.node_label}`}</MenuLabel>,
      menuItem('expand', t('Expand'), <CenterFocusStrongOutlined fontSize="small" />, () => expand(node)),
      <MenuSub key="by-relation">
        <MenuSubTrigger>{t('Expand by relation')}</MenuSubTrigger>
        <MenuSubContent>
          {ALL_EDGE_TYPES.map(type => menuItem(type, t(EDGE_TYPE_LABEL[type]), null, () => expand(node, [type])))}
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
      ...(href ? [menuItem('open', t('Open'), <OpenInNewOutlined fontSize="small" />, () => navigate(href))] : []),
      ...nodeActions.filter(a => a.appliesTo(node)).map(a => menuItem(a.key, a.label, a.icon, () => a.onClick(node))),
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
  // The controls and the mini-map move left of the open side panel instead of sitting under it.
  const sideOffset = panelOpen ? PANEL_WIDTH + 8 : 0;

  const canvas = empty
    ? (
        <div className="flex h-full items-center justify-center">
          <Text variant="content-compact" className="text-default-secondary">{t('Nothing to show in the graph yet')}</Text>
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
          onPaneClick={() => setMenu(null)}
          panOnScroll={false}
          fitView
          fitViewOptions={fitOptions}
        >
          <Background />
          {interactive && <Controls showInteractive={false} style={{ marginLeft: 8 }} />}
          {variant === 'page' && (
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
          height,
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
        {panel}
        {contextMenu}
      </Box>
      {variant === 'embedded' && legend}
    </Stack>
  );
};

const GraphExplorer = (props: GraphExplorerProps) => (
  <ReactFlowProvider>
    <GraphExplorerInner {...props} />
  </ReactFlowProvider>
);

export default GraphExplorer;
