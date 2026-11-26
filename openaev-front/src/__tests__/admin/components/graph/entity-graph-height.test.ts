import { describe, expect, it } from 'vitest';

import { EMBEDDED_GRAPH_BOTTOM_GAP, EMBEDDED_GRAPH_MIN_HEIGHT, embeddedGraphHeight } from '../../../../admin/components/graph/entity-graph-height';

describe('embeddedGraphHeight', () => {
  it('fills the room left under the canvas top, so the toolbar stays above the fold', () => {
    // UX round 1 B1 of #9448: the canvas started at 389 px in a 1000 px window.
    const height = embeddedGraphHeight(389, 1000);
    expect(height).toBe(1000 - 389 - EMBEDDED_GRAPH_BOTTOM_GAP);
    expect(389 + height).toBeLessThanOrEqual(1000);
  });

  it('never goes under the minimum height on a short window', () => {
    expect(embeddedGraphHeight(389, 700)).toBe(EMBEDDED_GRAPH_MIN_HEIGHT);
  });

  it('rounds down to whole pixels', () => {
    expect(Number.isInteger(embeddedGraphHeight(389.6, 1000))).toBe(true);
  });
});
