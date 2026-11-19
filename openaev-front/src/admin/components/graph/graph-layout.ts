import { type GraphEdgeOutput, type GraphNodeOutput, type GraphNodeType } from './graph-model';

export type GraphLayout = 'force' | 'hierarchical' | 'radial';

export interface Point {
  x: number;
  y: number;
}

const NODE_GAP = 140;
const RING_GAP = 220;

/** The tier of a node type in the hierarchical layout: scenario on top, findings at the bottom. */
const TIER: Record<GraphNodeType, number> = {
  SCENARIO: 0,
  SIMULATION: 1,
  INJECT: 2,
  ASSET_GROUP: 3,
  ASSET: 4,
  FINDING: 5,
  // People of #9447 hang below the assets their identities are: person, then team, then organization.
  PERSON: 5,
  TEAM: 6,
  ORGANIZATION: 7,
};

/** Breadth-first distance from the focus over undirected edges; unreachable nodes get the max + 1. */
export const distances = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], focus?: string | null) => {
  const adjacency = new Map<string, string[]>();
  nodes.forEach(n => adjacency.set(n.node_id, []));
  edges.forEach((e) => {
    adjacency.get(e.edge_source)?.push(e.edge_target);
    adjacency.get(e.edge_target)?.push(e.edge_source);
  });
  const result = new Map<string, number>();
  const start = focus && adjacency.has(focus) ? focus : nodes[0]?.node_id;
  if (!start) {
    return result;
  }
  const queue = [start];
  result.set(start, 0);
  while (queue.length > 0) {
    const current = queue.shift() as string;
    const d = result.get(current) as number;
    (adjacency.get(current) ?? []).forEach((next) => {
      if (!result.has(next)) {
        result.set(next, d + 1);
        queue.push(next);
      }
    });
  }
  const max = Math.max(0, ...result.values());
  nodes.forEach((n) => {
    if (!result.has(n.node_id)) {
      result.set(n.node_id, max + 1);
    }
  });
  return result;
};

const byLabel = (a: GraphNodeOutput, b: GraphNodeOutput) => a.node_label.localeCompare(b.node_label);

const hierarchical = (nodes: GraphNodeOutput[]): Map<string, Point> => {
  const tiers = new Map<number, GraphNodeOutput[]>();
  nodes.forEach((n) => {
    const t = TIER[n.node_type];
    tiers.set(t, [...(tiers.get(t) ?? []), n]);
  });
  const positions = new Map<string, Point>();
  [...tiers.keys()].sort((a, b) => a - b).forEach((tier, row) => {
    const items = (tiers.get(tier) ?? []).sort(byLabel);
    const width = (items.length - 1) * NODE_GAP;
    items.forEach((n, i) => positions.set(n.node_id, {
      x: i * NODE_GAP - width / 2,
      y: row * NODE_GAP,
    }));
  });
  return positions;
};

const radial = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], focus?: string | null): Map<string, Point> => {
  const dist = distances(nodes, edges, focus);
  const rings = new Map<number, GraphNodeOutput[]>();
  nodes.forEach((n) => {
    const d = dist.get(n.node_id) ?? 0;
    rings.set(d, [...(rings.get(d) ?? []), n]);
  });
  const positions = new Map<string, Point>();
  rings.forEach((items, ring) => {
    const sorted = items.sort((a, b) => TIER[a.node_type] - TIER[b.node_type] || byLabel(a, b));
    if (ring === 0) {
      sorted.forEach((n, i) => positions.set(n.node_id, {
        x: i * NODE_GAP,
        y: 0,
      }));
      return;
    }
    const radius = ring * RING_GAP;
    sorted.forEach((n, i) => {
      const angle = (2 * Math.PI * i) / sorted.length - Math.PI / 2;
      positions.set(n.node_id, {
        x: Math.round(radius * Math.cos(angle)),
        y: Math.round(radius * Math.sin(angle)),
      });
    });
  });
  return positions;
};

/** The box a node occupies (circle and label under it), kept clear of every other node's box. */
export const MIN_NODE_DX = 150;
export const MIN_NODE_DY = 90;

