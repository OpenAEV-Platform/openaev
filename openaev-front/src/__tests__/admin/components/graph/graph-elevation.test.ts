import { describe, expect, it } from 'vitest';

import { GROUP_THRESHOLD, groupId, groupNeighbours, isGroupId, MINI_SHOWN, miniView, rankNeighbours } from '../../../../admin/components/graph/graph-grouping';
import { extendLayout, MIN_NODE_DX, MIN_NODE_DY, miniCardLayout } from '../../../../admin/components/graph/graph-layout';
import { type GraphEdgeOutput, type GraphNodeOutput, type GraphNodeType, type GraphOutput } from '../../../../admin/components/graph/graph-model';
import { EMPTY_STATE, expandSlice, fromSlice, showHidden, ungroup } from '../../../../admin/components/graph/graph-state';

const node = (id: string, type: GraphNodeType = 'ASSET', failed = 0, findings = 0): GraphNodeOutput => ({
  node_id: id,
  node_entity_id: id,
  node_type: type,
  node_label: id,
  node_failed_count: failed,
  node_finding_count: findings,
});

const edge = (source: string, target: string): GraphEdgeOutput => ({
  edge_id: `${source}-${target}`,
  edge_type: 'TARGETS',
  edge_source: source,
  edge_target: target,
});

const slice = (focus: string, nodes: GraphNodeOutput[], edges: GraphEdgeOutput[]): GraphOutput => ({
  graph_focus: focus,
  graph_nodes: nodes,
  graph_edges: edges,
  graph_truncated: false,
  graph_path: [],
});

const noOverlap = (positions: Map<string, {
  x: number;
  y: number;
}>) => {
  const points = [...positions.values()];
  points.forEach((p, i) => points.slice(i + 1).forEach((q) => {
    expect(Math.abs(p.x - q.x) >= MIN_NODE_DX || Math.abs(p.y - q.y) >= MIN_NODE_DY).toBe(true);
  }));
};

describe('expand in place (OpenCTI investigation)', () => {
  it('keeps every node already placed where it is and places the newcomers without overlap', () => {
    const placed = new Map([
      ['a', {
        x: 0,
        y: 0,
      }],
      ['b', {
        x: 300,
        y: 0,
      }],
    ]);
    const nodes = [node('a'), node('b'), ...['n1', 'n2', 'n3', 'n4', 'n5'].map(id => node(id, 'INJECT'))];
    const edges = [edge('a', 'b'), ...['n1', 'n2', 'n3', 'n4', 'n5'].map(id => edge('b', id))];
    const positions = extendLayout(placed, nodes, edges);
    expect(positions.get('a')).toEqual({
      x: 0,
      y: 0,
    });
    expect(positions.get('b')).toEqual({
      x: 300,
      y: 0,
    });
    expect(positions.size).toBe(7);
    noOverlap(positions);
    // The newcomers open on the side of b facing away from a.
    ['n1', 'n2', 'n3', 'n4', 'n5'].forEach(id => expect((positions.get(id) as { x: number }).x).toBeGreaterThan(0));
  });

  it('returns the same positions when nothing enters', () => {
    const placed = new Map([['a', {
      x: 10,
      y: 20,
    }]]);
    expect(extendLayout(placed, [node('a')], [])).toEqual(placed);
  });

  it('merges the expansion into the graph on screen and moves the focus, never drops a node', () => {
    const start = fromSlice(slice('a', [node('a'), node('b')], [edge('a', 'b')]));
    const next = expandSlice(start, slice('b', [node('b'), node('c', 'FINDING'), node('d', 'INJECT')], [edge('b', 'c'), edge('b', 'd')]), 'b');
    expect(Object.keys(next.nodes).sort()).toEqual(['a', 'b', 'c', 'd']);
    expect(next.focus).toBe('b');
  });

  it('expands by entity type', () => {
    const start = fromSlice(slice('a', [node('a')], []));
    const next = expandSlice(start, slice('a', [node('a'), node('c', 'FINDING'), node('d', 'INJECT')], [edge('a', 'c'), edge('a', 'd')]), 'a', 'FINDING');
    expect(Object.keys(next.nodes).sort()).toEqual(['a', 'c']);
    expect(Object.keys(next.edges)).toEqual(['a-c']);
  });

  it('shows every hidden node again and records an opened group', () => {
    const hidden = {
      ...EMPTY_STATE,
      hidden: ['x'],
    };
    expect(showHidden(hidden).hidden).toEqual([]);
    expect(ungroup(EMPTY_STATE, 'g').ungrouped).toEqual(['g']);
    expect(ungroup(ungroup(EMPTY_STATE, 'g'), 'g').ungrouped).toEqual(['g']);
  });
});

