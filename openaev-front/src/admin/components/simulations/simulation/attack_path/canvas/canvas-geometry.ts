import { AP_FLOW_NODE_TYPE, type AttackPathFlowEdge, type AttackPathFlowNode } from '../attack-path-flow-helpers';
import { AP_ENDPOINT_CLUSTER_SIZE, AP_ENDPOINT_SIZE, AP_FINDING_SIZE, AP_INJECTOR_SIZE } from '../nodes/node-sizes';

// Pure geometry for the custom attack-path canvas (no graph library): converts the layout helpers'
// circle-tuned node positions into card rectangles, and routes the connector paths between them.
//
// The layout builders in attack-path-flow-helpers.ts position small circular nodes on a columnar
// left-to-right flow. The canvas renders CARDS (wider rectangles in the chaining Logic view's
// visual language), so the circle positions cannot be used as-is: the columns are re-spaced by a
// COMPACTION pass — every distinct column keeps its order but is re-laid at "widest card in the
// column + a fixed gap" — which yields tight, readable spacing in every mode (clustered bands,
// causal chain, focused path) without touching the layout builders or their tests.

export interface CanvasRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CardDim {
  width: number;
  height: number;
}

// New card dimensions per node kind (world units).
export const CARD_DIMS: Record<string, CardDim> = {
  [AP_FLOW_NODE_TYPE.injector]: {
    width: 208,
    height: 68,
  },
  [AP_FLOW_NODE_TYPE.asset]: {
    width: 216,
    height: 72,
  },
  [AP_FLOW_NODE_TYPE.findingType]: {
    width: 148,
    height: 46,
  },
  [AP_FLOW_NODE_TYPE.finding]: {
    width: 196,
    height: 46,
  },
  [AP_FLOW_NODE_TYPE.endpointCluster]: {
    width: 148,
    height: 50,
  },
  [AP_FLOW_NODE_TYPE.findingCluster]: {
    width: 152,
    height: 46,
  },
};

const DEFAULT_DIM: CardDim = {
  width: 180,
  height: 56,
};

// The node size each helper position was computed for (so we can recover the intended center).
// Heights follow the half-heights the layout builders actually subtract (CLUSTER_EP_HALF_H = 42,
// CLUSTER_INJECTOR_HALF_H = 36, CHAIN_FIND_HALF = 28), not the on-screen circle diameters.
const LEGACY_SIZES: Record<string, CardDim> = {
  [AP_FLOW_NODE_TYPE.injector]: {
    width: AP_INJECTOR_SIZE,
    height: 72,
  },
  [AP_FLOW_NODE_TYPE.asset]: {
    width: AP_ENDPOINT_SIZE,
    height: 84,
  },
  [AP_FLOW_NODE_TYPE.findingType]: {
    width: AP_FINDING_SIZE,
    height: AP_FINDING_SIZE,
  },
  [AP_FLOW_NODE_TYPE.finding]: {
    width: AP_FINDING_SIZE,
    height: AP_FINDING_SIZE,
  },
  [AP_FLOW_NODE_TYPE.endpointCluster]: {
    width: AP_ENDPOINT_CLUSTER_SIZE,
    height: AP_ENDPOINT_CLUSTER_SIZE,
  },
  [AP_FLOW_NODE_TYPE.findingCluster]: {
    width: 72,
    height: AP_FINDING_SIZE,
  },
};

// Centers whose x differs by no more than this belong to the same visual column.
const COLUMN_TOLERANCE = 40;
// Centers whose y differs by no more than this belong to the same visual row: they snap to a
// common centerline (real row pitches are >= 100, residual center-recovery errors are < 10).
const ROW_TOLERANCE = 28;
// Horizontal breathing room between two adjacent columns' card edges — enough for the connector
// curve and its pill label to sit between the cards without crossing them.
const COLUMN_GAP = 170;
// The vertical spacing was tuned for circles taller than the cards; compress it slightly so rows
// read as a group while never letting the tallest cards (72px) touch (min row pitch is ~110px).
const ROW_SCALE = 0.9;

/**
 * Card rectangles (world coordinates) for every node, keyed by node id, after column compaction.
 */