/** Margin kept between the mini graph card's border and its nodes (#9448, UX round 2 B3). */
export const MINI_CARD_PADDING = 24;
/** The mini graph card never goes under this height. */
export const MINI_CARD_MIN_HEIGHT = 240;

/**
 * Height of the mini graph card for a layout drawn at zoom 1: the vertical span of the node boxes
 * (a position is a node's top-left corner, the box is `nodeBoxHeight` tall) plus the card margin
 * above and below, so the top and bottom rows are never cut by the card's edges.
 */
export const miniCardHeight = (positions: Map<string, Point>, nodeBoxHeight: number): number => {
  if (positions.size === 0) {
    return MINI_CARD_MIN_HEIGHT;
  }
  const ys = [...positions.values()].map(p => p.y);
  const span = Math.max(...ys) - Math.min(...ys) + nodeBoxHeight;
  return Math.max(MINI_CARD_MIN_HEIGHT, Math.ceil(span + 2 * MINI_CARD_PADDING));
};

/** Half the vertical distance between the two rows of the mini layout. */
const MINI_ROW = 60;

/**
 * The layout of the mini graph card (a wide, short strip on an Overview): the focus in the centre,
 * its neighbours on two rows spreading out to the right and the left, closest first (ordered by
 * type then label). The strip stays about 180 px high whatever the count, so the card shows it at
 * zoom 1 with readable labels; the outer columns of a large ring are clipped by the card.
 */
export const miniLayout = (nodes: GraphNodeOutput[], focus?: string | null): Map<string, Point> => {
  const positions = new Map<string, Point>();
  const centre = focus && nodes.some(n => n.node_id === focus) ? focus : nodes[0]?.node_id;
  if (!centre) {
    return positions;
  }
  positions.set(centre, {
    x: 0,
    y: 0,
  });
  nodes
    .filter(n => n.node_id !== centre)
    .sort((a, b) => TIER[a.node_type] - TIER[b.node_type] || byLabel(a, b))
    .forEach((n, i) => {
      const slot = Math.floor(i / 2);
      const side = slot % 2 === 0 ? 1 : -1;
      const column = Math.floor(slot / 2) + 1;
      positions.set(n.node_id, {
        x: side * column * MIN_NODE_DX,
        y: i % 2 === 0 ? -MINI_ROW : MINI_ROW,
      });
    });
  return positions;
};

/**
 * Pushes overlapping nodes apart along the axis of least overlap until no two node boxes overlap
 * (bounded passes): many parallel edges between few nodes make the force step pull them closer
 * than their labels allow. The focused node stays put.
 */
export const separate = (positions: Map<string, Point>, ids: string[], focus?: string | null, fixed?: Set<string>) => {
  for (let pass = 0; pass < 50; pass += 1) {
    let moved = false;
    for (let i = 0; i < ids.length; i += 1) {
      for (let j = i + 1; j < ids.length; j += 1) {
        const a = positions.get(ids[i]) as Point;
        const b = positions.get(ids[j]) as Point;
        const ox = MIN_NODE_DX - Math.abs(a.x - b.x);
        const oy = MIN_NODE_DY - Math.abs(a.y - b.y);
        const aFixed = ids[i] === focus || Boolean(fixed?.has(ids[i]));
        const bFixed = ids[j] === focus || Boolean(fixed?.has(ids[j]));
        if (ox <= 0 || oy <= 0 || (aFixed && bFixed)) {
          continue;
        }
        moved = true;
        const share = aFixed || bFixed ? 1 : 0.5;
        if (ox / MIN_NODE_DX < oy / MIN_NODE_DY) {
          const dir = a.x < b.x || (a.x === b.x && i < j) ? -1 : 1;
          if (!aFixed) {
            a.x += dir * ox * share;
          }
          if (!bFixed) {
            b.x -= dir * ox * share;
          }
        } else {
          const dir = a.y < b.y || (a.y === b.y && i < j) ? -1 : 1;
          if (!aFixed) {
            a.y += dir * oy * share;
          }
          if (!bFixed) {
            b.y -= dir * oy * share;
          }
        }
      }
    }
    if (!moved) {
      return;
    }
  }
};

const snapshot = (positions: Map<string, Point>) => new Map([...positions].map(([id, p]) => [id, {
  x: Math.round(p.x),
  y: Math.round(p.y),
}]));

