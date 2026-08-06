import { describe, expect, it } from 'vitest';

import {
  ACTION_NODE_HEIGHT,
  buildLogicGraphLayout,
} from '../../../../../../admin/components/chaining/logic/logic-graph/layout';
import type { ActionMeta, EventMeta } from '../../../../../../admin/components/chaining/logic/types';

// Minimal metas: the layout only reads step_condition_ids on actions and formData.conditionGroups
// on events (for the inferred edges), plus the tactic inputs. Cast the rest away.
const action = (stepConditionIds: string[] = []): ActionMeta =>
  ({ step_condition_ids: stepConditionIds } as unknown as ActionMeta);
const event = (): EventMeta =>
  ({ formData: { conditionGroups: [] } } as unknown as EventMeta);

// A three-step chain: e1 gates a1, a1 -> e2 (inferred, elided here) ... a2 gated by e2, a3 gated by
// e3. Discovery = a1 + a2, Lateral Movement = a3.
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

describe('buildLogicGraphLayout dependency-depth layout', () => {
  it('lays nodes out left-to-right by dependency depth, NOT by tactic', () => {
    const { nodeById } = build();

    // A gating trigger sits strictly left of the action it gates (the causal wave reads L-to-R).
    expect(nodeById.e1.x).toBeLessThan(nodeById.a1.x);
    expect(nodeById.e2.x).toBeLessThan(nodeById.a2.x);
    expect(nodeById.e3.x).toBeLessThan(nodeById.a3.x);

    // a1 and a2 share a tactic but are on the SAME dependency layer (both gated by a first-wave
    // trigger), so they stack in the same column instead of being forced apart by tactic.
    expect(nodeById.a1.x).toBe(nodeById.a2.x);
    expect(nodeById.a1.y).not.toBe(nodeById.a2.y);
  });

  it('normalizes the layout to the origin (no negative coordinates)', () => {
    const { nodes, groups, bbox } = build();
    for (const node of nodes) {
      expect(node.x).toBeGreaterThanOrEqual(0);
      expect(node.y).toBeGreaterThanOrEqual(0);
    }
    for (const group of groups) {
      expect(group.x).toBeGreaterThanOrEqual(0);
      expect(group.y).toBeGreaterThanOrEqual(0);
    }
    expect(bbox.minX).toBe(0);
    expect(bbox.minY).toBe(0);
  });
});

describe('buildLogicGraphLayout tactic groups', () => {
  it('emits one padded group hull per tactic, ordered by phase for stable colours', () => {
    const { groups } = build();
    expect(groups.map(g => g.tactic)).toEqual(['Discovery', 'Lateral Movement']);
    expect(groups.map(g => g.order)).toEqual([1, 2]);
    for (const group of groups) {
      expect(group.width).toBeGreaterThan(0);
      expect(group.height).toBeGreaterThan(0);
      expect(group.headerHeight).toBeGreaterThan(0);
    }
  });

  it('pads each hull around its action cards (cards never sit flush on the border)', () => {
    const { nodeById, groups } = build();
    const discovery = groups.find(g => g.tactic === 'Discovery')!;
    for (const id of ['a1', 'a2']) {
      const node = nodeById[id];
      // Horizontal + bottom padding: the card is strictly inside the hull on every padded side.
      expect(node.x).toBeGreaterThan(discovery.x);
      expect(node.x + node.width).toBeLessThan(discovery.x + discovery.width);
      expect(node.y + node.height).toBeLessThan(discovery.y + discovery.height);
      // Header room on top: the card starts below the reserved header band.
      expect(node.y).toBeGreaterThanOrEqual(discovery.y + discovery.headerHeight);
    }
  });

  it('bounds every action of a tactic even when they span several dependency layers', () => {
    // a1 (layer 0) and a2 (gated by e2, a later layer) are both Discovery: the hull must span both.
    const { nodeById, groups } = buildLogicGraphLayout({
      actionMetas: {
        a1: action(),
        e2gate: action(['e2']),
      },
      eventMetas: { e2: event() },
      outputProviders: {},
      tacticForStep: {
        a1: 'Discovery',
        e2gate: 'Discovery',
      },
      tacticOrder: { Discovery: 1 },
    });
    const discovery = groups.find(g => g.tactic === 'Discovery')!;
    for (const id of ['a1', 'e2gate']) {
      const node = nodeById[id];
      expect(node.x).toBeGreaterThanOrEqual(discovery.x);
      expect(node.x + node.width).toBeLessThanOrEqual(discovery.x + discovery.width);
    }
  });

  it('falls a tactic missing from the order map to the end (after the known ones)', () => {
    const { groups } = buildLogicGraphLayout({
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
    expect(groups.map(g => g.tactic)).toEqual(['Lateral Movement', 'Alpha Unknown']);
  });

  it('returns an empty layout (no groups) for an empty graph', () => {
    const empty = buildLogicGraphLayout({
      actionMetas: {},
      eventMetas: {},
      outputProviders: {},
      tacticForStep: {},
      tacticOrder: {},
    });
    expect(empty.nodes).toHaveLength(0);
    expect(empty.groups).toHaveLength(0);
    // Degenerate fallback bbox is still a real box.
    expect(empty.bbox.height).toBe(ACTION_NODE_HEIGHT);
  });
});
