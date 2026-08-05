import { formatConditionKeyLabel } from '../events/event-types';
import { collectEventFields, type OutputProviderEntry } from '../logic-flow-helpers';
import type { ActionMeta, EventMeta } from '../types';

// -- Layout design tokens (px) --
export const NODE_WIDTH = 248;
export const ACTION_NODE_HEIGHT = 112;
export const TRIGGER_NODE_HEIGHT = 76;

// Tactic-column layout tokens (px). Actions are grouped into one column per MITRE tactic (ordered by
// kill-chain phase), and each gating event sits in a lane immediately to the left of its action's
// column. See buildLogicGraphLayout.
const EVENT_TO_COL_GAP = 60; // gap between an event lane and the action column it feeds
const INTER_COLUMN_GAP = 90; // gap between one tactic column and the next column's event lane
const ACTION_ROW_GAP = 40; // vertical gap between stacked actions in a column
const EVENT_ROW_GAP = 24; // vertical gap when de-overlapping events sharing a lane
const COLUMN_TOP_MARGIN = 56; // room above the top node for the tactic column header
const COLUMN_HEADER_Y = 22; // Y of the tactic column header label (within the top margin)
const BAND_TOP = 8; // top of the tactic column background band
const BAND_PADDING_BOTTOM = 20; // padding below the last node inside the column band
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

/** One MITRE-tactic column: its header label and the coloured band its nodes sit in. */
export interface LogicGraphColumn {
  tactic: string;
  /**
   * The coloured band rectangle (world coords). Covers the ACTION column only: events carry no TTP,
   * so they sit in the lane to the LEFT of this band, outside any tactic (see buildLogicGraphLayout).
   */
  x: number;
  width: number;
  top: number;
  height: number;
  /** Y of the column header label (centered over the band). */
  headerY: number;
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
  /** Tactic name -> kill-chain phase order, so columns follow the MITRE order (lower = leftmost). */
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
 * Build the auto-laid-out causal graph (nodes + orthogonal connector paths + bounding box) from the
 * parsed action / event metadata. Pure and deterministic so it can be memoized behind a content
 * fingerprint (polling re-renders must not shuffle the layout).
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

  // -- Tactic-column positioning ------------------------------------------------------------------
  // Actions are grouped into one column per MITRE tactic, columns ordered by kill-chain phase. Each
  // gating event sits in a lane immediately to the LEFT of its action's column, vertically aligned
  // to the action it gates (so the event -> action arrow stays horizontal). This restores the
  // tactic-column reading of the logic view (the dependency-depth layering it replaced put the whole
  // chain on a single alternating row). The graphEdges above are still used only for rendering.
  const actionIds = nodeIds.filter(id => kindById[id] === 'action');
  const eventIds = nodeIds.filter(id => kindById[id] === 'trigger');

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

  // Actions per column, in a stable order.
  const actionsByCol: Record<number, string[]> = {};
  for (const id of actionIds) (actionsByCol[colOfAction(id)] ??= []).push(id);

  // Actions are top-aligned within their column (like a table): every column starts at the same top,
  // just under its header, so headers stay attached to their nodes. `colActionBottomY` tracks each
  // column's lowest ACTION so its background band can be sized to fit — only actions carry a TTP, so
  // only actions belong to a tactic column; events sit outside the band (see below).
  const nodes: LogicGraphNode[] = [];
  const actionCenterY: Record<string, number> = {};
  const colActionBottomY: Record<number, number> = {};
  uniqueTactics.forEach((_tactic, col) => {
    const ids = actionsByCol[col] ?? [];
    let y = COLUMN_TOP_MARGIN;
    for (const id of ids) {
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

  // Resolve, per event, the column it sits left of and the Y to align to: the leftmost tactic among
  // the actions it gates, aligned to that action's center (topmost when it gates several).
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

  // Bucket events per lane X (orphan events, gating nothing, share column 0's lane), then align each
  // to its anchor and cascade down only to resolve overlaps ("align, then de-overlap").
  const eventLanes: Record<number, {
    id: string;
    anchorY: number | null;
  }[]> = {};
  for (const id of eventIds) {
    const p = eventPlacement[id];
    const laneX = eventLaneX(p ? p.col : 0);
    (eventLanes[laneX] ??= []).push({
      id,
      anchorY: p ? p.anchorY : null,
    });
  }
  for (const [laneXStr, items] of Object.entries(eventLanes)) {
    const laneX = Number(laneXStr);
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
        layer: 0,
        x: laneX,
        y,
        width: NODE_WIDTH,
        height: TRIGGER_NODE_HEIGHT,
      });
      cursor = y + TRIGGER_NODE_HEIGHT + EVENT_ROW_GAP;
    }
  }

  // One coloured band per column, covering the ACTION column only (events have no TTP, so they sit
  // in the lane to the left of the band, outside any tactic). Sized to the column's actions.
  const columns: LogicGraphColumn[] = uniqueTactics.map((tactic, col) => {
    const bottom = colActionBottomY[col] ?? COLUMN_TOP_MARGIN;
    return {
      tactic,
      x: actionColX(col),
      width: NODE_WIDTH,
      top: BAND_TOP,
      height: bottom + BAND_PADDING_BOTTOM - BAND_TOP,
      headerY: COLUMN_HEADER_Y,
    };
  });

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
  // Start at the band top so the column headers/bands above the topmost node are never framed out.
  let minY = columns.length > 0 ? BAND_TOP : Infinity;
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
