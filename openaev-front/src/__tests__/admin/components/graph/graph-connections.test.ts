import { describe, expect, it } from 'vitest';

import { drawnRelations, nodeConnections } from '../../../../admin/components/graph/graph-connections';
import { type GraphEdgeOutput, type GraphNodeOutput } from '../../../../admin/components/graph/graph-model';
import { fromSlice, mergeSlice } from '../../../../admin/components/graph/graph-state';

const asset = (counts?: GraphNodeOutput['node_relation_counts']): GraphNodeOutput => ({
  node_id: 'ASSET:a1',
  node_type: 'ASSET',
  node_entity_id: 'a1',
  node_label: 'host-1',
  node_failed_count: 0,
  node_finding_count: 0,
  node_relation_counts: counts,
});

const edge = (id: string, type: GraphEdgeOutput['edge_type'], source: string, target: string): GraphEdgeOutput => ({
  edge_id: id,
  edge_type: type,
  edge_source: source,
  edge_target: target,
});

describe('graph connection counts (#9572)', () => {
  it('counts the relations in the data against the ones drawn, per type', () => {
    const drawn = drawnRelations([
      edge('e1', 'TARGETS', 'INJECT:i1', 'ASSET:a1'),
      edge('e2', 'FOUND_ON', 'FINDING:f1', 'ASSET:a1'),
    ]);
    const c = nodeConnections(asset({
      TARGETS: 12,
      FOUND_ON: 1,
      MEMBER_OF: 2,
    }), drawn);
    expect(c).not.toBeNull();
    expect(c?.total).toBe(15);
    expect(c?.shown).toBe(2);
    expect(c?.hidden).toBe(13);
    expect(c?.byType.find(r => r.type === 'TARGETS')).toEqual({
      type: 'TARGETS',
      total: 12,
      shown: 1,
    });
  });

  it('never reports a negative "not shown" when more is drawn than counted', () => {
    const drawn = drawnRelations([
      edge('e1', 'MEMBER_OF', 'ASSET:a1', 'ASSET_GROUP:g1'),
      edge('e2', 'MEMBER_OF', 'ASSET:a1', 'ASSET_GROUP:g2'),
    ]);
    const c = nodeConnections(asset({ MEMBER_OF: 1 }), drawn);
    expect(c?.total).toBe(2);
    expect(c?.hidden).toBe(0);
  });

  it('is null for a node the API did not count', () => {
    expect(nodeConnections(asset(undefined), new Map())).toBeNull();
  });

  it('keeps the newest counts when a slice is merged', () => {
    const state = fromSlice({
      graph_focus: null,
      graph_nodes: [asset({ TARGETS: 3 })],
      graph_edges: [],
      graph_truncated: false,
      graph_path: [],
    });
    const merged = mergeSlice(state, {
      graph_focus: null,
      graph_nodes: [asset({ TARGETS: 5 })],
      graph_edges: [],
      graph_truncated: false,
      graph_path: [],
    });
    expect(merged.nodes['ASSET:a1'].node_relation_counts).toEqual({ TARGETS: 5 });
    const kept = mergeSlice(merged, {
      graph_focus: null,
      graph_nodes: [asset(undefined)],
      graph_edges: [],
      graph_truncated: false,
      graph_path: [],
    });
    expect(kept.nodes['ASSET:a1'].node_relation_counts).toEqual({ TARGETS: 5 });
  });
});
