import { formatConditionKeyLabel } from '../events/event-types';
import { collectEventFields, type OutputProviderEntry } from '../logic-flow-helpers';
import type { ActionMeta, EventMeta } from '../types';

// -- Layout design tokens (px) --
export const NODE_WIDTH = 248;
export const ACTION_NODE_HEIGHT = 112;
export const TRIGGER_NODE_HEIGHT = 76;

// Column / row separation. Denser when the chain is large (mirrors XTM One's
// node-count-driven `dynNodeSep` / `dynRankSep`) so big graphs stay compact.
const columnGap = (nodeCount: number) => (nodeCount > 14 ? 84 : 120);
const rowGap = (nodeCount: number) => (nodeCount > 14 ? 18 : 30);

export type LogicGraphNodeKind = 'action' | 'trigger';

export interface LogicGraphNode {
  id: string;
  kind: LogicGraphNodeKind;
  layer: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export type LogicGraphEdgeKind = 'real' | 'inferred';

export interface LogicGraphEdge {
  id: string;
  source: string;
  target: string;
  kind: LogicGraphEdgeKind;
  /** Finding type(s) an inferred edge carries (e.g. "Port"). Empty for real edges. */
  label?: string;
  /** Orthogonal SVG path in world coordinates. */
  path: string;
  labelX: number;
  labelY: number;
}

export interface LogicGraphBBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  width: number;
  height: number;
}

export interface LogicGraphLayout {
  nodes: LogicGraphNode[];
  edges: LogicGraphEdge[];
  bbox: LogicGraphBBox;
  nodeById: Record<string, LogicGraphNode>;
}

/** Minimal positioned box needed to route a connector (lets edges re-route while a node is dragged). */
export interface PositionedBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Orthogonal S-bend between two boxes: exit the source's right edge, run to a horizontal midpoint,
 * rise/drop, then enter the target's left edge. Pure so the graph container can re-route on drag.
 */
export const routeOrthogonalEdge = (source: PositionedBox, target: PositionedBox) => {
  const sx = source.x + source.width;
  const sy = source.y + source.height / 2;
  const tx = target.x;
  const ty = target.y + target.height / 2;
  const midX = sx + Math.max(24, (tx - sx) / 2);
  return {
    path: `M ${sx} ${sy} L ${midX} ${sy} L ${midX} ${ty} L ${tx} ${ty}`,
    labelX: midX,
    labelY: (sy + ty) / 2,
  };
};

interface BuildLayoutInput {
  actionMetas: Record<string, ActionMeta>;
  eventMetas: Record<string, EventMeta>;
  /** Inverted "output type -> provider actions" map (see buildOutputProvidersMap). */
  outputProviders: Record<string, OutputProviderEntry[]>;
}

interface RawEdge {
  source: string;
  target: string;
  kind: LogicGraphEdgeKind;
  label?: string;
}

/**
 * Inferred `producerAction -> trigger` links. The data model never stores which step produced a
 * finding, so we infer the link by intersecting each producer action's output types with the
 * fields a trigger listens on. Every matching producer yields one (dashed) edge - fan-in included.
 */
const buildInferredEdges = (
  eventMetas: Record<string, EventMeta>,
  outputProviders: Record<string, OutputProviderEntry[]>,
): RawEdge[] => {
  const edges: RawEdge[] = [];
  for (const [eventId, meta] of Object.entries(eventMetas)) {
    const fields = new Set(
      meta.formData.conditionGroups.flatMap(collectEventFields).filter(Boolean),
    );
    const typesByStep = new Map<string, Set<string>>();
    for (const field of fields) {
      for (const provider of outputProviders[field] ?? []) {
        const current = typesByStep.get(provider.stepId) ?? new Set<string>();
        current.add(field);
        typesByStep.set(provider.stepId, current);
      }
    }
    for (const [stepId, types] of typesByStep.entries()) {
      edges.push({
        source: stepId,
        target: eventId,
        kind: 'inferred',
        label: Array.from(types).map(formatConditionKeyLabel).join(', '),
      });
    }
  }
  return edges;
};

/** Real `trigger -> consumerAction` links, taken from each step's `step_condition_ids`. */
const buildRealEdges = (
  actionMetas: Record<string, ActionMeta>,
  eventMetas: Record<string, EventMeta>,
): RawEdge[] => {
  const edges: RawEdge[] = [];
  for (const [stepId, meta] of Object.entries(actionMetas)) {
    for (const condId of meta.step_condition_ids) {
      if (eventMetas[condId]) {
        edges.push({
          source: condId,
          target: stepId,
          kind: 'real',
        });
      }
    }
  }
  return edges;
};

