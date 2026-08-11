import { describe, expect, it } from 'vitest';

import type { OutputProviderEntry } from '../../../../../../admin/components/chaining/logic/logic-flow-helpers';
import {
  ACTION_NODE_HEIGHT,
  buildLogicGraphLayout,
  type LogicGraphColumn,
  type LogicGraphNode,
} from '../../../../../../admin/components/chaining/logic/logic-graph/layout';
import type { ActionMeta, EventMeta } from '../../../../../../admin/components/chaining/logic/types';

// Minimal metas: the layout only reads step_condition_ids on actions and formData.conditionGroups
// on events (for the inferred edges), plus the tactic inputs. Cast the rest away.
const action = (stepConditionIds: string[] = []): ActionMeta =>
  ({ step_condition_ids: stepConditionIds } as unknown as ActionMeta);
const event = (): EventMeta =>
  ({ formData: { conditionGroups: [] } } as unknown as EventMeta);
/** An event listening on one finding field, so `outputProviders` can wire an inferred producer edge. */
const eventOn = (field: string): EventMeta =>
  ({
    formData: {
      conditionGroups: [{
        conditions: [{ field }],
        subGroups: [],
      }],
    },
  } as unknown as EventMeta);

/** Do two boxes share any surface? Touching edges do NOT count as overlapping. */
const overlaps = (
  a: {
    x: number;
    y: number;
    width: number;
    height: number;
  },
  b: {
    x: number;
    y: number;
    width: number;
    height: number;
  },
) => a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height;

