import { describe, expect, it } from 'vitest';

import { ALL_EDGE_TYPES, ALL_NODE_TYPES, EDGE_TYPES_BY_NODE_TYPE } from '../../../../admin/components/graph/graph-model';

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
