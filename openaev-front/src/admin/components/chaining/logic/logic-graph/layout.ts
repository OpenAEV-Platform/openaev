import { formatConditionKeyLabel } from '../events/event-types';
import { collectEventFields, type OutputProviderEntry } from '../logic-flow-helpers';
import type { ActionMeta, EventMeta } from '../types';

// -- Layout design tokens (px) --
export const NODE_WIDTH = 248;
export const ACTION_NODE_HEIGHT = 112;
export const TRIGGER_NODE_HEIGHT = 76;

// -- Tactic-column tokens (px). Actions are laid out in one column per MITRE tactic (ordered by
// kill-chain phase), and each gating event sits in a lane immediately to the left of the column it
// feeds. Every tactic owns an exclusive horizontal stride, which is what makes the tactic bands
// non-overlapping BY CONSTRUCTION (see buildLogicGraphLayout). --
const EVENT_TO_COL_GAP = 60; // gap between an event lane and the action column it feeds
const INTER_COLUMN_GAP = 90; // gap between one tactic column and the next column's event lane
const ACTION_ROW_GAP = 40; // vertical gap between stacked actions in a column
const EVENT_ROW_GAP = 24; // vertical gap when de-overlapping events sharing a lane
const COLUMN_TOP_MARGIN = 56; // room above the top node, inside the band, for the tactic header
const BAND_TOP = 8; // top of the tactic band
const BAND_PADDING_X = 18; // horizontal breathing room between a card and its band border
const BAND_PADDING_BOTTOM = 20; // padding below the last card inside the band
const BAND_HEADER_HEIGHT = 26; // room reserved at the top of a band for the tactic label
// One tactic column spans its event lane + gap + action column; the next starts a stride further.
const COLUMN_STRIDE = NODE_WIDTH + EVENT_TO_COL_GAP + NODE_WIDTH + INTER_COLUMN_GAP;
const eventLaneX = (col: number) => col * COLUMN_STRIDE;
const actionColX = (col: number) => col * COLUMN_STRIDE + NODE_WIDTH + EVENT_TO_COL_GAP;

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

/**
 * One MITRE-tactic column: the padded band drawn behind the action cards of that tactic, plus its
 * header label. Exactly one band per tactic, and bands never overlap — each owns an exclusive
 * horizontal stride of the canvas. The band covers the ACTION column only: events carry no TTP, so
 * they sit in the lane to its LEFT, outside any tactic. `order` is the tactic's kill-chain phase
 * rank, used by the renderer to pick a stable accent colour.
 */
export interface LogicGraphColumn {
  tactic: string;
  order: number;
  x: number;
  y: number;
  width: number;
  height: number;
  /** Height reserved for the header label, inside the band at the top. */
  headerHeight: number;
}