// A three-step chain: e1 gates a1, a2 is gated by e2, a3 by e3. Discovery = a1 + a2, Lateral
// Movement = a3.
const build = () => buildLogicGraphLayout({
  actionMetas: {
    a1: action(['e1']),
    a2: action(['e2']),
    a3: action(['e3']),
  },
  eventMetas: {
    e1: event(),
    e2: event(),
    e3: event(),
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

describe('buildLogicGraphLayout tactic-column layout', () => {
  it('puts every action of a tactic in that tactic\'s column, ordered by kill-chain phase', () => {
    const { nodeById } = build();

    // Same tactic -> same column, stacked; different tactic -> a column further right (phase order).
    expect(nodeById.a1.x).toBe(nodeById.a2.x);
    expect(nodeById.a1.y).not.toBe(nodeById.a2.y);
    expect(nodeById.a3.x).toBeGreaterThan(nodeById.a1.x);
  });

  it('places a gating event in the lane immediately left of the action it gates', () => {
    const { nodeById } = build();
    expect(nodeById.e1.x).toBeLessThan(nodeById.a1.x);
    expect(nodeById.e2.x).toBeLessThan(nodeById.a2.x);
    expect(nodeById.e3.x).toBeLessThan(nodeById.a3.x);
    // Aligned on its action's center, so the event -> action arrow stays horizontal.
    expect(nodeById.e1.y + nodeById.e1.height / 2).toBe(nodeById.a1.y + nodeById.a1.height / 2);
  });

  it('normalizes the layout to the origin (no negative coordinates)', () => {
    const { nodes, columns, bbox } = build();
    for (const node of nodes) {
      expect(node.x).toBeGreaterThanOrEqual(0);
      expect(node.y).toBeGreaterThanOrEqual(0);
    }
    for (const column of columns) {
      expect(column.x).toBeGreaterThanOrEqual(0);
      expect(column.y).toBeGreaterThanOrEqual(0);
    }
    expect(bbox.minX).toBe(0);
    expect(bbox.minY).toBe(0);
  });
});

describe('buildLogicGraphLayout tactic columns', () => {
  it('keeps a real trigger->action edge when a reciprocal inferred edge exists', () => {
    const { edges } = buildLogicGraphLayout({
      actionMetas: { a1: action(['e1']) },
      eventMetas: { e1: eventOn('text') },
      outputProviders: { text: [{ stepId: 'a1' } as unknown as OutputProviderEntry] },
      tacticForStep: { a1: 'Discovery' },
      tacticOrder: { Discovery: 1 },
    });
    expect(edges.some(e => e.kind === 'real' && e.source === 'e1' && e.target === 'a1')).toBe(true);
  });

  it('emits exactly one band per tactic, in kill-chain phase order', () => {
    const { columns } = build();
    expect(columns.map(c => c.tactic)).toEqual(['Discovery', 'Lateral Movement']);
    for (const column of columns) {
      expect(column.width).toBeGreaterThan(0);
      expect(column.height).toBeGreaterThan(0);
      expect(column.headerHeight).toBeGreaterThan(0);
    }
  });

  it('pads each band around its action cards (cards never sit flush on the border)', () => {
    const { nodeById, columns } = build();
    const discovery = columns.find(c => c.tactic === 'Discovery')!;
    for (const id of ['a1', 'a2']) {
      const node = nodeById[id];
      expect(node.x).toBeGreaterThan(discovery.x);
      expect(node.x + node.width).toBeLessThan(discovery.x + discovery.width);
      expect(node.y + node.height).toBeLessThan(discovery.y + discovery.height);
      // Header room on top: the card starts below the reserved header band.
      expect(node.y).toBeGreaterThanOrEqual(discovery.y + discovery.headerHeight);
    }
  });

  it('never overlaps anything: band vs band, card vs card, band vs foreign card', () => {
    // A denser graph than `build()`: several tactics, several actions each, and events shared
    // between actions of the same tactic (the shape that made the old bounding hulls overlap).
    const { nodes, columns, nodeById } = buildLogicGraphLayout({
      actionMetas: {
        scan1: action(),
        scan2: action(),
        exec1: action(['smbFound']),
        exec2: action(['smbFound']),
        disco1: action(['ldapFound']),
        creds1: action(['event4']),
        impair1: action(['event4']),
        other1: action(),
      },
      eventMetas: {
        smbFound: event(),
        ldapFound: event(),
        event4: event(),
      },
      outputProviders: {},
      tacticForStep: {
        scan1: 'Reconnaissance',
        scan2: 'Reconnaissance',
        exec1: 'Execution',
        exec2: 'Execution',
        disco1: 'Discovery',
        creds1: 'Credential Access',
        impair1: 'Defense Impairment',
        // other1 has no tactic at all -> the "Other" column.
      },
      tacticOrder: {
        'Reconnaissance': 1,
        'Execution': 2,
        'Discovery': 3,
        'Credential Access': 4,
        'Defense Impairment': 5,
      },
    });

    const box = (n: LogicGraphNode | LogicGraphColumn) => ({
      x: n.x,
      y: n.y,
      width: n.width,
      height: n.height,
    });

    // 1. No two tactic bands intersect.
    for (let i = 0; i < columns.length; i += 1) {
      for (let j = i + 1; j < columns.length; j += 1) {
        expect(overlaps(box(columns[i]), box(columns[j]))).toBe(false);
      }
    }

    // 2. No two cards intersect.
    for (let i = 0; i < nodes.length; i += 1) {
      for (let j = i + 1; j < nodes.length; j += 1) {
        expect(overlaps(box(nodes[i]), box(nodes[j]))).toBe(false);
      }
    }

    // 3. A band only ever contains its own tactic's action cards: every other card (foreign tactic,
    //    and every event, which carries no TTP) stays fully outside it.
    const tacticOfNode: Record<string, string> = {
      scan1: 'Reconnaissance',
      scan2: 'Reconnaissance',
      exec1: 'Execution',
      exec2: 'Execution',
      disco1: 'Discovery',
      creds1: 'Credential Access',
      impair1: 'Defense Impairment',
      other1: '',
    };
    for (const column of columns) {
      for (const node of nodes) {
        if (node.kind === 'action' && tacticOfNode[node.id] === column.tactic) continue;
        expect(overlaps(box(node), box(column))).toBe(false);
      }
    }

    // 4. One tactic per column: no two tactics share an x.
    const tacticsByX = new Map<number, Set<string>>();
    for (const node of nodes) {
      if (node.kind !== 'action') continue;
      const set = tacticsByX.get(node.x) ?? new Set<string>();
      set.add(tacticOfNode[node.id]);
      tacticsByX.set(node.x, set);
    }
    for (const set of tacticsByX.values()) expect(set.size).toBe(1);

    // Sanity: the shared trigger sits left of both actions it gates.
    expect(nodeById.smbFound.x).toBeLessThan(nodeById.exec1.x);
    expect(nodeById.smbFound.x).toBeLessThan(nodeById.exec2.x);
  });

  it('collapses the event lane of a tactic that has no event, tightening the bands', () => {
    // Three tactics, no event anywhere: nothing has to be routed between the bands, so none of them
    // may reserve an event card's worth of empty canvas.
    const { columns, nodeById } = buildLogicGraphLayout({
      actionMetas: {
        a1: action(),
        a2: action(),
        a3: action(),
      },
      eventMetas: {},
      outputProviders: {},
      tacticForStep: {
        a1: 'Discovery',
        a2: 'Lateral Movement',
        a3: 'Impact',
      },
      tacticOrder: {
        'Discovery': 1,
        'Lateral Movement': 2,
        'Impact': 3,
      },
    });
    expect(columns).toHaveLength(3);
    // The first band starts at the origin: no empty lane pushes it right.
    expect(columns[0].x).toBe(0);
    for (let i = 1; i < columns.length; i += 1) {
      const gap = columns[i].x - (columns[i - 1].x + columns[i - 1].width);
      expect(gap).toBeGreaterThan(0); // still non-overlapping
      expect(gap).toBeLessThan(nodeById.a1.width); // but too tight to hide an unused card slot
    }
  });

  it('keeps lane room only where an event actually sits', () => {
    // Only the second tactic is gated: its lane must be wide enough for the trigger card, while the
    // first tactic (no event) stays flush against it.
    const { columns, nodeById } = buildLogicGraphLayout({
      actionMetas: {
        a1: action(),
        a2: action(['gate']),
        a3: action(),
      },
      eventMetas: { gate: event() },
      outputProviders: {},
      tacticForStep: {
        a1: 'Discovery',
        a2: 'Lateral Movement',
        a3: 'Impact',
      },
      tacticOrder: {
        'Discovery': 1,
        'Lateral Movement': 2,
        'Impact': 3,
      },
    });
    const [discovery, lateral, impact] = columns;

    // Lane present between Discovery and Lateral Movement: the gap fits the trigger card, and the
    // trigger sits inside it (outside both bands).
    const gatedGap = lateral.x - (discovery.x + discovery.width);
    expect(gatedGap).toBeGreaterThanOrEqual(nodeById.gate.width);
    expect(nodeById.gate.x).toBeGreaterThanOrEqual(discovery.x + discovery.width);
    expect(nodeById.gate.x + nodeById.gate.width).toBeLessThanOrEqual(lateral.x);

    // No lane between Lateral Movement and Impact: that gap stays tight.
    const ungatedGap = impact.x - (lateral.x + lateral.width);
    expect(ungatedGap).toBeGreaterThan(0);
    expect(ungatedGap).toBeLessThan(gatedGap);
    expect(ungatedGap).toBeLessThan(nodeById.gate.width);
  });

  it('stacks a column\'s gated cards on top, ungated ones at the bottom', () => {
    // Only `gated` has a trigger to align with, so it goes first and the lane's card stays near the
    // top of the canvas instead of hanging under a stack of ungated cards.
    const { nodeById } = buildLogicGraphLayout({
      actionMetas: {
        ungated1: action(),
        gated: action(['gate']),
        ungated2: action(),
      },
      eventMetas: { gate: event() },
      outputProviders: {},
      tacticForStep: {
        ungated1: 'Discovery',
        gated: 'Discovery',
        ungated2: 'Discovery',
      },
      tacticOrder: { Discovery: 1 },
    });
    expect(nodeById.gated.x).toBe(nodeById.ungated1.x);
    expect(nodeById.gated.y).toBeLessThan(nodeById.ungated1.y);
    expect(nodeById.gated.y).toBeLessThan(nodeById.ungated2.y);
    // Which is the point: the trigger sits level with the topmost row, not far below it.
    expect(nodeById.gate.y + nodeById.gate.height / 2)
      .toBe(nodeById.gated.y + nodeById.gated.height / 2);
    expect(nodeById.gate.y).toBeLessThan(nodeById.ungated1.y);
  });

  it('orders the gated cards among themselves by dependency depth', () => {
    // deep is gated by `late`, a trigger fed by shallow's "port" output, so it comes after shallow.
    const { nodeById } = buildLogicGraphLayout({
      actionMetas: {
        deep: action(['late']),
        shallow: action(['first']),
      },
      eventMetas: {
        first: event(),
        late: eventOn('port'),
      },
      outputProviders: { port: [{ stepId: 'shallow' } as unknown as OutputProviderEntry] },
      tacticForStep: {
        deep: 'Discovery',
        shallow: 'Discovery',
      },
      tacticOrder: { Discovery: 1 },
    });
    expect(nodeById.deep.x).toBe(nodeById.shallow.x);
    expect(nodeById.shallow.y).toBeLessThan(nodeById.deep.y);
  });

  it('falls a tactic missing from the order map to the end (after the known ones)', () => {
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
    // Degenerate fallback bbox is still a real box.
    expect(empty.bbox.height).toBe(ACTION_NODE_HEIGHT);
  });
});