export const computeCardRects = (nodes: AttackPathFlowNode[]): Map<string, CanvasRect> => {
  const rects = new Map<string, CanvasRect>();
  if (nodes.length === 0) {
    return rects;
  }
  // Recover the circle centers the layout builders intended.
  const centers = nodes.map((node) => {
    const legacy = LEGACY_SIZES[node.type ?? ''] ?? DEFAULT_DIM;
    const dim = CARD_DIMS[node.type ?? ''] ?? DEFAULT_DIM;
    return {
      id: node.id,
      cx: node.position.x + legacy.width / 2,
      cy: node.position.y + legacy.height / 2,
      dim,
    };
  });
  // Group centers into columns (distinct x values within tolerance).
  const xs = [...new Set(centers.map(c => Math.round(c.cx)))].sort((a, b) => a - b);
  const columns: number[][] = [];
  for (const x of xs) {
    const last = columns[columns.length - 1];
    if (last && x - last[last.length - 1] <= COLUMN_TOLERANCE) {
      last.push(x);
    } else {
      columns.push([x]);
    }
  }
  const columnOf = new Map<number, number>();
  columns.forEach((col, i) => col.forEach(x => columnOf.set(x, i)));
  // Re-space the columns: widest card halves + a fixed gap between adjacent column edges.
  const halfW = columns.map(() => DEFAULT_DIM.width / 2);
  centers.forEach((c) => {
    const i = columnOf.get(Math.round(c.cx))!;
    halfW[i] = Math.max(halfW[i], c.dim.width / 2);
  });
  const columnCx: number[] = [];
  columns.forEach((_, i) => {
    columnCx[i] = i === 0
      ? halfW[0]
      : columnCx[i - 1] + halfW[i - 1] + COLUMN_GAP + halfW[i];
  });
  // Snap near-identical row centers to a shared centerline so a linear chain reads as ONE crisp
  // horizontal line (the builders' per-kind half-height offsets leave a few pixels of drift).
  const ys = [...new Set(centers.map(c => Math.round(c.cy)))].sort((a, b) => a - b);
  const rows: number[][] = [];
  for (const y of ys) {
    const last = rows[rows.length - 1];
    if (last && y - last[last.length - 1] <= ROW_TOLERANCE) {
      last.push(y);
    } else {
      rows.push([y]);
    }
  }
  const rowCyOf = new Map<number, number>();
  rows.forEach((row) => {
    const mean = row.reduce((s, y) => s + y, 0) / row.length;
    row.forEach(y => rowCyOf.set(y, mean));
  });
  // Place each card centered on its compacted column and its snapped (slightly compressed) row.
  centers.forEach((c) => {
    const i = columnOf.get(Math.round(c.cx))!;
    const cy = rowCyOf.get(Math.round(c.cy)) ?? c.cy;
    rects.set(c.id, {
      x: columnCx[i] - c.dim.width / 2,
      y: cy * ROW_SCALE - c.dim.height / 2,
      width: c.dim.width,
      height: c.dim.height,
    });
  });
  return rects;
};

/** Bounding box of all cards, padded. */
export const computeContentBounds = (rects: Map<string, CanvasRect>): CanvasRect => {
  if (rects.size === 0) {
    return {
      x: 0,
      y: 0,
      width: 400,
      height: 300,
    };
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  rects.forEach((r) => {
    minX = Math.min(minX, r.x);
    minY = Math.min(minY, r.y);
    maxX = Math.max(maxX, r.x + r.width);
    maxY = Math.max(maxY, r.y + r.height);
  });
  const PAD = 48;
  return {
    x: minX - PAD,
    y: minY - PAD,
    width: maxX - minX + PAD * 2,
    height: maxY - minY + PAD * 2,
  };
};

export interface EdgeGeometry {
  id: string;
  path: string;
  labelX: number;
  labelY: number;
  edge: AttackPathFlowEdge;
}

/**
 * Smooth horizontal bezier from the source card's side to the target card's side. Forward edges
 * leave right and enter left (the graph flows left-to-right); a backward edge flips both sides so
 * the curve never crosses through its own cards.
 */
export const computeEdgeGeometry = (
  edges: AttackPathFlowEdge[],
  rects: Map<string, CanvasRect>,
): EdgeGeometry[] => {
  const result: EdgeGeometry[] = [];
  for (const edge of edges) {
    const s = rects.get(edge.source);
    const t = rects.get(edge.target);
    if (!s || !t) {
      continue;
    }
    const forward = t.x + t.width / 2 >= s.x + s.width / 2;
    const x1 = forward ? s.x + s.width : s.x;
    const y1 = s.y + s.height / 2;
    const x2 = forward ? t.x : t.x + t.width;
    const y2 = t.y + t.height / 2;
    const dx = Math.max(48, Math.min(190, Math.abs(x2 - x1) * 0.45));
    const c1x = forward ? x1 + dx : x1 - dx;
    const c2x = forward ? x2 - dx : x2 + dx;
    result.push({
      id: edge.id,
      path: `M ${x1} ${y1} C ${c1x} ${y1}, ${c2x} ${y2}, ${x2} ${y2}`,
      labelX: (x1 + x2) / 2,
      labelY: (y1 + y2) / 2,
      edge,
    });
  }
  return result;
};
