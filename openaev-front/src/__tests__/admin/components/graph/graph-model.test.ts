import { describe, expect, it } from 'vitest';

import { ALL_EDGE_TYPES, ALL_NODE_TYPES, EDGE_TYPES_BY_NODE_TYPE, nodeCounterpart } from '../../../../admin/components/graph/graph-model';

describe('EDGE_TYPES_BY_NODE_TYPE', () => {
  it('names the relations of every node type, from the known relation types only', () => {
    ALL_NODE_TYPES.forEach((type) => {
      const edges = EDGE_TYPES_BY_NODE_TYPE[type];
      expect(edges.length).toBeGreaterThan(0);
      edges.forEach(edge => expect(ALL_EDGE_TYPES).toContain(edge));
    });
  });

  it('offers a simulation only the relations it can hold', () => {
    expect(EDGE_TYPES_BY_NODE_TYPE.SIMULATION).toEqual(['PART_OF', 'RUN_OF', 'ATTACK_STEP']);
    expect(EDGE_TYPES_BY_NODE_TYPE.SIMULATION).not.toContain('WORKS_FOR');
  });

  it('covers every relation type with at least one node type', () => {
    const covered = new Set(Object.values(EDGE_TYPES_BY_NODE_TYPE).flat());
    ALL_EDGE_TYPES.forEach(edge => expect(covered.has(edge)).toBe(true));
  });
});

describe('nodeCounterpart', () => {
  it('matches a CVE finding on its CVE id, whatever the subtype case', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'CVE',
      node_label: 'CVE-2024-3400',
    }))
      .toEqual({
        kind: 'vulnerabilities',
        key: 'CVE-2024-3400',
      });
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'cve',
      node_label: 'CVE-2021-44228',
    }))
      .toEqual({
        kind: 'vulnerabilities',
        key: 'CVE-2021-44228',
      });
  });

  it('has no key for another finding type, an empty label or an asset (named, not keyed)', () => {
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'TEXT',
      node_label: 'open port',
    })).toBeNull();
    expect(nodeCounterpart({
      node_type: 'FINDING',
      node_subtype: 'CVE',
      node_label: '',
    })).toBeNull();
    expect(nodeCounterpart({
      node_type: 'ASSET',
      node_subtype: null,
      node_label: 'oaev-test-win-03',
    })).toBeNull();
    expect(nodeCounterpart({
      node_type: 'SIMULATION',
      node_subtype: null,
      node_label: 'Weekly run',
    })).toBeNull();
  });
});