/** True when the placement is taller than it is wide. */
export const isPortrait = (positions: Map<string, Point>): boolean => {
  const points = [...positions.values()];
  if (points.length < 2) {
    return false;
  }
  const xs = points.map(p => p.x);
  const ys = points.map(p => p.y);
  return Math.max(...ys) - Math.min(...ys) > Math.max(...xs) - Math.min(...xs);
};

/**
 * Turns a placement a quarter turn about a centre, in place: what was top to bottom reads left to
 * right. The page canvas is wider than tall, so a force placement that settled as a tall chain (the
 * landing story of #9447 is one attack path) is turned to fill the width instead of falling off the
 * top and the bottom of the canvas at the story's minimum zoom.
 */
export const quarterTurn = (positions: Map<string, Point>, centre: Point) => {
  positions.forEach((p, id) => positions.set(id, {
    x: centre.x + (p.y - centre.y),
    y: centre.y - (p.x - centre.x),
  }));
};

/** One frame of the force simulation every this many iterations: what the screen plays live. */
export const FORCE_FRAME_EVERY = 5;

/**
 * A deterministic force-directed layout (Fruchterman-Reingold), seeded by the radial layout so the
 * same graph always lands the same way: repulsion between every pair, attraction along edges, a
 * cooling step. Bounded to a fixed number of iterations: the API caps a slice at a few hundred nodes.
 * Answers the frames of the simulation, from the seed to the settled, separated placement, so the
 * explorer plays the forces settling on screen instead of jumping to the result. A placement that
 * settles taller than wide is turned a quarter turn, every frame with it, to fit the wide canvas.
 */
const forceFrames = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], focus?: string | null): Map<string, Point>[] => {
  const positions = radial(nodes, edges, focus);
  const ids = nodes.map(n => n.node_id);
  if (ids.length < 3) {
    return [positions];
  }
  const frames = [snapshot(positions)];
  const k = NODE_GAP;
  let temperature = RING_GAP;
  const iterations = Math.min(150, 20 + ids.length * 2);
  for (let step = 0; step < iterations; step += 1) {
    const shift = new Map<string, Point>(ids.map(id => [id, {
      x: 0,
      y: 0,
    }]));
    for (let i = 0; i < ids.length; i += 1) {
      for (let j = i + 1; j < ids.length; j += 1) {
        const a = positions.get(ids[i]) as Point;
        const b = positions.get(ids[j]) as Point;
        const dx = a.x - b.x || 0.01;
        const dy = a.y - b.y || 0.01;
        const d = Math.max(Math.hypot(dx, dy), 1);
        const f = (k * k) / d;
        const sa = shift.get(ids[i]) as Point;
        const sb = shift.get(ids[j]) as Point;
        sa.x += (dx / d) * f;
        sa.y += (dy / d) * f;
        sb.x -= (dx / d) * f;
        sb.y -= (dy / d) * f;
      }
    }
    edges.forEach((e) => {
      const a = positions.get(e.edge_source);
      const b = positions.get(e.edge_target);
      if (!a || !b) {
        return;
      }
      const dx = a.x - b.x;
      const dy = a.y - b.y;
      const d = Math.max(Math.hypot(dx, dy), 1);
      const f = (d * d) / k;
      const sa = shift.get(e.edge_source) as Point;
      const sb = shift.get(e.edge_target) as Point;
      sa.x -= (dx / d) * f;
      sa.y -= (dy / d) * f;
      sb.x += (dx / d) * f;
      sb.y += (dy / d) * f;
    });
    ids.forEach((id) => {
      if (id === focus) {
        return;
      }
      const s = shift.get(id) as Point;
      const length = Math.max(Math.hypot(s.x, s.y), 1);
      const p = positions.get(id) as Point;
      p.x += (s.x / length) * Math.min(length, temperature);
      p.y += (s.y / length) * Math.min(length, temperature);
    });
    temperature *= 0.95;
    if ((step + 1) % FORCE_FRAME_EVERY === 0) {
      frames.push(snapshot(positions));
    }
  }
  if (isPortrait(positions)) {
    const pivot = (focus && positions.get(focus)) || {
      x: 0,
      y: 0,
    };
    const centre = {
      x: pivot.x,
      y: pivot.y,
    };
    frames.forEach(frame => quarterTurn(frame, centre));
    quarterTurn(positions, centre);
  }
  separate(positions, ids, focus);
  frames.push(snapshot(positions));
  return frames;
};

