import { describe, expect, it } from 'vitest';

import createFacetFilter from '../../../../../components/common/marketplace/filter/facetFilter';

interface Item {
  name: string;
  colors: string[];
  size?: string;
}

const RED_SMALL: Item = {
  name: 'Red ball',
  colors: ['red'],
  size: 'small',
};
const BLUE_GREEN_LARGE: Item = {
  name: 'Blue and green box',
  colors: ['blue', 'green'],
  size: 'large',
};
const RED_NO_SIZE: Item = {
  name: 'Red pen',
  colors: ['red'],
};
const ITEMS = [RED_SMALL, BLUE_GREEN_LARGE, RED_NO_SIZE];

const filter = createFacetFilter<Item, 'colors' | 'sizes'>(
  {
    colors: item => item.colors,
    sizes: item => (item.size ? [item.size] : []),
  },
  item => item.name,
);

const filtersOf = (values: Partial<Record<'colors' | 'sizes', string[]>>, keyword = '') => ({
  keyword,
  values: {
    ...filter.noFilters.values,
    ...values,
  },
});

describe('createFacetFilter', () => {
  describe('noFilters and hasFilters', () => {
    it('given_noFilters_should_haveAnEmptyKeywordAndNoValuePerFacet', () => {
      // Act
      const { noFilters } = filter;

      // Assert
      expect(noFilters).toEqual({
        keyword: '',
        values: {
          colors: [],
          sizes: [],
        },
      });
      expect(filter.hasFilters(noFilters)).toBe(false);
    });

    it.each([
      ['a keyword', filtersOf({}, 'red')],
      ['a facet value', filtersOf({ colors: ['red'] })],
    ])('given_%s_should_haveFilters', (_name, filters) => {
      // Act
      const result = filter.hasFilters(filters);

      // Assert
      expect(result).toBe(true);
    });
  });

  describe('apply', () => {
    it('given_noFilters_should_keepEveryItem', () => {
      // Act
      const result = filter.apply(ITEMS, filter.noFilters);

      // Assert
      expect(result).toEqual(ITEMS);
    });

    it('given_aKeyword_should_matchTheTextIgnoringCase', () => {
      // Act
      const result = filter.apply(ITEMS, filtersOf({}, 'RED'));

      // Assert
      expect(result).toEqual([RED_SMALL, RED_NO_SIZE]);
    });

    it('given_twoValuesOfOneFacet_should_keepItemsWithEither', () => {
      // Act
      const result = filter.apply(ITEMS, filtersOf({ colors: ['blue', 'red'] }));

      // Assert
      expect(result).toEqual(ITEMS);
    });

    it('given_valuesOfTwoFacets_should_keepItemsMatchingBoth', () => {
      // Act
      const result = filter.apply(ITEMS, filtersOf({
        colors: ['red'],
        sizes: ['small'],
      }));

      // Assert
      expect(result).toEqual([RED_SMALL]);
    });
  });

  describe('counts', () => {
    it('given_aValueOfTheSameFacet_should_countAsIfItWereNotSelected', () => {
      // Act
      const result = filter.counts(ITEMS, filtersOf({ colors: ['blue'] }), 'colors');

      // Assert
      expect(result).toEqual({
        red: 2,
        blue: 1,
        green: 1,
      });
    });

    it('given_aValueOfAnotherFacet_should_countOnlyTheMatchingItems', () => {
      // Act
      const result = filter.counts(ITEMS, filtersOf({ sizes: ['large'] }), 'colors');

      // Assert
      expect(result).toEqual({
        blue: 1,
        green: 1,
      });
    });
  });

  describe('values', () => {
    it('given_items_should_listTheDistinctValuesSorted', () => {
      // Act
      const result = filter.values(ITEMS, 'colors');

      // Assert
      expect(result).toEqual(['blue', 'green', 'red']);
    });

    it('given_itemsWithoutAValue_should_skipThem', () => {
      // Act
      const result = filter.values(ITEMS, 'sizes');

      // Assert
      expect(result).toEqual(['large', 'small']);
    });
  });
});
