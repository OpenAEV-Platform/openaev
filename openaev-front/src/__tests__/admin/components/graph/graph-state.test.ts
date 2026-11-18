import { describe, expect, it } from 'vitest';

import { distances, layoutGraph } from '../../../../admin/components/graph/graph-layout';
import { type GraphEdgeOutput, graphHref, type GraphNodeOutput, type GraphOutput, parseNodeType } from '../../../../admin/components/graph/graph-model';
import {
  DEFAULT_FILTERS,
  encodeFocus,
  failedHighlights,
  fromSlice,
  hideNode,
  mergeSlice,
  pathEdges,
  togglePin,
  visibleGraph,
  worst,
} from '../../../../admin/components/graph/graph-state';

const node = (id: string, type: GraphNodeOutput['node_type'], extra: Partial<GraphNodeOutput> = {}): GraphNodeOutput => ({
  node_id: id,
  node_type: type,
  node_entity_id: id.split(':')[1] ?? id,
  node_label: id,
  node_failed_count: 0,
  node_finding_count: 0,
  ...extra,
});

const edge = (id: string, source: string, target: string, extra: Partial<GraphEdgeOutput> = {}): GraphEdgeOutput => ({
  edge_id: id,
  edge_type: 'TARGETS',
  edge_source: source,
  edge_target: target,
  ...extra,
});

const slice = (nodes: GraphNodeOutput[], edges: GraphEdgeOutput[], extra: Partial<GraphOutput> = {}): GraphOutput => ({
  graph_focus: null,
  graph_nodes: nodes,
  graph_edges: edges,
  graph_truncated: false,
  graph_path: [],
  ...extra,
});

const asset = node('ASSET:a1', 'ASSET', { node_failed_count: 1 });
const inject = node('INJECT:i1', 'INJECT', { node_result: 'FAILED' });
const finding = node('FINDING:f1', 'FINDING');
const group = node('ASSET_GROUP:g1', 'ASSET_GROUP');