/**
 * The frames that lead to a layout: the force layout's simulation steps, or the single placement of
 * a static layout. The last frame is the layout itself.
 */
export const layoutFrames = (
  layout: GraphLayout,
  nodes: GraphNodeOutput[],
  edges: GraphEdgeOutput[],
  focus?: string | null,
): Map<string, Point>[] => {
  switch (layout) {
    case 'hierarchical':
      return [hierarchical(nodes)];
    case 'radial':
      return [radial(nodes, edges, focus)];
    default:
      return forceFrames(nodes, edges, focus);
  }
};

export const layoutGraph = (
  layout: GraphLayout,
  nodes: GraphNodeOutput[],
  edges: GraphEdgeOutput[],
  focus?: string | null,
): Map<string, Point> => {
  const frames = layoutFrames(layout, nodes, edges, focus);
  return frames[frames.length - 1];
};

/**
 * Where a node that enters the graph starts its move: at the current place of a neighbour already
 * on screen (an expanded node's ring grows out of it), else at its own target.
 */
export const entering = (from: Map<string, Point>, target: Map<string, Point>, edges: GraphEdgeOutput[]): Map<string, Point> => {
  const start = new Map(from);
  target.forEach((p, id) => {
    if (start.has(id)) {
      return;
    }
    const edge = edges.find(e => (e.edge_source === id && from.has(e.edge_target)) || (e.edge_target === id && from.has(e.edge_source)));
    const anchor = edge ? from.get(edge.edge_source === id ? edge.edge_target : edge.edge_source) : undefined;
    start.set(id, anchor ?? p);
  });
  return start;
};

/**
 * The layout of the compact mini graph card (#9447 elevation): the focus in the centre, its first
 * ring split left and right in columns of three, and each second-hop node one column further out than
 * the neighbour it hangs on, so a two-hop view stays a small readable block instead of a strip.
 */
export const miniCardLayout = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], focus: string): Map<string, Point> => {
  const positions = new Map<string, Point>();
  if (!nodes.some(n => n.node_id === focus)) {
    return miniLayout(nodes, focus);
  }
  positions.set(focus, {
    x: 0,
    y: 0,
  });
  const first = new Set<string>();
  edges.forEach((e) => {
    if (e.edge_source === focus) first.add(e.edge_target);
    if (e.edge_target === focus) first.add(e.edge_source);
  });
  const ring = nodes.filter(n => first.has(n.node_id));
  const rows = [-MIN_NODE_DY, 0, MIN_NODE_DY];
  ring.forEach((n, i) => {
    const side = i % 2 === 0 ? 1 : -1;
    const index = Math.floor(i / 2);
    const column = Math.floor(index / rows.length) + 1;
    positions.set(n.node_id, {
      x: side * column * MIN_NODE_DX,
      y: rows[index % rows.length],
    });
  });
  const outer = new Map<number, number>();
  nodes.filter(n => !positions.has(n.node_id)).forEach((n) => {
    const edge = edges.find(e => (e.edge_source === n.node_id && first.has(e.edge_target)) || (e.edge_target === n.node_id && first.has(e.edge_source)));
    const parent = edge ? positions.get(edge.edge_source === n.node_id ? edge.edge_target : edge.edge_source) : undefined;
    const base = parent ?? {
      x: MIN_NODE_DX,
      y: 0,
    };
    const side = base.x < 0 ? -1 : 1;
    const x = Math.max(...[...positions.values()].filter(p => Math.sign(p.x) === side).map(p => Math.abs(p.x)), 0) + MIN_NODE_DX;
    const slot = outer.get(side) ?? 0;
    outer.set(side, slot + 1);
    positions.set(n.node_id, {
      x: side * x,
      y: base.y + (slot % 2 === 0 ? 0 : MIN_NODE_DY / 2),
    });
  });
  separate(positions, nodes.map(n => n.node_id), focus);
  return snapshot(positions);
};

