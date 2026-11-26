import { describe, expect, it } from 'vitest';

import { MIN_NODE_DX, MIN_NODE_DY, miniLayout } from '../../../../admin/components/graph/graph-layout';
import { type GraphNodeOutput, type GraphNodeType } from '../../../../admin/components/graph/graph-model';

const node = (id: string, type: GraphNodeType = 'ASSET'): GraphNodeOutput => ({
  node_id: id,
  node_entity_id: id,
  node_type: type,
  node_label: id,
  node_failed_count: 0,
} as GraphNodeOutput);

describe('miniLayout', () => {
  it('centres the focus and keeps a 7-neighbour ring in a short strip', () => {
    const nodes = [node('focus'), ...['a', 'b', 'c', 'd', 'e', 'f', 'g'].map(id => node(id, 'FINDING'))];
    const positions = miniLayout(nodes, 'focus');
    expect(positions.get('focus')).toEqual({
      x: 0,
      y: 0,
    });
    const ys = [...positions.values()].map(p => p.y);
    // Two rows around the focus: the strip is far shorter than the 240 px card at zoom 1.
    expect(Math.max(...ys) - Math.min(...ys)).toBeLessThanOrEqual(120);
    expect(positions.size).toBe(8);
  });

  it('never overlaps two node boxes', () => {
    const nodes = [node('focus'), ...Array.from({ length: 12 }, (_, i) => node(`n${i}`))];
    const points = [...miniLayout(nodes, 'focus').values()];
    points.forEach((p, i) => points.slice(i + 1).forEach((q) => {
      const apart = Math.abs(p.x - q.x) >= MIN_NODE_DX || Math.abs(p.y - q.y) >= MIN_NODE_DY;
      expect(apart).toBe(true);
    }));
  });

  it('puts the closest neighbours on both sides of the focus first', () => {
    const nodes = [node('focus'), node('a'), node('b'), node('c'), node('d')];
    const positions = miniLayout(nodes, 'focus');
    expect(positions.get('a')?.x).toBe(MIN_NODE_DX);
    expect(positions.get('b')?.x).toBe(MIN_NODE_DX);
    expect(positions.get('c')?.x).toBe(-MIN_NODE_DX);
    expect(positions.get('d')?.x).toBe(-MIN_NODE_DX);
  });

  it('falls back to the first node when the focus is missing, and is empty for no node', () => {
    expect(miniLayout([node('x'), node('y')], 'missing').get('x')).toEqual({
      x: 0,
      y: 0,
    });
    expect(miniLayout([], null).size).toBe(0);
  });
});