describe('graph-state', () => {
  it('keeps the worst result', () => {
    expect(worst('SUCCESS', 'FAILED')).toBe('FAILED');
    expect(worst('PENDING', 'SUCCESS')).toBe('PENDING');
    expect(worst(null, 'SUCCESS')).toBe('SUCCESS');
    expect(worst(undefined, undefined)).toBeNull();
  });

  it('merges a slice keeping the highest counts and the worst result', () => {
    const first = fromSlice(slice([asset, inject], [edge('e1', 'INJECT:i1', 'ASSET:a1', { edge_result: 'FAILED' })], { graph_focus: 'ASSET:a1' }));
    expect(first.focus).toBe('ASSET:a1');
    const merged = mergeSlice(first, slice([{
      ...asset,
      node_failed_count: 0,
      node_finding_count: 3,
      node_result: 'SUCCESS',
    }, finding], [edge('e2', 'FINDING:f1', 'ASSET:a1', { edge_type: 'FOUND_ON' })]));
    expect(Object.keys(merged.nodes)).toHaveLength(3);
    expect(merged.nodes['ASSET:a1'].node_failed_count).toBe(1);
    expect(merged.nodes['ASSET:a1'].node_finding_count).toBe(3);
    expect(Object.keys(merged.edges)).toHaveLength(2);
    expect(merged.focus).toBe('ASSET:a1');
  });

  it('records where a truncated expansion stopped and clears it once complete', () => {
    const start = fromSlice(slice([asset], []));
    const truncated = mergeSlice(start, slice([inject], [], { graph_truncated: true }), 'ASSET:a1');
    expect(truncated.truncatedAt).toEqual(['ASSET:a1']);
    const complete = mergeSlice(truncated, slice([finding], []), 'ASSET:a1');
    expect(complete.truncatedAt).toEqual([]);
  });

  it('un-hides a node a new slice brings back', () => {
    const state = hideNode(fromSlice(slice([asset, inject], [])), 'INJECT:i1');
    expect(state.hidden).toEqual(['INJECT:i1']);
    expect(mergeSlice(state, slice([inject], [])).hidden).toEqual([]);
  });

  it('pins and unpins, and a hidden node is unpinned', () => {
    const pinned = togglePin(fromSlice(slice([asset], [])), 'ASSET:a1');
    expect(pinned.pinned).toEqual(['ASSET:a1']);
    expect(togglePin(pinned, 'ASSET:a1').pinned).toEqual([]);
    expect(hideNode(pinned, 'ASSET:a1').pinned).toEqual([]);
  });

  it('filters by type and result, keeps the focus, and drops dangling edges', () => {
    const state = {
      ...fromSlice(slice([asset, inject, finding], [edge('e1', 'INJECT:i1', 'ASSET:a1'), edge('e2', 'FINDING:f1', 'ASSET:a1', { edge_type: 'FOUND_ON' })])),
      focus: 'ASSET:a1',
    };
    const view = visibleGraph(state, {
      ...DEFAULT_FILTERS,
      nodeTypes: ['FINDING'],
    });
    expect(view.nodes.map(n => n.node_id).sort()).toEqual(['ASSET:a1', 'FINDING:f1']);
    expect(view.edges.map(e => e.edge_id)).toEqual(['e2']);
    const noFailed = visibleGraph(state, {
      ...DEFAULT_FILTERS,
      results: ['SUCCESS', 'PENDING'],
    });
    expect(noFailed.nodes.map(n => n.node_id)).not.toContain('INJECT:i1');
  });

  it('focus mode keeps the focus and its first ring only, search marks matches', () => {
    const state = {
      ...fromSlice(slice([asset, inject, group, finding], [edge('e1', 'INJECT:i1', 'ASSET:a1'), edge('e2', 'ASSET:a1', 'ASSET_GROUP:g1', { edge_type: 'MEMBER_OF' }), edge('e3', 'FINDING:f1', 'INJECT:i1', { edge_type: 'PRODUCED' })])),
      focus: 'ASSET:a1',
    };
    const view = visibleGraph(state, {
      ...DEFAULT_FILTERS,
      focusMode: true,
      search: 'group',
    });
    expect(view.nodes.map(n => n.node_id).sort()).toEqual(['ASSET:a1', 'ASSET_GROUP:g1', 'INJECT:i1']);
    expect([...view.matches]).toEqual(['ASSET_GROUP:g1']);
  });

  it('highlights failed edges and their ends, and the edges of a path', () => {
    const edges = [edge('e1', 'INJECT:i1', 'ASSET:a1', { edge_result: 'FAILED' }), edge('e2', 'ASSET:a1', 'ASSET_GROUP:g1', { edge_result: 'SUCCESS' })];
    const failed = failedHighlights(edges);
    expect([...failed.edgeIds]).toEqual(['e1']);
    expect([...failed.nodeIds].sort()).toEqual(['ASSET:a1', 'INJECT:i1']);
    expect([...pathEdges(['ASSET_GROUP:g1', 'ASSET:a1', 'INJECT:i1'], edges)].sort()).toEqual(['e1', 'e2']);
  });

  it('encodes the focus for the shareable URL', () => {
    expect(encodeFocus('ASSET:abc')).toBe('asset:abc');
    expect(encodeFocus(null)).toBeUndefined();
  });
});

describe('graph-model', () => {
  it('parses node types and builds the focused graph link', () => {
    expect(parseNodeType('asset_group')).toBe('ASSET_GROUP');
    expect(parseNodeType('nope')).toBeUndefined();
    expect(graphHref('ASSET', 'a b')).toBe('/admin/graph?focus=asset:a%20b');
  });
});

describe('graph-layout', () => {
  const nodes = [asset, inject, group, finding];
  const edges = [edge('e1', 'INJECT:i1', 'ASSET:a1'), edge('e2', 'ASSET:a1', 'ASSET_GROUP:g1')];

  it('measures breadth-first distances from the focus, unreachable last', () => {
    const d = distances(nodes, edges, 'ASSET:a1');
    expect(d.get('ASSET:a1')).toBe(0);
    expect(d.get('INJECT:i1')).toBe(1);
    expect(d.get('ASSET_GROUP:g1')).toBe(1);
    expect(d.get('FINDING:f1')).toBe(2);
  });

  it.each(['force', 'hierarchical', 'radial'] as const)('places every node with finite coordinates (%s)', (layout) => {
    const positions = layoutGraph(layout, nodes, edges, 'ASSET:a1');
    expect(positions.size).toBe(nodes.length);
    positions.forEach((p) => {
      expect(Number.isFinite(p.x)).toBe(true);
      expect(Number.isFinite(p.y)).toBe(true);
    });
  });
});