/**
 * Break cycles by classifying back edges with a DFS and returning only the acyclic subset.
 *
 * Chained scenarios routinely contain feedback loops: an action gated by a trigger (real edge
 * `trigger -> action`) also produces a finding that same trigger listens on (inferred edge
 * `action -> trigger`), e.g. a NetExec step gated by `SMB_FOUND` that itself emits `port`. Left in,
 * such a loop makes longest-path layering relax up to `nodeCount` times, launching the looped nodes
 * far to the right and drawing a connector across the whole canvas. Back edges are still rendered
 * (they simply hook back one column) but must not drive layering.
 */
const removeBackEdges = (nodeIds: string[], edges: RawEdge[]): RawEdge[] => {
  const adjacency = new Map<string, RawEdge[]>();
  for (const id of nodeIds) adjacency.set(id, []);
  for (const edge of edges) adjacency.get(edge.source)?.push(edge);

  // 0 = unvisited, 1 = on the current DFS stack, 2 = fully explored.
  const state = new Map<string, 0 | 1 | 2>(nodeIds.map(id => [id, 0]));
  const acyclic: RawEdge[] = [];

  const visit = (start: string) => {
    const stack: Array<{
      node: string;
      edgeIndex: number;
    }> = [{
      node: start,
      edgeIndex: 0,
    }];
    state.set(start, 1);
    while (stack.length > 0) {
      const frame = stack[stack.length - 1];
      const outgoing = adjacency.get(frame.node) ?? [];
      if (frame.edgeIndex >= outgoing.length) {
        state.set(frame.node, 2);
        stack.pop();
        continue;
      }
      const edge = outgoing[frame.edgeIndex];
      frame.edgeIndex += 1;
      const targetState = state.get(edge.target);
      if (targetState === 1) continue; // back edge -> drop from the layering graph
      acyclic.push(edge);
      if (targetState === 0) {
        state.set(edge.target, 1);
        stack.push({
          node: edge.target,
          edgeIndex: 0,
        });
      }
    }
  };

  for (const id of nodeIds) {
    if (state.get(id) === 0) visit(id);
  }
  return acyclic;
};

/**
 * Longest-path layering over an already-acyclic edge set. Every edge points strictly left-to-right
 * (target layer >= source layer + 1), producing the alternating `Action -> Trigger -> Action` "waves".
 */
const computeLayers = (nodeIds: string[], acyclicEdges: RawEdge[]): Record<string, number> => {
  const layer: Record<string, number> = {};
  for (const id of nodeIds) layer[id] = 0;
  for (let iteration = 0; iteration < nodeIds.length; iteration += 1) {
    let changed = false;
    for (const edge of acyclicEdges) {
      const candidate = layer[edge.source] + 1;
      if (candidate > layer[edge.target]) {
        layer[edge.target] = candidate;
        changed = true;
      }
    }
    if (!changed) break;
  }
  return layer;
};

/**
 * Build the auto-laid-out causal graph (nodes + orthogonal connector paths + bounding box) from the
 * parsed action / event metadata. Pure and deterministic so it can be memoized behind a content
 * fingerprint (polling re-renders must not shuffle the layout).
 */
