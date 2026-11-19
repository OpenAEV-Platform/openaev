import {
  ALL_EDGE_TYPES,
  ALL_NODE_TYPES,
  ALL_RESULTS,
  type GraphEdgeOutput,
  type GraphEdgeType,
  type GraphNodeOutput,
  type GraphNodeType,
  type GraphOutput,
  type GraphResult,
} from './graph-model';

/** What the explorer holds: every slice loaded so far, merged, plus the user's view choices. */
export interface GraphState {
  nodes: Record<string, GraphNodeOutput>;
  edges: Record<string, GraphEdgeOutput>;
  focus?: string | null;
  hidden: string[];
  pinned: string[];
  path: string[];
  truncatedAt: string[];
  /** Group nodes the user opened: their members are drawn one by one (see graph-grouping). */
  ungrouped: string[];
}

export interface GraphFilters {
  nodeTypes: GraphNodeType[];
  edgeTypes: GraphEdgeType[];
  results: GraphResult[];
  search: string;
  /** Show only the focus and its direct neighbours. */
  focusMode: boolean;
}

export const EMPTY_STATE: GraphState = {
  nodes: {},
  edges: {},
  focus: null,
  hidden: [],
  pinned: [],
  path: [],
  truncatedAt: [],
  ungrouped: [],
};

export const DEFAULT_FILTERS: GraphFilters = {
  nodeTypes: ALL_NODE_TYPES,
  edgeTypes: ALL_EDGE_TYPES,
  results: ALL_RESULTS,
  search: '',
  focusMode: false,
};

const RANK: Record<GraphResult, number> = {
  SUCCESS: 0,
  PENDING: 1,
  FAILED: 2,
};

export const worst = (a?: GraphResult | null, b?: GraphResult | null): GraphResult | null => {
  if (!a) {
    return b ?? null;
  }
  if (!b) {
    return a;
  }
  return RANK[a] >= RANK[b] ? a : b;
};

/**
 * Merges a slice into the state (expand, pivot, load more). Counts and results of a node already
 * known are kept at their highest: a slice only ever sees part of a node's ring.
 */
export const mergeSlice = (state: GraphState, slice: GraphOutput, expandedFrom?: string | null): GraphState => {
  const nodes = { ...state.nodes };
  slice.graph_nodes.forEach((n) => {
    const previous = nodes[n.node_id];
    nodes[n.node_id] = previous
      ? {
          ...previous,
          node_failed_count: Math.max(previous.node_failed_count, n.node_failed_count),
          node_finding_count: Math.max(previous.node_finding_count, n.node_finding_count),
          node_result: worst(previous.node_result, n.node_result),
        }
      : n;
  });
  const edges = { ...state.edges };
  slice.graph_edges.forEach((e) => {
    edges[e.edge_id] = e;
  });
  const truncatedAt = slice.graph_truncated && expandedFrom
    ? Array.from(new Set([...state.truncatedAt, expandedFrom]))
    : state.truncatedAt.filter(id => id !== expandedFrom);
  const added = new Set(slice.graph_nodes.map(n => n.node_id));
  return {
    ...state,
    nodes,
    edges,
    focus: state.focus ?? slice.graph_focus ?? null,
    hidden: state.hidden.filter(id => !added.has(id)),
    path: slice.graph_path.length > 0 ? slice.graph_path : state.path,
    truncatedAt,
  };
};

/**
 * Expands a node in place, as an OpenCTI investigation: the slice is merged into the graph on
 * screen and the node becomes the focus; nothing already on screen is dropped. `nodeType` keeps only
 * the neighbours of one entity type (the context menu's Expand by entity type).
 */
export const expandSlice = (state: GraphState, slice: GraphOutput, nodeId: string, nodeType?: GraphNodeOutput['node_type']): GraphState => {
  const kept = nodeType
    ? {
        ...slice,
        graph_nodes: slice.graph_nodes.filter(n => n.node_id === nodeId || n.node_type === nodeType || state.nodes[n.node_id]),
      }
    : slice;
  const ids = new Set([...Object.keys(state.nodes), ...kept.graph_nodes.map(n => n.node_id)]);
  const merged = mergeSlice(state, {
    ...kept,
    graph_edges: kept.graph_edges.filter(e => ids.has(e.edge_source) && ids.has(e.edge_target)),
    graph_path: [],
  }, nodeId);
  return {
    ...merged,
    focus: nodeId,
  };
};

/** Shows every hidden node again. */
export const showHidden = (state: GraphState): GraphState => ({
  ...state,
  hidden: [],
});

