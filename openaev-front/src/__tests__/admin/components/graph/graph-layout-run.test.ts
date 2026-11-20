import { describe, expect, it } from 'vitest';

import { moveAt, planMove, type Point } from '../../../../admin/components/graph/graph-layout';
import { type GraphEdgeOutput, type GraphNodeOutput } from '../../../../admin/components/graph/graph-model';

const node = (id: string): GraphNodeOutput => ({
  node_id: id,
  node_entity_id: id,
  node_type: 'ASSET',
  node_label: id,
  node_failed_count: 0,
  node_finding_count: 0,
});

const edge = (source: string, target: string): GraphEdgeOutput => ({
  edge_id: `${source}-${target}`,
  edge_type: 'MEMBER_OF',
  edge_source: source,
  edge_target: target,
});

const nodes = ['g', 'a', 'b', 'c', 'd'].map(node);
const edges = ['a', 'b', 'c', 'd'].map(id => edge('g', id));
const last = (frames: Map<string, Point>[]) => frames[frames.length - 1];

// #9572: a layout button did nothing, or only after several clicks. The plan was written into refs
// during the render; React renders twice under StrictMode, so the replay found the run "already laid
// out", extended the positions on screen instead, and the nodes never moved.
describe('planMove: a layout run is decided by what is committed, never by the render itself', () => {
  const settled = last(planMove({
    variant: 'page',
    runKey: 'force|1',
    laidOut: '',
    settled: new Map(),
    layout: 'force',
    nodes,
    edges,
    focus: 'g',
  }).frames);

  it('plays the whole layout for a new run, and the same plan when the render is replayed', () => {
    const args = {
      variant: 'page',
      runKey: 'hierarchical|2',
      laidOut: 'force|1',
      settled,
      layout: 'hierarchical' as const,
      nodes,
      edges,
      focus: 'g',
    };
    const first = planMove(args);
    const replay = planMove(args);
    expect(last(first.frames)).toEqual(last(replay.frames));
    expect(first.fit).toBe(true);
    const moved = nodes.filter(n => JSON.stringify(last(first.frames).get(n.node_id)) !== JSON.stringify(settled.get(n.node_id)));
    expect(moved.length).toBeGreaterThan(0);
  });

  it('keeps the run target when a re-render happens during its animation (selection, URL write)', () => {
    const run = planMove({
      variant: 'page',
      runKey: 'radial|2',
      laidOut: 'force|1',
      settled,
      layout: 'radial',
      nodes,
      edges,
      focus: 'g',
    });
    const target = last(run.frames);
    // Committed: the effect records the run and its target. Mid-animation, a re-render plans again.
    const midway = planMove({
      variant: 'page',
      runKey: 'radial|2',
      laidOut: 'radial|2',
      settled: target,
      layout: 'radial',
      nodes,
      edges,
      focus: 'g',
    });
    expect(midway.frames).toHaveLength(1);
    expect(midway.frames[0]).toEqual(target);
    expect(midway.fit).toBe(false);
  });

  it('extends the committed placement for an expand: the nodes on screen stay, the newcomer is placed', () => {
    const more = [...nodes, node('e')];
    const plan = planMove({
      variant: 'page',
      runKey: 'force|1',
      laidOut: 'force|1',
      settled,
      layout: 'force',
      nodes: more,
      edges: [...edges, edge('a', 'e')],
      focus: 'g',
    });
    nodes.forEach(n => expect(plan.frames[0].get(n.node_id)).toEqual(settled.get(n.node_id)));
    expect(plan.frames[0].has('e')).toBe(true);
    expect(plan.fit).toBe(true);
  });

  it('lays the graph out when nothing is on screen yet', () => {
    const plan = planMove({
      variant: 'page',
      runKey: 'force|1',
      laidOut: 'force|1',
      settled: new Map(),
      layout: 'force',
      nodes,
      edges,
      focus: 'g',
    });
    expect(last(plan.frames).size).toBe(nodes.length);
  });
});

describe('moveAt: every share of time reads an existing frame', () => {
  const from = new Map<string, Point>([['a', {
    x: 0,
    y: 0,
  }]]);
  const steps = [new Map<string, Point>([['a', {
    x: 10,
    y: 0,
  }]]), new Map<string, Point>([['a', {
    x: 20,
    y: 0,
  }]])];

  it('starts on the screen and ends on the last frame', () => {
    expect(moveAt(from, steps, 0).get('a')).toEqual({
      x: 0,
      y: 0,
    });
    expect(moveAt(from, steps, 1).get('a')).toEqual({
      x: 20,
      y: 0,
    });
  });

  it('never throws for a timestamp before the start or past the end (#9572 console error)', () => {
    expect(moveAt(from, steps, -0.01).get('a')).toEqual({
      x: 0,
      y: 0,
    });
    expect(moveAt(from, steps, 3).get('a')).toEqual({
      x: 20,
      y: 0,
    });
    expect(moveAt(from, steps, Number.NaN).get('a')).toEqual({
      x: 20,
      y: 0,
    });
  });
});
