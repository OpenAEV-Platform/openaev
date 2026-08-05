import { describe, expect, it } from 'vitest';

import { buildLogicGraphLayout } from '../../../../../../admin/components/chaining/logic/logic-graph/layout';
import type { ActionMeta, EventMeta } from '../../../../../../admin/components/chaining/logic/types';

// Minimal metas: the layout only reads step_condition_ids on actions and formData.conditionGroups
// on events (for the inferred edges), plus the tactic inputs. Cast the rest away.
const action = (stepConditionIds: string[] = []): ActionMeta =>
  ({ step_condition_ids: stepConditionIds } as unknown as ActionMeta);
const event = (): EventMeta =>
  ({ formData: { conditionGroups: [] } } as unknown as EventMeta);

// Two tactics (Discovery before Lateral Movement); a1+a2 in Discovery, a3 in Lateral Movement.
// e1 gates a1, e2 gates a3.
const build = () => buildLogicGraphLayout({
  actionMetas: {
    a1: action(['e1']),
    a2: action([]),
    a3: action(['e2']),
  },
  eventMetas: {
    e1: event(),
    e2: event(),
  },
  outputProviders: {},
  tacticForStep: {
    a1: 'Discovery',
    a2: 'Discovery',
    a3: 'Lateral Movement',
  },
  tacticOrder: {
    'Discovery': 1,
    'Lateral Movement': 2,
  },
});

describe('buildLogicGraphLayout tactic columns', () => {
  it('groups actions of the same tactic into one column, ordered by phase', () => {
    const { nodeById, columns } = build();

    // a1 and a2 share the Discovery column; a3 is in a later (further-right) column.
    expect(nodeById.a1.x).toBe(nodeById.a2.x);
    expect(nodeById.a3.x).toBeGreaterThan(nodeById.a1.x);
    // Stacked vertically within the shared column.
    expect(nodeById.a1.y).not.toBe(nodeById.a2.y);

    // Columns follow the MITRE phase order, each a sized band.
    expect(columns.map(c => c.tactic)).toEqual(['Discovery', 'Lateral Movement']);
    expect(columns[0].x).toBeLessThan(columns[1].x);
    expect(columns[0].height).toBeGreaterThan(0);
    // Each action sits inside its tactic's band.
    expect(nodeById.a1.x).toBeGreaterThanOrEqual(columns[0].x);
    expect(nodeById.a1.x + nodeById.a1.width).toBeLessThanOrEqual(columns[0].x + columns[0].width);
    expect(nodeById.a3.x).toBeGreaterThanOrEqual(columns[1].x);
  });

  it('places each gating event just left of its action, vertically aligned', () => {
    const { nodeById } = build();

    // e1 sits left of the action it gates (a1) and is centered on it.
    expect(nodeById.e1.x).toBeLessThan(nodeById.a1.x);
    const e1Center = nodeById.e1.y + nodeById.e1.height / 2;
    const a1Center = nodeById.a1.y + nodeById.a1.height / 2;
    expect(Math.round(e1Center)).toBe(Math.round(a1Center));

    // e2 gates a3 (second column), so it sits in that column's lane — left of a3 but right of the
    // first column's actions, not dumped in the far-left lane.
    expect(nodeById.e2.x).toBeLessThan(nodeById.a3.x);
    expect(nodeById.e2.x).toBeGreaterThan(nodeById.a1.x);
  });

  it('keeps events outside the tactic bands (only actions carry a TTP)', () => {
    const { nodeById, columns } = build();
    // Every event lies fully to the left of every tactic band (no event overlaps a band's x-range).
    for (const eventId of ['e1', 'e2']) {
      const ev = nodeById[eventId];
      for (const col of columns) {
        const insideBand = ev.x + ev.width > col.x && ev.x < col.x + col.width;
        expect(insideBand).toBe(false);
      }
    }
  });

  it('cascades events sharing a lane down without overlapping', () => {
    // e1 and e2 both gate a1: same lane, same anchor. The first is centered on a1, the second must
    // cascade below it (align, then de-overlap) instead of stacking on the same Y.
    const { nodeById } = buildLogicGraphLayout({
      actionMetas: { a1: action(['e1', 'e2']) },
      eventMetas: {
        e1: event(),
        e2: event(),
      },
      outputProviders: {},
      tacticForStep: { a1: 'Discovery' },
      tacticOrder: { Discovery: 1 },
    });
    expect(nodeById.e1.x).toBe(nodeById.e2.x);
    const [first, second] = [nodeById.e1, nodeById.e2].sort((a, b) => a.y - b.y);
    expect(second.y).toBeGreaterThanOrEqual(first.y + first.height);
  });

  it('sorts a tactic missing from the order map after the known ones', () => {
    const { columns } = buildLogicGraphLayout({
      actionMetas: {
        a1: action(),
        a2: action(),
      },
      eventMetas: {},
      outputProviders: {},
      tacticForStep: {
        a1: 'Alpha Unknown',
        a2: 'Lateral Movement',
      },
      // 'Alpha Unknown' is absent from the order map: it must fall to the end even though it would
      // come first alphabetically.
      tacticOrder: { 'Lateral Movement': 8 },
    });
    expect(columns.map(c => c.tactic)).toEqual(['Lateral Movement', 'Alpha Unknown']);
  });

  it('puts an event gating actions in several columns in the leftmost lane', () => {
    // e1 gates both a2 (second column) and a1 (first column): it must take the FIRST column's lane
    // (left of a1), keeping the graph reading left to right.
    const { nodeById } = buildLogicGraphLayout({
      actionMetas: {
        a1: action(['e1']),
        a2: action(['e1']),
      },
      eventMetas: { e1: event() },
      outputProviders: {},
      tacticForStep: {
        a1: 'Discovery',
        a2: 'Lateral Movement',
      },
      tacticOrder: {
        'Discovery': 1,
        'Lateral Movement': 2,
      },
    });
    expect(nodeById.e1.x).toBeLessThan(nodeById.a1.x);
    const e1Center = nodeById.e1.y + nodeById.e1.height / 2;
    const a1Center = nodeById.a1.y + nodeById.a1.height / 2;
    expect(Math.round(e1Center)).toBe(Math.round(a1Center));
  });

  it('returns an empty layout (no columns) for an empty graph', () => {
    const empty = buildLogicGraphLayout({
      actionMetas: {},
      eventMetas: {},
      outputProviders: {},
      tacticForStep: {},
      tacticOrder: {},
    });
    expect(empty.nodes).toHaveLength(0);
    expect(empty.columns).toHaveLength(0);
  });
});
