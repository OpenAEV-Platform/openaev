import { describe, expect, it } from 'vitest';

import { NODE_CIRCLE_SIZE, NODE_WIDTH } from '../../../../admin/components/graph/graph-edge-geometry';
import { LASSO_MIN_LENGTH, nodesInLasso, pathLength, pointInPolygon } from '../../../../admin/components/graph/graph-lasso';

const square = [
  {
    x: 0,
    y: 0,
  },
  {
    x: 100,
    y: 0,
  },
  {
    x: 100,
    y: 100,
  },
  {
    x: 0,
    y: 100,
  },
];

// A node box whose circle centre lands on (cx, cy).
const at = (cx: number, cy: number) => ({
  x: cx - NODE_WIDTH / 2,
  y: cy - NODE_CIRCLE_SIZE / 2,
});

describe('lasso selection (OpenCTI LassoSelection)', () => {
  it('tells a point inside a polygon from one outside', () => {
    expect(pointInPolygon({
      x: 50,
      y: 50,
    }, square)).toBe(true);
    expect(pointInPolygon({
      x: 150,
      y: 50,
    }, square)).toBe(false);
  });

  it('handles a concave outline', () => {
    // A U shape: the notch between the arms is outside.
    const u = [
      {
        x: 0,
        y: 0,
      },
      {
        x: 30,
        y: 0,
      },
      {
        x: 30,
        y: 70,
      },
      {
        x: 70,
        y: 70,
      },
      {
        x: 70,
        y: 0,
      },
      {
        x: 100,
        y: 0,
      },
      {
        x: 100,
        y: 100,
      },
      {
        x: 0,
        y: 100,
      },
    ];
    expect(pointInPolygon({
      x: 50,
      y: 30,
    }, u)).toBe(false);
    expect(pointInPolygon({
      x: 15,
      y: 30,
    }, u)).toBe(true);
  });

  it('selects the nodes whose circle centre the outline surrounds', () => {
    const positions = new Map([
      ['in', at(50, 50)],
      ['out', at(300, 50)],
      ['edge-box', at(110, 50)],
    ]);
    expect(nodesInLasso(square, positions)).toEqual(['in']);
  });

  it('selects nothing from a degenerate outline', () => {
    const positions = new Map([['in', at(50, 50)]]);
    expect(nodesInLasso(square.slice(0, 2), positions)).toEqual([]);
  });

  it('measures the outline so a click is never a lasso', () => {
    expect(pathLength(square)).toBe(300);
    expect(pathLength([{
      x: 0,
      y: 0,
    }, {
      x: 3,
      y: 4,
    }])).toBeLessThan(LASSO_MIN_LENGTH);
  });
});