export interface LogicGraphLayout {
  nodes: LogicGraphNode[];
  edges: LogicGraphEdge[];
  columns: LogicGraphColumn[];
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
  /** Resolved MITRE tactic name per action step id (see buildTacticForStep). */
  tacticForStep: Record<string, string>;
  /** Tactic name -> kill-chain phase order, so group colours follow the MITRE order (lower = earlier). */
  tacticOrder: Record<string, number>;
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
 * Build the auto-laid-out graph (nodes + orthogonal connector paths + tactic columns + bounding box)
 * from the parsed action / event metadata. Pure and deterministic so it can be memoized behind a
 * content fingerprint (polling re-renders must not shuffle the layout).
 *
 * Columns are MITRE tactics, ordered by kill-chain phase: every action sits in its tactic's column
 * and every gating event in the lane just left of it, so the map reads as one band per tactic with
 * no overlap possible. Dependency depth only orders the cards inside a column.
 */
export const buildLogicGraphLayout = ({
  actionMetas,
  eventMetas,
  outputProviders,
  tacticForStep,
  tacticOrder,
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

  // Dependency depth. It no longer picks a node's COLUMN (the tactic does, see below) — it only
  // orders the cards top-down inside a column so a tactic's steps still read in causal order.
  const layer = computeLayers(nodeIds, graphEdges);

  // -- Tactic-column positioning ------------------------------------------------------------------
  // INVARIANT: one MITRE tactic per column, and no overlap anywhere. Actions are bucketed into one
  // column per tactic (columns ordered by kill-chain phase), and each gating event sits in a lane
  // immediately to the LEFT of its action's column, aligned to the action it gates so the
  // event -> action arrow stays horizontal.
  //
  // Because every tactic owns an exclusive horizontal stride (event lane + gap + action column +
  // gap), a tactic band can never intersect another band, nor a card of another tactic. That is the
  // whole point of laying out by tactic rather than decorating a depth-ordered layout with per-tactic
  // bounding hulls: one tactic's actions would then scatter across several depth columns, so its hull
  // stretched over its neighbours' cards and every stacked hull's header landed on the card above.
  const actionIds = nodeIds.filter(id => kindById[id] === 'action');
  const eventIds = nodeIds.filter(id => kindById[id] === 'trigger');
  const inputOrder: Record<string, number> = {};
  nodeIds.forEach((id, i) => {
    inputOrder[id] = i;
  });

  // The tactic columns present in THIS workflow, ordered by phase (unknown tactics sort last, then
  // by name for determinism).
  const uniqueTactics = Array.from(new Set(actionIds.map(id => tacticForStep[id] ?? '')));
  uniqueTactics.sort((a, b) => {
    const oa = tacticOrder[a] ?? 99;
    const ob = tacticOrder[b] ?? 99;
    return oa !== ob ? oa - ob : a.localeCompare(b);
  });
  const colByTactic: Record<string, number> = {};
  uniqueTactics.forEach((tactic, i) => {
    colByTactic[tactic] = i;
  });
  const colOfAction = (id: string) => colByTactic[tacticForStep[id] ?? ''] ?? 0;

  // Actions per column, stacked in causal order (dependency depth, then input order to stay stable).
  const actionsByCol: Record<number, string[]> = {};
  for (const id of actionIds) (actionsByCol[colOfAction(id)] ??= []).push(id);
  for (const ids of Object.values(actionsByCol)) {
    ids.sort((a, b) => (layer[a] !== layer[b] ? layer[a] - layer[b] : inputOrder[a] - inputOrder[b]));
  }

  // Actions are top-aligned within their column (like a table): every column starts at the same top,
  // just under its header, so headers stay attached to their cards. `colActionBottomY` tracks each
  // column's lowest ACTION so its band can be sized to fit.
  const nodes: LogicGraphNode[] = [];
  const actionCenterY: Record<string, number> = {};
  const colActionBottomY: Record<number, number> = {};
  uniqueTactics.forEach((_tactic, col) => {
    let y = COLUMN_TOP_MARGIN;
    for (const id of actionsByCol[col] ?? []) {
      nodes.push({
        id,
        kind: 'action',
        layer: col,
        x: actionColX(col),
        y,
        width: NODE_WIDTH,
        height: ACTION_NODE_HEIGHT,
      });
      actionCenterY[id] = y + ACTION_NODE_HEIGHT / 2;
      colActionBottomY[col] = y + ACTION_NODE_HEIGHT;
      y += ACTION_NODE_HEIGHT + ACTION_ROW_GAP;
    }
  });

  // Resolve, per event, the lane it sits in and the Y to align to: the leftmost tactic among the
  // actions it gates, aligned to that action's center (topmost when it gates several).
  const eventPlacement: Record<string, {
    col: number;
    anchorY: number;
  }> = {};
  for (const [stepId, meta] of Object.entries(actionMetas)) {
    if (actionCenterY[stepId] === undefined) continue;
    const col = colOfAction(stepId);
    const y = actionCenterY[stepId];
    for (const eventId of meta.step_condition_ids) {
      if (!eventMetas[eventId]) continue;
      const cur = eventPlacement[eventId];
      if (!cur || col < cur.col || (col === cur.col && y < cur.anchorY)) {
        eventPlacement[eventId] = {
          col,
          anchorY: y,
        };
      }
    }
  }

  // Bucket events per lane (orphan events, gating nothing, share column 0's lane), then align each
  // to its anchor and cascade down only to resolve overlaps ("align, then de-overlap") — a lane
  // never stacks two cards closer than EVENT_ROW_GAP.
  const eventLanes: Record<number, {
    id: string;
    anchorY: number | null;
  }[]> = {};
  for (const id of eventIds) {
    const col = eventPlacement[id]?.col ?? 0;
    (eventLanes[col] ??= []).push({
      id,
      anchorY: eventPlacement[id]?.anchorY ?? null,
    });
  }
  for (const [laneColStr, items] of Object.entries(eventLanes)) {
    const laneCol = Number(laneColStr);
    items.sort(
      (a, b) => (a.anchorY ?? Number.POSITIVE_INFINITY) - (b.anchorY ?? Number.POSITIVE_INFINITY),
    );
    let cursor = COLUMN_TOP_MARGIN;
    for (const { id, anchorY } of items) {
      const desiredTop = anchorY === null ? cursor : anchorY - TRIGGER_NODE_HEIGHT / 2;
      const y = Math.max(desiredTop, cursor);
      nodes.push({
        id,
        kind: 'trigger',
        layer: laneCol,
        x: eventLaneX(laneCol),
        y,
        width: NODE_WIDTH,
        height: TRIGGER_NODE_HEIGHT,
      });
      cursor = y + TRIGGER_NODE_HEIGHT + EVENT_ROW_GAP;
    }
  }

  // One band per tactic column, covering the ACTION column only (events have no TTP, so they sit in
  // the lane to the left, outside any band). Padded so cards never sit flush against the border, and
  // sized to its own column's actions.
  const columns: LogicGraphColumn[] = uniqueTactics.map((tactic, col) => {
    const bottom = colActionBottomY[col] ?? COLUMN_TOP_MARGIN;
    return {
      tactic,
      order: tacticOrder[tactic] ?? 99,
      x: actionColX(col) - BAND_PADDING_X,
      y: BAND_TOP,
      width: NODE_WIDTH + 2 * BAND_PADDING_X,
      height: bottom + BAND_PADDING_BOTTOM - BAND_TOP,
      headerHeight: BAND_HEADER_HEIGHT,
    };
  });

  // Normalize the whole layout to the origin: the leftmost/topmost content is a band corner (bands
  // pad out around their cards), not a node. The render container and pan/zoom fit both assume
  // content starts at (0, 0), so shift every node and band by the negative of the global minimum. Do
  // this BEFORE routing edges so the connector paths are computed in the final coordinate space.
  let originX = Infinity;
  let originY = Infinity;
  for (const node of nodes) {
    originX = Math.min(originX, node.x);
    originY = Math.min(originY, node.y);
  }
  for (const column of columns) {
    originX = Math.min(originX, column.x);
    originY = Math.min(originY, column.y);
  }
  const offsetX = Number.isFinite(originX) ? -originX : 0;
  const offsetY = Number.isFinite(originY) ? -originY : 0;
  if (offsetX !== 0 || offsetY !== 0) {
    for (const node of nodes) {
      node.x += offsetX;
      node.y += offsetY;
    }
    for (const column of columns) {
      column.x += offsetX;
      column.y += offsetY;
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
  // Fold the tactic bands into the bbox so their padded borders and headers (which extend above and
  // around the cards) are never framed out on fit.
  for (const column of columns) {
    minX = Math.min(minX, column.x);
    minY = Math.min(minY, column.y);
    maxX = Math.max(maxX, column.x + column.width);
    maxY = Math.max(maxY, column.y + column.height);
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
    columns,
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
