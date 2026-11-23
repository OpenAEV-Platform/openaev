import { describe, expect, it } from 'vitest';

import { type GraphNodeOutput } from '../../../../admin/components/graph/graph-model';
import { DEFAULT_FILTERS, fromSlice, visibleGraph } from '../../../../admin/components/graph/graph-state';
import { dateBounds, followBounds, inTimeRange } from '../../../../admin/components/graph/graph-time-range';

const DAY = 24 * 60 * 60 * 1000;

const node = (id: string, date?: string, type: GraphNodeOutput['node_type'] = 'SIMULATION'): GraphNodeOutput => ({
  node_id: `${type}:${id}`,
  node_type: type,
  node_entity_id: id,
  node_label: id,
  node_failed_count: 0,
  node_finding_count: 0,
  node_date: date,
});

describe('graph time range', () => {
  it('spans the dated nodes in whole days and ignores undated ones', () => {
    const bounds = dateBounds([node('a', '2026-11-01T10:00:00Z'), node('b', '2026-11-05T08:00:00Z'), node('c', undefined, 'ASSET')]);
    expect(bounds).toEqual([Date.parse('2026-11-01T00:00:00Z'), Date.parse('2026-11-06T00:00:00Z') - 1]);
  });

  it('is absent when fewer than two days are dated', () => {
    expect(dateBounds([node('c', undefined, 'ASSET')])).toBeUndefined();
    expect(dateBounds([node('a', '2026-11-01T10:00:00Z'), node('b', '2026-11-01T18:00:00Z')])).toBeUndefined();
  });

  it('keeps undated nodes and drops dated ones outside the range', () => {
    const range: [number, number] = [Date.parse('2026-11-03T00:00:00Z'), Date.parse('2026-11-04T00:00:00Z')];
    expect(inTimeRange(node('c', undefined, 'ASSET'), range)).toBe(true);
    expect(inTimeRange(node('a', '2026-11-01T10:00:00Z'), range)).toBe(false);
    expect(inTimeRange(node('b', '2026-11-03T10:00:00Z'), range)).toBe(true);
    expect(inTimeRange(node('a', '2026-11-01T10:00:00Z'), null)).toBe(true);
  });

  it('filters the visible graph, the asset the simulations hang on staying', () => {
    const asset = node('h', undefined, 'ASSET');
    const old = node('old', '2026-10-01T10:00:00Z');
    const recent = node('recent', '2026-11-10T10:00:00Z');
    const state = fromSlice({
      graph_focus: null,
      graph_nodes: [asset, old, recent],
      graph_edges: [],
      graph_truncated: false,
      graph_path: [],
    });
    const { nodes } = visibleGraph(state, {
      ...DEFAULT_FILTERS,
      timeRange: [Date.parse('2026-11-01T00:00:00Z'), Date.parse('2026-11-30T00:00:00Z')],
    });
    expect(nodes.map(n => n.node_id).sort()).toEqual([asset.node_id, recent.node_id].sort());
  });

  it('follows widened bounds when never narrowed, else clamps to them', () => {
    const previous: [number, number] = [0, 10 * DAY];
    const next: [number, number] = [0, 20 * DAY];
    expect(followBounds([0, 10 * DAY], previous, next)).toBeNull();
    expect(followBounds([2 * DAY, 5 * DAY], previous, next)).toEqual([2 * DAY, 5 * DAY]);
    expect(followBounds([2 * DAY, 5 * DAY], previous, [3 * DAY, 4 * DAY])).toEqual([3 * DAY, 4 * DAY]);
    expect(followBounds([2 * DAY, 5 * DAY], previous, undefined)).toBeNull();
  });
});
