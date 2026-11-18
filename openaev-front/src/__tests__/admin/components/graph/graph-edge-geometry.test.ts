import { describe, expect, it } from 'vitest';

import { EDGE_GAP, edgeEndpoint } from '../../../../admin/components/graph/graph-edge-geometry';

const label = (x: number, y: number) => ({
  left: x - 70,
  right: x + 70,
  top: y + 26,
  bottom: y + 44,
});

describe('edgeEndpoint', () => {
  it('starts on the circle plus the gap when the edge goes up, whatever the label', () => {
    const p = edgeEndpoint({
      x: 0,
      y: 0,
    }, {
      x: 0,
      y: -300,
    }, 22, label(0, 0));
    expect(p.x).toBeCloseTo(0);
    expect(p.y).toBeCloseTo(-(22 + EDGE_GAP));
  });

  it('leaves below the label when the edge goes straight down', () => {
    const p = edgeEndpoint({
      x: 0,
      y: 0,
    }, {
      x: 0,
      y: 300,
    }, 22, label(0, 0));
    expect(p.y).toBeCloseTo(44 + EDGE_GAP);
  });

  it('leaves past the label when the edge goes down through it at an angle', () => {
    const p = edgeEndpoint({
      x: 0,
      y: 0,
    }, {
      x: 300,
      y: 200,
    }, 22, label(0, 0));
    expect(p.y).toBeGreaterThan(44);
  });

  it('stays on the circle when a shallow edge passes beside the label', () => {
    const p = edgeEndpoint({
      x: 0,
      y: 0,
    }, {
      x: 600,
      y: 100,
    }, 22, label(0, 0));
    expect(Math.hypot(p.x, p.y)).toBeCloseTo(22 + EDGE_GAP);
  });

  it('stays on the circle when the edge is horizontal and misses the label', () => {
    const p = edgeEndpoint({
      x: 0,
      y: 0,
    }, {
      x: 300,
      y: 0,
    }, 22, label(0, 0));
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
    }, 22, null)).toEqual({
      x: 5,
      y: 5,
    });
  });
});
