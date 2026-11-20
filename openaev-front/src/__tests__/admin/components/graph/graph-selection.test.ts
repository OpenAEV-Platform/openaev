import { describe, expect, it } from 'vitest';

import { type GraphEdgeOutput, type GraphNodeOutput, type GraphOutput } from '../../../../admin/components/graph/graph-model';
import { fromSlice, hideNodes, pinNodes, removeNodes } from '../../../../admin/components/graph/graph-state';

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
  edge_type: 'TARGETS',
  edge_source: source,
  edge_target: target,
});

const slice: GraphOutput = {
  graph_focus: 'a',
  graph_nodes: [node('a'), node('b'), node('c')],
  graph_edges: [edge('a', 'b'), edge('b', 'c')],
  graph_truncated: false,
  graph_path: ['a', 'b', 'c'],
};

describe('selection actions (OpenCTI graph)', () => {
  it('removes the selected nodes and their relations from the view, never the focus', () => {
    const start = {
      ...fromSlice(slice),
      pinned: ['c'],
    };
    const next = removeNodes(start, ['a', 'c']);
    expect(Object.keys(next.nodes).sort()).toEqual(['a', 'b']);
    expect(Object.keys(next.edges)).toEqual(['a-b']);
    expect(next.pinned).toEqual([]);
    expect(next.path).toEqual([]);
  });

  it('returns the same state when only the focus is removed', () => {
    const start = fromSlice(slice);
    expect(removeNodes(start, ['a'])).toBe(start);
  });

  it('pins a selection, then unpins it when every node is already pinned', () => {
    const start = fromSlice(slice);
    const pinned = pinNodes(start, ['b', 'c']);
    expect(pinned.pinned.sort()).toEqual(['b', 'c']);
    expect(pinNodes(pinned, ['b', 'c']).pinned).toEqual([]);
  });

  it('hides a selection and drops its pins', () => {
    const start = {
      ...fromSlice(slice),
      pinned: ['b'],
    };
    const next = hideNodes(start, ['b', 'c']);
    expect(next.hidden.sort()).toEqual(['b', 'c']);
    expect(next.pinned).toEqual([]);
  });
});