/**
 * Places the nodes that enter a graph already on screen (an expand, a pivot, a node shown again)
 * without moving any node already placed, as an OpenCTI investigation grows: each new node starts on
 * an arc around the neighbour it came from, on the side facing away from the rest of the graph, then
 * only the new nodes are pushed apart until no two node boxes overlap. A node with no neighbour on
 * screen goes on an arc beyond the graph's outer edge.
 */
export const extendLayout = (placed: Map<string, Point>, nodes: GraphNodeOutput[], edges: GraphEdgeOutput[]): Map<string, Point> => {
  const result = new Map<string, Point>();
  nodes.forEach((n) => {
    const p = placed.get(n.node_id);
    if (p) {
      result.set(n.node_id, {
        x: p.x,
        y: p.y,
      });
    }
  });
  const fixed = new Set(result.keys());
  const fresh = nodes.filter(n => !fixed.has(n.node_id));
  if (fresh.length === 0) {
    return result;
  }
  const existing = [...result.values()];
  const centre = existing.length > 0
    ? {
        x: existing.reduce((sum, p) => sum + p.x, 0) / existing.length,
        y: existing.reduce((sum, p) => sum + p.y, 0) / existing.length,
      }
    : {
        x: 0,
        y: 0,
      };
  const outer = Math.max(0, ...existing.map(p => Math.hypot(p.x - centre.x, p.y - centre.y)));
  const byAnchor = new Map<string, GraphNodeOutput[]>();
  fresh.forEach((n) => {
    const edge = edges.find(e => (e.edge_source === n.node_id && fixed.has(e.edge_target)) || (e.edge_target === n.node_id && fixed.has(e.edge_source)));
    let anchor = '';
    if (edge) {
      anchor = edge.edge_source === n.node_id ? edge.edge_target : edge.edge_source;
    }
    byAnchor.set(anchor, [...(byAnchor.get(anchor) ?? []), n]);
  });
  byAnchor.forEach((group, anchorId) => {
    const anchor = result.get(anchorId);
    const origin = anchor ?? centre;
    const away = anchor && Math.hypot(anchor.x - centre.x, anchor.y - centre.y) > 1
      ? Math.atan2(anchor.y - centre.y, anchor.x - centre.x)
      : 0;
    // A small ring opens a half circle away from the graph; a large one closes to a full circle.
    const spread = Math.min(2 * Math.PI, Math.PI * (1 + group.length / 12));
    const radius = anchor
      ? Math.max(RING_GAP, (group.length * MIN_NODE_DX) / spread)
      : outer + RING_GAP;
    group
      .sort((a, b) => TIER[a.node_type] - TIER[b.node_type] || byLabel(a, b))
      .forEach((n, i) => {
        const angle = group.length === 1 ? away : away - spread / 2 + (spread * (i + 0.5)) / group.length;
        result.set(n.node_id, {
          x: Math.round(origin.x + radius * Math.cos(angle)),
          y: Math.round(origin.y + radius * Math.sin(angle)),
        });
      });
  });
  separate(result, nodes.map(n => n.node_id), null, fixed);
  return snapshot(result);
};

/** The box that holds every node of a placement (a node's position is the top left of its box). */
export const layoutBounds = (positions: Map<string, Point>, nodeWidth: number, nodeHeight: number) => {
  const points = [...positions.values()];
  if (points.length === 0) {
    return null;
  }
  const xs = points.map(p => p.x);
  const ys = points.map(p => p.y);
  const x = Math.min(...xs);
  const y = Math.min(...ys);
  return {
    x,
    y,
    width: Math.max(...xs) - x + nodeWidth,
    height: Math.max(...ys) - y + nodeHeight,
  };
};

/** The positions between two frames at t in [0, 1], for the nodes of the second frame. */
export const interpolate = (a: Map<string, Point>, b: Map<string, Point>, t: number): Map<string, Point> => {
  const result = new Map<string, Point>();
  b.forEach((to, id) => {
    const from = a.get(id) ?? to;
    result.set(id, {
      x: from.x + (to.x - from.x) * t,
      y: from.y + (to.y - from.y) * t,
    });
  });
  return result;
};
