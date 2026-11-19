import {
  Alert,
  Button,
  ButtonGroup,
  ButtonGroupItem,
  Chip,
  IconButton,
  Menu,
  MenuContent,
  MenuItem,
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
  BubbleChartOutlined,
  CenterFocusStrongOutlined,
  CloseOutlined,
  DonutLargeOutlined,
  DownloadOutlined,
  LinkOutlined,
  OpenInFullOutlined,
  OpenInNewOutlined,
  PushPinOutlined,
  RouteOutlined,
  UndoOutlined,
  VisibilityOffOutlined,
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
import { type MouseEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';

import { fetchGraphNeighbourhood, fetchGraphPath } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import { NODE_LABEL_OFFSET, NODE_WIDTH } from './graph-edge-geometry';
import NODE_ICON from './graph-icons';
import { entering, type GraphLayout, interpolate, layoutFrames, miniLayout, type Point } from './graph-layout';
import {
  ALL_EDGE_TYPES,
  ALL_NODE_TYPES,
  ALL_RESULTS,
  EDGE_TYPE_LABEL,
  type GraphEdgeType,
  graphHref,
  type GraphNodeOutput,
  type GraphOutput,
  type GraphResult,
  NODE_TYPE_LABEL,
  nodeHref,
  RESULT_LABEL,
} from './graph-model';
import {
  DEFAULT_FILTERS,
  failedHighlights,
  firstRing,
  fromSlice,
  type GraphFilters,
  type GraphState,
  hideNode,
  mergeSlice,
  pathEdges,
  togglePin,
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

const LAYOUT_LABEL: Record<GraphLayout, string> = {
  force: 'Force',
  hierarchical: 'Hierarchical',
  radial: 'Radial',
};

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
}: GraphExplorerProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { fitView } = useReactFlow();
  const wrapper = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<GraphState>(() => fromSlice(initial));
  const [history, setHistory] = useState<History>({ past: [] });
  const [filters, setFilters] = useState<GraphFilters>(DEFAULT_FILTERS);
  const [layout, setLayout] = useState<GraphLayout>(initialLayout);
  const [selected, setSelected] = useState<string | null>(initial.graph_focus ?? null);
  const [pathFrom, setPathFrom] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [showFailed, setShowFailed] = useState(true);
  const [hovered, setHovered] = useState<string | null>(null);
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
    setSelected(initial.graph_focus ?? null);
  }, [initial]);

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
  // The frames toward the current layout: the force simulation's steps, or one static placement.
  // The mini card draws its own short strip (focus centred, neighbours on two rows) so it fits at zoom 1.
  const frames = useMemo(
    () => (variant === 'mini' ? [miniLayout(shownNodes, state.focus)] : layoutFrames(layout, shownNodes, shownEdges, state.focus)),
    [variant, layout, shownNodes, shownEdges, state.focus],
  );
  const target = frames[frames.length - 1];
  // What is on screen. A layout switch, an expand or a hide moves the nodes from where they are to
  // where they go: the force layout plays its simulation frames (the forces settle visibly), a static
  // layout glides; a node entering the graph grows out of its neighbour already on screen.
  const [positions, setPositions] = useState<Map<string, Point>>(target);
  const shown = useRef<Map<string, Point>>(target);
  useEffect(() => {
    const from = entering(shown.current, target, shownEdges);
    const steps = layout === 'force' && frames.length > 1 ? frames : [target];
    const duration = layout === 'force' ? Math.max(MOVE_MS, steps.length * FRAME_MS) : MOVE_MS;
    if (prefersReducedMotion() || variant === 'mini') {
      shown.current = target;
      setPositions(target);
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
      }
    };
    raf = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(raf);
  }, [frames, target, shownEdges, layout, variant]);
  // Hover dimming: the hovered node's first ring stays, the rest fades.
  const ring = useMemo(() => (hovered ? firstRing(hovered, shownEdges) : null), [hovered, shownEdges]);

  const flowNodes: GraphEntityNodeType[] = shownNodes.map(n => ({
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
      highlighted: failed.nodeIds.has(n.node_id) && n.node_type !== 'INJECT',
      truncated: state.truncatedAt.includes(n.node_id),
    },
  }));

  const flowEdges: Edge[] = shownEdges.map((e) => {
    const isFailed = failed.edgeIds.has(e.edge_id);
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
      // The relation name only where it is read: on a path, or around the selected node (a failed
      // edge already says it by its colour; a label on each of them piles up between close nodes).
      label: interactive && (isPath || (selected !== null && (e.edge_source === selected || e.edge_target === selected)))
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

  useEffect(() => {
    // Fit once the move has landed, so the view frames the settled layout.
    const id = window.setTimeout(() => fitView({
      ...fitOptions,
      duration: 300,
    }), layout === 'force' ? Math.max(MOVE_MS, frames.length * FRAME_MS) + 50 : MOVE_MS + 50);
    return () => window.clearTimeout(id);
  }, [layout, shownNodes.length, fitView, frames.length, fitOptions]);

  const expand = async (node: GraphNodeOutput, edges: GraphEdgeType[] = [], limit = 50) => {
    setLoading(true);
    try {
      const slice = await fetchGraphNeighbourhood(node.node_type, node.node_entity_id, edges, limit);
      commit(s => mergeSlice(s, slice, node.node_id));
    } finally {
      setLoading(false);
    }
  };

  const refocus = async (node: GraphNodeOutput) => {
    setLoading(true);
    try {
      const slice = await fetchGraphNeighbourhood(node.node_type, node.node_entity_id);
      commit(() => fromSlice(slice));
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

  const legend = (
    <div className="flex flex-wrap items-center gap-3">
      {ALL_NODE_TYPES.map((type) => {
        const Icon = NODE_ICON[type];
        return (
          <span key={type} className="inline-flex items-center gap-1">
            <Icon />
            <Text variant="content-caption">{t(NODE_TYPE_LABEL[type])}</Text>
          </span>
        );
      })}
      <span aria-hidden className="h-4 w-px bg-[var(--border-default-primary)]" />
      {ALL_RESULTS.map(result => (
        <span key={result} className="inline-flex items-center gap-1">
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

  const iconAction = (label: string, icon: ReactNode, onClick: () => void, disabled = false) => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>
          <IconButton priority="tertiary" icon={icon} aria-label={label} onClick={onClick} disabled={disabled} />
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
        <ButtonGroup
          value={layout}
          onValueChange={(value) => {
            const next = value as GraphLayout;
            setLayout(next);
            onLayoutChange?.(next);
          }}
          aria-label={t('Graph layout')}
        >
          {(['force', 'hierarchical', 'radial'] as GraphLayout[]).map(value => (
            <ButtonGroupItem key={value} value={value} icon={LAYOUT_ICON[value]} aria-label={t(LAYOUT_LABEL[value])} />
          ))}
        </ButtonGroup>
        {iconAction(t('Undo'), <UndoOutlined fontSize="small" />, undo, history.past.length === 0)}
        {iconAction(t('Export as PNG'), <DownloadOutlined fontSize="small" />, exportPng)}
        {iconAction(t('Copy a link to this view'), <LinkOutlined fontSize="small" />, copyLink)}
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
        <Button type="button" priority="secondary" startIcon={<CenterFocusStrongOutlined fontSize="small" />} onClick={() => refocus(selectedNode)}>
          {t('Pivot on this node')}
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

  const empty = shownNodes.length === 0;
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
                const node = state.nodes[n.id];
                if (pathFrom && pathFrom !== n.id) {
                  findPath(state.nodes[pathFrom], node);
                  return;
                }
                setSelected(n.id);
              }
            : undefined}
          onNodeDoubleClick={interactive ? (_, n) => expand(state.nodes[n.id]) : undefined}
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
