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

/**
 * A deterministic force-directed layout (Fruchterman-Reingold), seeded by the radial layout so the
 * same graph always lands the same way: repulsion between every pair, attraction along edges, a
 * cooling step. Bounded to a fixed number of iterations: the API caps a slice at a few hundred nodes.
 */
const force = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], focus?: string | null): Map<string, Point> => {
  const positions = radial(nodes, edges, focus);
  const ids = nodes.map(n => n.node_id);
  if (ids.length < 3) {
    return positions;
  }
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
  }
  positions.forEach((p) => {
    p.x = Math.round(p.x);
    p.y = Math.round(p.y);
  });
  return positions;
};

export const layoutGraph = (
  layout: GraphLayout,
  nodes: GraphNodeOutput[],
  edges: GraphEdgeOutput[],
  focus?: string | null,
): Map<string, Point> => {
  switch (layout) {
    case 'hierarchical':
      return hierarchical(nodes);
    case 'radial':
      return radial(nodes, edges, focus);
    default:
      return force(nodes, edges, focus);
  }
};
