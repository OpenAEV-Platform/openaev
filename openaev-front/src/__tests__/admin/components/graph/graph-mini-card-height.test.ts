import { describe, expect, it } from 'vitest';

import { MIN_NODE_DY, MINI_CARD_MIN_HEIGHT, MINI_CARD_PADDING, miniCardHeight } from '../../../../admin/components/graph/graph-layout';

const BOX = 68;

describe('miniCardHeight', () => {
  it('keeps the card minimum for an empty or a one-row layout', () => {
    expect(miniCardHeight(new Map(), BOX)).toBe(MINI_CARD_MIN_HEIGHT);
    expect(miniCardHeight(new Map([['a', {
      x: 0,
      y: 0,
    }]]), BOX)).toBe(MINI_CARD_MIN_HEIGHT);
  });

  it('fits the top and bottom rows with the card margin, so no node is cut (#9448, UX round 2 B3)', () => {
    // Three rows, as the mini card layout of an asset with links and groups: top row at -90, bottom at +90.
    const positions = new Map([
      ['top', {
        x: 150,
        y: -MIN_NODE_DY,
      }],
      ['focus', {
        x: 0,
        y: 0,
      }],
      ['bottom', {
        x: -150,
        y: MIN_NODE_DY,
      }],
    ]);
    const height = miniCardHeight(positions, BOX);
    expect(height).toBeGreaterThanOrEqual(2 * MIN_NODE_DY + BOX + 2 * MINI_CARD_PADDING);
    expect(height).toBeGreaterThan(MINI_CARD_MIN_HEIGHT);
  });

  it('rounds up to whole pixels', () => {
    const positions = new Map([['a', {
      x: 0,
      y: 0.4,
    }], ['b', {
      x: 0,
      y: 300,
    }]]);
    expect(Number.isInteger(miniCardHeight(positions, BOX))).toBe(true);
  });
});
