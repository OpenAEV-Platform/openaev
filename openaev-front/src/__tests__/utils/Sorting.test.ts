import { describe, expect, it } from 'vitest';

import { ascendBy, descendBy } from '../../utils/Sorting';

interface Item {
  id: string;
  order?: number | null;
}

describe('Sorting utils', () => {
  describe('Function: ascendBy()', () => {
    it('given items in any order, should sort them by increasing value', () => {
      const items: Item[] = [{
        id: 'c',
        order: 3,
      }, {
        id: 'a',
        order: 1,
      }, {
        id: 'b',
        order: 2,
      }];

      expect(items.toSorted(ascendBy('order')).map(i => i.id)).toEqual(['a', 'b', 'c']);
    });

    it('given strings, should compare them code unit by code unit rather than by locale', () => {
      const items: Item[] = [{ id: 'b' }, { id: 'B' }, { id: 'a' }];

      expect(items.toSorted(ascendBy('id')).map(i => i.id)).toEqual(['B', 'a', 'b']);
    });

    it('given items with a missing value, should keep them in place', () => {
      const items: Item[] = [{ id: 'a' }, { id: 'b' }];

      expect(items.toSorted(ascendBy('order')).map(i => i.id)).toEqual(['a', 'b']);
    });

    it('given a list, should not mutate it', () => {
      const items: Item[] = [{
        id: 'b',
        order: 2,
      }, {
        id: 'a',
        order: 1,
      }];

      items.toSorted(ascendBy('order'));

      expect(items.map(i => i.id)).toEqual(['b', 'a']);
    });

    it('given equal values, should keep the original order', () => {
      const items: Item[] = [{
        id: 'x',
        order: 1,
      }, {
        id: 'y',
        order: 1,
      }, {
        id: 'z',
        order: 1,
      }];

      expect(items.toSorted(ascendBy('order')).map(i => i.id)).toEqual(['x', 'y', 'z']);
    });
  });

  describe('Function: descendBy()', () => {
    it('given items in any order, should sort them by decreasing value', () => {
      const items: Item[] = [{
        id: 'a',
        order: 1,
      }, {
        id: 'c',
        order: 3,
      }, {
        id: 'b',
        order: 2,
      }];

      expect(items.toSorted(descendBy('order')).map(i => i.id)).toEqual(['c', 'b', 'a']);
    });

    it('given equal values, should keep the original order', () => {
      const items: Item[] = [{
        id: 'x',
        order: 1,
      }, {
        id: 'y',
        order: 1,
      }];

      expect(items.toSorted(descendBy('order')).map(i => i.id)).toEqual(['x', 'y']);
    });
  });
});
