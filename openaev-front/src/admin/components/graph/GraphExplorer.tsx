import {
  CenterFocusStrongOutlined,
  CloseOutlined,
  DownloadOutlined,
  LinkOutlined,
  OpenInFullOutlined,
  OpenInNewOutlined,
  PushPinOutlined,
  RouteOutlined,
  UndoOutlined,
  VisibilityOffOutlined,
} from '@mui/icons-material';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  FormControlLabel,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
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
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';

import { fetchGraphNeighbourhood, fetchGraphPath } from '../../../actions/graph/graph-actions';
import { useFormatter } from '../../../components/i18n';
import { NODE_LABEL_OFFSET, NODE_WIDTH } from './graph-edge-geometry';
import NODE_ICON from './graph-icons';
import { type GraphLayout, layoutGraph } from './graph-layout';
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
  nodeHref,
  RESULT_LABEL,
} from './graph-model';
import {
  DEFAULT_FILTERS,
  failedHighlights,
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
}

interface History { past: GraphState[] }

const RESULT_CHIP_COLOUR: Record<GraphResult, 'error' | 'success' | 'warning'> = {
  FAILED: 'error',
  SUCCESS: 'success',
  PENDING: 'warning',
};

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
  const interactive = variant !== 'mini';

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
  const positions = useMemo(() => layoutGraph(layout, shownNodes, shownEdges, state.focus), [layout, shownNodes, shownEdges, state.focus]);

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
      dimmed: (matches.size > 0 && !matches.has(n.node_id)) || (state.path.length > 0 && !pathNodes.has(n.node_id)),
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
    }
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
        strokeWidth: isPath || isFailed ? 2 : 1,
        opacity: state.path.length > 0 && !isPath ? 0.2 : 1,
      },
    };
  });

  useEffect(() => {
    const id = window.setTimeout(() => fitView({
      padding: 0.15,
      duration: 300,
    }), 50);
    return () => window.clearTimeout(id);
  }, [layout, shownNodes.length, fitView]);

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

  const legend = (
    <Stack
      direction="row"
      spacing={1.5}
      useFlexGap
      sx={{
        alignItems: 'center',
        flexWrap: 'wrap',
      }}
    >
      {ALL_NODE_TYPES.map((type) => {
        const Icon = NODE_ICON[type];
        return (
          <Stack key={type} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <Icon />
            <Typography variant="caption">{t(NODE_TYPE_LABEL[type])}</Typography>
          </Stack>
        );
      })}
      <Divider orientation="vertical" flexItem />
      {ALL_RESULTS.map((result) => {
        const colours: Record<GraphResult, string> = {
          FAILED: theme.palette.error.main,
          PENDING: theme.palette.warning.main,
          SUCCESS: theme.palette.success.main,
        };
        return (
          <Stack key={result} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <Box sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              border: `2px solid ${colours[result]}`,
            }}
            />
            <Typography variant="caption">{t(RESULT_LABEL[result])}</Typography>
          </Stack>
        );
      })}
    </Stack>
  );

  const toolbar = (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack
        direction="row"
        spacing={1.5}
        useFlexGap
        sx={{
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <TextField
          size="small"
          placeholder={t('Search in the graph')}
          value={filters.search}
          onChange={e => setFilters({
            ...filters,
            search: e.target.value,
          })}
          sx={{ width: 220 }}
          slotProps={{ htmlInput: { 'aria-label': t('Search in the graph') } }}
        />
        <Select
          size="small"
          multiple
          value={filters.nodeTypes}
          onChange={e => setFilters({
            ...filters,
            nodeTypes: e.target.value as GraphNodeType[],
          })}
          renderValue={v => `${t('Entities')} (${(v as GraphNodeType[]).length}/${ALL_NODE_TYPES.length})`}
          inputProps={{ 'aria-label': t('Entities') }}
        >
          {ALL_NODE_TYPES.map(type => (
            <MenuItem key={type} value={type}>{t(NODE_TYPE_LABEL[type])}</MenuItem>
          ))}
        </Select>
        <Select
          size="small"
          multiple
          value={filters.edgeTypes}
          onChange={e => setFilters({
            ...filters,
            edgeTypes: e.target.value as GraphEdgeType[],
          })}
          renderValue={v => `${t('Relations')} (${(v as GraphEdgeType[]).length}/${ALL_EDGE_TYPES.length})`}
          inputProps={{ 'aria-label': t('Relations') }}
        >
          {ALL_EDGE_TYPES.map(type => (
            <MenuItem key={type} value={type}>{t(EDGE_TYPE_LABEL[type])}</MenuItem>
          ))}
        </Select>
        <Stack direction="row" spacing={0.5}>
          {ALL_RESULTS.map(result => (
            <Chip
              key={result}
              size="small"
              label={t(RESULT_LABEL[result])}
              color={filters.results.includes(result) ? RESULT_CHIP_COLOUR[result] : 'default'}
              variant={filters.results.includes(result) ? 'filled' : 'outlined'}
              onClick={() => setFilters({
                ...filters,
                results: toggle(filters.results, result),
              })}
            />
          ))}
        </Stack>
        <FormControlLabel
          control={(
            <Switch
              size="small"
              checked={filters.focusMode}
              onChange={e => setFilters({
                ...filters,
                focusMode: e.target.checked,
              })}
            />
          )}
          label={t('Focus mode')}
        />
        <FormControlLabel
          control={<Switch size="small" checked={showFailed} onChange={e => setShowFailed(e.target.checked)} />}
          label={t('Highlight failed paths')}
        />
        <Box sx={{ flexGrow: 1 }} />
        <ToggleButtonGroup
          size="small"
          exclusive
          value={layout}
          onChange={(_, value: GraphLayout | null) => {
            if (value) {
              setLayout(value);
              onLayoutChange?.(value);
            }
          }}
          aria-label={t('Graph layout')}
        >
          <ToggleButton value="force">{t('Force')}</ToggleButton>
          <ToggleButton value="hierarchical">{t('Hierarchical')}</ToggleButton>
          <ToggleButton value="radial">{t('Radial')}</ToggleButton>
        </ToggleButtonGroup>
        <Tooltip title={t('Undo')}>
          <span>
            <IconButton size="small" onClick={undo} disabled={history.past.length === 0} aria-label={t('Undo')}>
              <UndoOutlined fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title={t('Export as PNG')}>
          <IconButton size="small" onClick={exportPng} aria-label={t('Export as PNG')}>
            <DownloadOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Copy a link to this view')}>
          <IconButton size="small" onClick={copyLink} aria-label={t('Copy a link to this view')}>
            <LinkOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
      <Box sx={{ mt: 1 }}>{legend}</Box>
    </Paper>
  );

  const panel = selectedNode && interactive && (
    <Drawer
      anchor="right"
      variant="persistent"
      open={Boolean(selectedNode)}
      slotProps={{
        paper: {
          sx: {
            position: 'absolute',
            width: 340,
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
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        {NODE_ICON[selectedNode.node_type]()}
        <Typography variant="h6" sx={{ flexGrow: 1 }} noWrap>{selectedNode.node_label}</Typography>
        <IconButton size="small" onClick={() => setSelected(null)} aria-label={t('Close')}>
          <CloseOutlined fontSize="small" />
        </IconButton>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        {t(NODE_TYPE_LABEL[selectedNode.node_type])}
        {selectedNode.node_subtype ? ` · ${selectedNode.node_subtype}` : ''}
      </Typography>
      <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
        <Chip size="small" color={selectedNode.node_failed_count > 0 ? 'error' : 'default'} label={`${t('Failed expectations')}: ${selectedNode.node_failed_count}`} />
        <Chip size="small" label={`${t('Findings')}: ${selectedNode.node_finding_count}`} />
      </Stack>
      <Stack spacing={1} sx={{ mt: 2 }}>
        {nodeHref(selectedNode) && (
          <Button component={Link} to={nodeHref(selectedNode) as string} variant="outlined" size="small" startIcon={<OpenInNewOutlined />}>
            {t('Open')}
          </Button>
        )}
        <Button variant="outlined" size="small" startIcon={<CenterFocusStrongOutlined />} onClick={() => refocus(selectedNode)}>
          {t('Pivot on this node')}
        </Button>
        <Button variant="outlined" size="small" startIcon={<RouteOutlined />} onClick={() => setPathFrom(selectedNode.node_id)} disabled={pathFrom === selectedNode.node_id}>
          {pathFrom === selectedNode.node_id ? t('Select the destination node') : t('Find a path from here')}
        </Button>
        <Stack direction="row" spacing={1}>
          <Button size="small" startIcon={<PushPinOutlined />} onClick={() => commit(s => togglePin(s, selectedNode.node_id))}>
            {state.pinned.includes(selectedNode.node_id) ? t('Unpin') : t('Pin')}
          </Button>
          <Button
            size="small"
            startIcon={<VisibilityOffOutlined />}
            onClick={() => {
              commit(s => hideNode(s, selectedNode.node_id));
              setSelected(null);
            }}
          >
            {t('Hide')}
          </Button>
        </Stack>
        {nodeActions.filter(a => a.appliesTo(selectedNode)).map(action => (
          <Button key={action.key} variant="outlined" size="small" startIcon={action.icon} onClick={() => action.onClick(selectedNode)}>
            {action.label}
          </Button>
        ))}
      </Stack>
      <Typography variant="subtitle2" sx={{ mt: 2 }}>{t('Expand by relation')}</Typography>
      <Stack
        direction="row"
        spacing={0.5}
        useFlexGap
        sx={{
          mt: 0.5,
          flexWrap: 'wrap',
        }}
      >
        {ALL_EDGE_TYPES.map(type => (
          <Chip key={type} size="small" variant="outlined" label={t(EDGE_TYPE_LABEL[type])} onClick={() => expand(selectedNode, [type])} />
        ))}
      </Stack>
      {state.truncatedAt.includes(selectedNode.node_id) && (
        <Button size="small" sx={{ mt: 1 }} onClick={() => expand(selectedNode, [], 200)}>{t('Load more')}</Button>
      )}
      <Typography variant="subtitle2" sx={{ mt: 2 }}>{t('Neighbours')}</Typography>
      <List dense sx={{ overflowY: 'auto' }}>
        {neighbours.map(({ node, type, result }) => (
          <ListItemButton key={`${type}-${node.node_id}`} onClick={() => setSelected(node.node_id)}>
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

  return (
    <Stack spacing={1.5} sx={{ height }}>
      {variant === 'page' && toolbar}
      {notice && <Alert severity="info" onClose={() => setNotice(null)}>{notice}</Alert>}
      {pathFrom && <Alert severity="info">{t('Select the destination node to find the shortest path')}</Alert>}
      <Paper
        variant="outlined"
        ref={wrapper}
        sx={{
          position: 'relative',
          flexGrow: 1,
          minHeight: variant === 'mini' ? 200 : 420,
          overflow: 'hidden',
          cursor: variant === 'mini' ? 'pointer' : 'default',
        }}
        onClick={variant === 'mini' ? onOpen : undefined}
      >
        {empty ? (
          <Stack sx={{
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
          }}
          >
            <Typography color="text.secondary">{t('Nothing to show in the graph yet')}</Typography>
          </Stack>
        ) : (
          <ReactFlow
            nodes={flowNodes}
            edges={flowEdges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            proOptions={proOptions}
            colorMode={theme.palette.mode}
            nodesDraggable={interactive}
            nodesConnectable={false}
            elementsSelectable={interactive}
            panOnDrag={interactive}
            zoomOnScroll={interactive}
            zoomOnDoubleClick={false}
            minZoom={0.1}
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
            fitView
          >
            <Background />
            {interactive && <Controls showInteractive={false} />}
            {variant === 'page' && (
              <MiniMap
                pannable
                zoomable
                bgColor={theme.palette.background.paper}
                maskColor={alpha(theme.palette.background.default, 0.6)}
                nodeColor={n => ((n.data as GraphEntityNodeType['data']).node.node_failed_count > 0 ? theme.palette.error.main : theme.palette.primary.main)}
                nodeStrokeWidth={0}
                nodeBorderRadius={12}
              />
            )}
          </ReactFlow>
        )}
        {loading && (
          <Box sx={{
            position: 'absolute',
            top: 12,
            left: 12,
          }}
          >
            <CircularProgress size={20} />
          </Box>
        )}
        {variant === 'embedded' && state.focus && state.nodes[state.focus] && (
          <Tooltip title={t('Open in full screen')}>
            <IconButton
              component={Link}
              to={graphHref(state.nodes[state.focus].node_type, state.nodes[state.focus].node_entity_id)}
              sx={{
                position: 'absolute',
                top: 8,
                right: 8,
              }}
              aria-label={t('Open in full screen')}
            >
              <OpenInFullOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        {panel}
      </Paper>
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