export const buildLogicGraphLayout = ({
  actionMetas,
  eventMetas,
  outputProviders,
}: BuildLayoutInput): LogicGraphLayout => {
  const kindById: Record<string, LogicGraphNodeKind> = {};
  for (const id of Object.keys(actionMetas)) kindById[id] = 'action';
  for (const id of Object.keys(eventMetas)) kindById[id] = 'trigger';
  const nodeIds = Object.keys(kindById);

  const rawEdges = [
    ...buildInferredEdges(eventMetas, outputProviders),
    ...buildRealEdges(actionMetas, eventMetas),
  ].filter(e => kindById[e.source] && kindById[e.target]);

  // Drop feedback loops entirely (both from layering and rendering): a downstream action that emits
  // a finding its own gating trigger listens on would otherwise draw a backward dashed stub hooking
  // out of the last card, which reads as a dangling connector rather than useful causality.
  const acyclicEdges = removeBackEdges(nodeIds, rawEdges);

  // Prune the inferred fan-in to the CLOSEST producer wave per trigger. A finding type (e.g. "port")
  // is often emitted by both an early seed and a later step; drawing an edge from every producer
  // creates long lines that cut across the whole canvas and wrongly imply the seed "triggers" a far
  // trigger. Keeping only the producers in the trigger's nearest upstream layer preserves the real
  // proximate cause, kills the crossing lines, and tightens the layout.
  const provisionalLayer = computeLayers(nodeIds, acyclicEdges);
  const inferredByTrigger = new Map<string, RawEdge[]>();
  const nonInferred: RawEdge[] = [];
  for (const edge of acyclicEdges) {
    if (edge.kind === 'inferred') {
      const list = inferredByTrigger.get(edge.target) ?? [];
      list.push(edge);
      inferredByTrigger.set(edge.target, list);
    } else {
      nonInferred.push(edge);
    }
  }
  const prunedInferred: RawEdge[] = [];
  for (const list of inferredByTrigger.values()) {
    const closestLayer = Math.max(...list.map(e => provisionalLayer[e.source]));
    for (const edge of list) {
      if (provisionalLayer[edge.source] === closestLayer) prunedInferred.push(edge);
    }
  }
  const graphEdges = [...prunedInferred, ...nonInferred];

  const preds: Record<string, string[]> = {};
  for (const edge of graphEdges) (preds[edge.target] ??= []).push(edge.source);

  const layer = computeLayers(nodeIds, graphEdges);

  // Bucket nodes per layer, keeping a stable initial order.
  const layers: Record<number, string[]> = {};
  for (const id of nodeIds) (layers[layer[id]] ??= []).push(id);
  const layerKeys = Object.keys(layers).map(Number).sort((a, b) => a - b);

  // Barycenter ordering (single downward pass) to reduce edge crossings: order each layer by the
  // average row of its predecessors in the previous layer.
  const orderIndex: Record<string, number> = {};
  layerKeys.forEach((layerKey, layerIdx) => {
    const ids = layers[layerKey];
    if (layerIdx === 0) {
      ids.forEach((id, i) => {
        orderIndex[id] = i;
      });
      return;
    }
    const withKey = ids.map((id) => {
      const parents = (preds[id] ?? []).filter(p => orderIndex[p] !== undefined);
      const key = parents.length > 0
        ? parents.reduce((sum, p) => sum + orderIndex[p], 0) / parents.length
        : Number.MAX_SAFE_INTEGER;
      return {
        id,
        key,
      };
    });
    withKey.sort((a, b) => a.key - b.key);
    layers[layerKey] = withKey.map(w => w.id);
    layers[layerKey].forEach((id, i) => {
      orderIndex[id] = i;
    });
  });

  const nodeCount = nodeIds.length;
  const colGap = columnGap(nodeCount);
  const rGap = rowGap(nodeCount);
  const heightOf = (id: string) =>
    (kindById[id] === 'action' ? ACTION_NODE_HEIGHT : TRIGGER_NODE_HEIGHT);

  // Vertically center each column around a shared midline so the chain reads as balanced rather
  // than top-left heavy.
  const colHeight: Record<number, number> = {};
  for (const layerKey of layerKeys) {
    const ids = layers[layerKey];
    colHeight[layerKey] = ids.reduce((sum, id) => sum + heightOf(id), 0)
      + Math.max(0, ids.length - 1) * rGap;
  }
  const maxColHeight = Math.max(0, ...Object.values(colHeight));

  const nodes: LogicGraphNode[] = [];
  for (const layerKey of layerKeys) {
    const ids = layers[layerKey];
    let y = (maxColHeight - colHeight[layerKey]) / 2;
    const x = layerKey * (NODE_WIDTH + colGap);
    for (const id of ids) {
      const height = heightOf(id);
      nodes.push({
        id,
        kind: kindById[id],
        layer: layerKey,
        x,
        y,
        width: NODE_WIDTH,
        height,
      });
      y += height + rGap;
    }
  }

  const nodeById: Record<string, LogicGraphNode> = Object.fromEntries(
    nodes.map(n => [n.id, n]),
  );

  const edges: LogicGraphEdge[] = graphEdges.map((edge, i) => {
    const routed = routeOrthogonalEdge(nodeById[edge.source], nodeById[edge.target]);
    return {
      id: `${edge.kind}-${edge.source}-${edge.target}-${i}`,
      source: edge.source,
      target: edge.target,
      kind: edge.kind,
      label: edge.label,
      ...routed,
    };
  });

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const node of nodes) {
    minX = Math.min(minX, node.x);
    minY = Math.min(minY, node.y);
    maxX = Math.max(maxX, node.x + node.width);
    maxY = Math.max(maxY, node.y + node.height);
  }
  if (!Number.isFinite(minX)) {
    minX = 0;
    minY = 0;
    maxX = NODE_WIDTH;
    maxY = ACTION_NODE_HEIGHT;
  }

  return {
    nodes,
    edges,
    bbox: {
      minX,
      minY,
      maxX,
      maxY,
      width: maxX - minX,
      height: maxY - minY,
    },
    nodeById,
  };
};