describe('grouping of same-type neighbours (OpenCTI graph collapse)', () => {
  const leaves = Array.from({ length: GROUP_THRESHOLD + 3 }, (_, i) => node(`i${i}`, 'INJECT', i === 0 ? 2 : 0));
  const nodes = [node('hub'), node('other'), ...leaves];
  const edges = [edge('hub', 'other'), ...leaves.map(l => edge('hub', l.node_id))];

  it('folds more than the threshold of same-type leaves into one group node', () => {
    const grouped = groupNeighbours(nodes, edges);
    const group = grouped.nodes.find(n => isGroupId(n.node_id));
    expect(group?.node_id).toBe(groupId('hub', 'INJECT'));
    expect(group?.node_group_members).toHaveLength(leaves.length);
    expect(group?.node_failed_count).toBe(2);
    expect(group?.node_result).toBe('FAILED');
    expect(grouped.nodes).toHaveLength(3);
    expect(grouped.edges.map(e => e.edge_target).sort()).toEqual([groupId('hub', 'INJECT'), 'other'].sort());
  });

  it('keeps the leaves one by one once the group is opened, or when a leaf is kept', () => {
    expect(groupNeighbours(nodes, edges, [groupId('hub', 'INJECT')]).nodes).toHaveLength(nodes.length);
    const kept = groupNeighbours(nodes, edges, [], new Set(['i1']));
    expect(kept.nodes.some(n => n.node_id === 'i1')).toBe(true);
  });

  it('leaves a graph under the threshold untouched', () => {
    const small = [node('hub'), node('other'), node('x', 'INJECT')];
    const grouped = groupNeighbours(small, [edge('hub', 'other'), edge('hub', 'x')]);
    expect(grouped.nodes).toBe(small);
  });
});

describe('compact mini graph', () => {
  it('ranks failed expectations and findings first', () => {
    const ranked = rankNeighbours([node('plain', 'SCENARIO'), node('found', 'ASSET', 0, 3), node('failed', 'INJECT', 4)]);
    expect(ranked.map(n => n.node_id)).toEqual(['failed', 'found', 'plain']);
  });

  it('shows the most relevant neighbours, groups the rest by type and adds a second hop', () => {
    const ring = Array.from({ length: MINI_SHOWN + 5 }, (_, i) => node(`r${i}`, i < 3 ? 'FINDING' : 'INJECT', i === 4 ? 9 : 0));
    const hop = node('hop', 'SIMULATION');
    const nodes = [node('focus'), ...ring, hop];
    const edges = [...ring.map(r => edge('focus', r.node_id)), edge('r4', 'hop')];
    const view = miniView('focus', nodes, edges);
    const singles = view.nodes.filter(n => !isGroupId(n.node_id) && n.node_id !== 'focus');
    expect(singles.map(n => n.node_id)).toContain('r4');
    expect(singles.map(n => n.node_id)).toContain('hop');
    const groups = view.nodes.filter(n => isGroupId(n.node_id));
    expect(groups.reduce((sum, g) => sum + (g.node_group_members?.length ?? 0), 0)).toBe(5);
    const positions = miniCardLayout(view.nodes, view.edges, 'focus');
    expect(positions.get('focus')).toEqual({
      x: 0,
      y: 0,
    });
    noOverlap(positions);
    const ys = [...positions.values()].map(p => p.y);
    // A compact block: at most a few rows tall, never a full-width strip of one row.
    expect(Math.max(...ys) - Math.min(...ys)).toBeLessThanOrEqual(4 * MIN_NODE_DY);
  });
});