/** Opens a group node: its members are drawn one by one. */
export const ungroup = (state: GraphState, groupId: string): GraphState => ({
  ...state,
  ungrouped: state.ungrouped.includes(groupId) ? state.ungrouped : [...state.ungrouped, groupId],
});

/** A fresh state from one slice: the landing view, or a new focus. */
export const fromSlice = (slice: GraphOutput): GraphState => mergeSlice(EMPTY_STATE, slice, slice.graph_focus ?? null);

export const hideNode = (state: GraphState, nodeId: string): GraphState => ({
  ...state,
  hidden: state.hidden.includes(nodeId) ? state.hidden : [...state.hidden, nodeId],
  pinned: state.pinned.filter(id => id !== nodeId),
});

export const togglePin = (state: GraphState, nodeId: string): GraphState => ({
  ...state,
  pinned: state.pinned.includes(nodeId) ? state.pinned.filter(id => id !== nodeId) : [...state.pinned, nodeId],
});

/** The nodes and edges to draw once filters, hidden nodes and focus mode are applied. */
export const visibleGraph = (state: GraphState, filters: GraphFilters) => {
  const hidden = new Set(state.hidden);
  const search = filters.search.trim().toLowerCase();
  const pinned = new Set(state.pinned);
  let nodes = Object.values(state.nodes).filter(n => !hidden.has(n.node_id)
    && (pinned.has(n.node_id) || n.node_id === state.focus || (
      filters.nodeTypes.includes(n.node_type)
      && (!n.node_result || filters.results.includes(n.node_result)))));
  let ids = new Set(nodes.map(n => n.node_id));
  let edges = Object.values(state.edges).filter(e => filters.edgeTypes.includes(e.edge_type)
    && ids.has(e.edge_source) && ids.has(e.edge_target));
  if (filters.focusMode && state.focus && ids.has(state.focus)) {
    const ring = new Set<string>([state.focus, ...state.pinned]);
    edges.forEach((e) => {
      if (e.edge_source === state.focus) ring.add(e.edge_target);
      if (e.edge_target === state.focus) ring.add(e.edge_source);
    });
    nodes = nodes.filter(n => ring.has(n.node_id));
    ids = new Set(nodes.map(n => n.node_id));
    edges = edges.filter(e => ids.has(e.edge_source) && ids.has(e.edge_target));
  }
  const matches = new Set(search
    ? nodes.filter(n => n.node_label.toLowerCase().includes(search)).map(n => n.node_id)
    : []);
  return {
    nodes,
    edges,
    matches,
  };
};

/** Node ids and edge ids along a failed chain: TARGETS edges whose result is FAILED and their ends. */
export const failedHighlights = (edges: GraphEdgeOutput[]) => {
  const nodeIds = new Set<string>();
  const edgeIds = new Set<string>();
  edges.filter(e => e.edge_result === 'FAILED').forEach((e) => {
    edgeIds.add(e.edge_id);
    nodeIds.add(e.edge_source);
    nodeIds.add(e.edge_target);
  });
  return {
    nodeIds,
    edgeIds,
  };
};

/**
 * The first ring of a node: the node, its neighbours and the edges between it and them. The
 * hover dimming keeps these at full opacity and fades the rest of the graph.
 */
export const firstRing = (nodeId: string, edges: GraphEdgeOutput[]) => {
  const nodeIds = new Set<string>([nodeId]);
  const edgeIds = new Set<string>();
  edges.forEach((e) => {
    if (e.edge_source === nodeId || e.edge_target === nodeId) {
      edgeIds.add(e.edge_id);
      nodeIds.add(e.edge_source);
      nodeIds.add(e.edge_target);
    }
  });
  return {
    nodeIds,
    edgeIds,
  };
};

/** The edges between consecutive nodes of a path, either direction. */
export const pathEdges = (path: string[], edges: GraphEdgeOutput[]) => {
  const pairs = new Set<string>();
  for (let i = 1; i < path.length; i += 1) {
    pairs.add(`${path[i - 1]}|${path[i]}`);
    pairs.add(`${path[i]}|${path[i - 1]}`);
  }
  return new Set(edges.filter(e => pairs.has(`${e.edge_source}|${e.edge_target}`)).map(e => e.edge_id));
};

/** The shareable URL state: focus, hidden, pinned and layout, nothing else. */
export const encodeFocus = (focus?: string | null) => {
  if (!focus) {
    return undefined;
  }
  const [type, ...rest] = focus.split(':');
  return `${type.toLowerCase()}:${rest.join(':')}`;
};
