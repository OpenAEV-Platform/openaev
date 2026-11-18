import { describe, expect, it } from 'vitest';

import { EDGE_GAP, edgeEndpoint } from '../../../../admin/components/graph/graph-edge-geometry';

const origin = {
  x: 0,
  y: 0,
};

describe('edgeEndpoint', () => {
  it('ends on the circle plus the gap when the edge goes up', () => {
    const p = edgeEndpoint(origin, {
      x: 0,
      y: -300,
    }, 22);
    expect(p.x).toBeCloseTo(0);
    expect(p.y).toBeCloseTo(-(22 + EDGE_GAP));
  });

  it('ends on the circle when the edge goes straight down, never past the label', () => {
    const p = edgeEndpoint(origin, {
      x: 0,
      y: 300,
    }, 22);
    expect(p.y).toBeCloseTo(22 + EDGE_GAP);
  });

  it('ends on the circle at any angle, so the arrowhead touches its target', () => {
    [[300, 200], [-150, 400], [600, 100], [-300, -40]].forEach(([x, y]) => {
      const p = edgeEndpoint(origin, {
        x,
        y,
      }, 22);
      expect(Math.hypot(p.x, p.y)).toBeCloseTo(22 + EDGE_GAP);
    });
  });

  it('stays on the line between the two centres', () => {
    const p = edgeEndpoint(origin, {
      x: 300,
      y: 0,
    }, 22);
    expect(p.x).toBeCloseTo(22 + EDGE_GAP);
    expect(p.y).toBeCloseTo(0);
  });

  it('answers the centre for two nodes at the same place', () => {
    expect(edgeEndpoint({
      x: 5,
      y: 5,
    }, {
      x: 5,
      y: 5,
    }, 22)).toEqual({
      x: 5,
      y: 5,
    });
  });
});
