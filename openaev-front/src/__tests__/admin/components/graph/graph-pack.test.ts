import { describe, expect, it } from 'vitest';

import { PACK_GAP, packBubbles } from '../../../../admin/components/graph/graph-pack';

const inputs = (weights: number[]) => weights.map((weight, index) => ({
  id: `g${index}`,
  weight,
}));

describe('packBubbles', () => {
  it('gives nothing for no group or a canvas without size', () => {
    expect(packBubbles([], 800, 600)).toEqual([]);
    expect(packBubbles(inputs([3]), 0, 600)).toEqual([]);
    expect(packBubbles(inputs([3]), 800, 0)).toEqual([]);
  });

  it('centres a single bubble and makes it fill the tighter side', () => {
    const [bubble] = packBubbles(inputs([10]), 800, 600);
    expect(bubble.x).toBeCloseTo(400);
    expect(bubble.y).toBeCloseTo(300);
    expect(bubble.r).toBeCloseTo((600 - 2 * PACK_GAP) / 2);
  });

  it('keeps every bubble inside the canvas and never overlaps two of them', () => {
    const width = 1200;
    const height = 640;
    const packed = packBubbles(inputs([500, 120, 80, 80, 40, 12, 5, 1, 1, 0, 300, 60]), width, height);
    expect(packed).toHaveLength(12);
    packed.forEach((b) => {
      expect(b.x - b.r).toBeGreaterThanOrEqual(-0.5);
      expect(b.y - b.r).toBeGreaterThanOrEqual(-0.5);
      expect(b.x + b.r).toBeLessThanOrEqual(width + 0.5);
      expect(b.y + b.r).toBeLessThanOrEqual(height + 0.5);
    });
    for (let i = 0; i < packed.length; i += 1) {
      for (let j = i + 1; j < packed.length; j += 1) {
        const a = packed[i];
        const b = packed[j];
        expect(Math.hypot(a.x - b.x, a.y - b.y)).toBeGreaterThanOrEqual(a.r + b.r - 0.5);
      }
    }
  });

  it('fills the canvas: the pack touches the canvas on its tighter side', () => {
    const width = 1200;
    const height = 640;
    const packed = packBubbles(inputs([500, 120, 80, 80, 40, 12, 5, 1]), width, height);
    const minX = Math.min(...packed.map(b => b.x - b.r));
    const maxX = Math.max(...packed.map(b => b.x + b.r));
    const minY = Math.min(...packed.map(b => b.y - b.r));
    const maxY = Math.max(...packed.map(b => b.y + b.r));
    const fillsWidth = Math.abs(maxX - minX - (width - 2 * PACK_GAP)) < 1;
    const fillsHeight = Math.abs(maxY - minY - (height - 2 * PACK_GAP)) < 1;
    expect(fillsWidth || fillsHeight).toBe(true);
  });

  it('draws larger groups as larger bubbles, and the same data the same way', () => {
    const first = packBubbles(inputs([400, 100, 25]), 900, 600);
    const second = packBubbles(inputs([400, 100, 25]), 900, 600);
    expect(second).toEqual(first);
    const radius = (id: string) => first.find(b => b.id === id)?.r ?? 0;
    expect(radius('g0')).toBeGreaterThan(radius('g1'));
    expect(radius('g1')).toBeGreaterThan(radius('g2'));
  });
});
