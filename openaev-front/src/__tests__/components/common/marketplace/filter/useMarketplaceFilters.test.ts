import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import createFacetFilter from '../../../../../components/common/marketplace/filter/facetFilter';
import useMarketplaceFilters from '../../../../../components/common/marketplace/filter/useMarketplaceFilters';

interface Item {
  name: string;
  group?: string;
  color: string;
}

const APPLE: Item = {
  name: 'Apple',
  group: 'Fruit',
  color: 'red',
};
const CARROT: Item = {
  name: 'Carrot',
  group: 'Vegetable',
  color: 'orange',
};
const BANANA: Item = {
  name: 'Banana',
  group: 'Fruit',
  color: 'yellow',
};
const STONE: Item = {
  name: 'Stone',
  color: 'grey',
};
const ITEMS = [CARROT, STONE, BANANA, APPLE];

const facetFilter = createFacetFilter<Item, 'colors'>({ colors: item => [item.color] }, item => item.name);

const render = (groupKey?: (item: Item) => string | undefined) => renderHook(() => useMarketplaceFilters({
  items: ITEMS,
  facetFilter,
  sortValues: { name: (item: Item) => item.name },
  defaultSort: 'name',
  groupKey,
}));

describe('useMarketplaceFilters', () => {
  describe('sort', () => {
    it('given_theDefaultSort_should_showItemsByNameAscending', () => {
      // Act
      const { result } = render();

      // Assert
      expect(result.current.shown).toEqual([APPLE, BANANA, CARROT, STONE]);
      expect(result.current.toolbar.count).toBe(4);
    });

    it('given_aSortOnTheSameField_should_reverseIt', () => {
      // Arrange
      const { result } = render();

      // Act
      act(() => result.current.toolbar.sortHelpers.handleSort('name'));

      // Assert
      expect(result.current.toolbar.sortHelpers.getSortAsc()).toBe(false);
      expect(result.current.shown).toEqual([STONE, CARROT, BANANA, APPLE]);
    });

    it('given_aDirectedSort_should_applyTheDirection', () => {
      // Arrange
      const { result } = render();

      // Act
      act(() => result.current.toolbar.sortHelpers.handleDirectedSort('name', false));

      // Assert
      expect(result.current.toolbar.sortHelpers.getSortBy()).toBe('name');
      expect(result.current.shown).toEqual([STONE, CARROT, BANANA, APPLE]);
    });

    it('given_aGroupKey_should_groupFirstWithoutGroupLast', () => {
      // Act
      const { result } = render(item => item.group);

      // Assert
      expect(result.current.shown).toEqual([APPLE, BANANA, CARROT, STONE]);
    });
  });

  describe('filters', () => {
    it('given_aKeyword_should_showMatchingItemsAndCountThem', () => {
      // Arrange
      const { result } = render();

      // Act
      act(() => result.current.toolbar.onSearch('an'));

      // Assert
      expect(result.current.shown).toEqual([BANANA]);
      expect(result.current.toolbar.keyword).toBe('an');
      expect(result.current.filtered).toBe(true);
    });

    it('given_aFacetValueToggledTwice_should_selectThenUnselectIt', () => {
      // Arrange
      const { result } = render();

      // Act
      act(() => result.current.facetState.onToggle('colors', 'red'));
      const selected = result.current.shown;
      act(() => result.current.facetState.onToggle('colors', 'red'));

      // Assert
      expect(selected).toEqual([APPLE]);
      expect(result.current.shown).toHaveLength(4);
      expect(result.current.filtered).toBe(false);
    });

    it('given_aSelectedValue_should_keepTheCountsOfTheFacetAndListItsValues', () => {
      // Arrange
      const { result } = render();

      // Act
      act(() => result.current.facetState.onToggle('colors', 'red'));

      // Assert
      expect(result.current.facetState.counts('colors')).toEqual({
        red: 1,
        orange: 1,
        yellow: 1,
        grey: 1,
      });
      expect(result.current.facetState.values('colors')).toEqual(['grey', 'orange', 'red', 'yellow']);
    });

    it('given_resetFilters_should_clearTheKeywordAndValues', () => {
      // Arrange
      const { result } = render();
      act(() => {
        result.current.toolbar.onSearch('apple');
        result.current.facetState.onToggle('colors', 'red');
      });

      // Act
      act(() => result.current.resetFilters());

      // Assert
      expect(result.current.filtered).toBe(false);
      expect(result.current.facetState.filters).toEqual(facetFilter.noFilters);
    });

    it('given_aClearedSearch_should_storeAnEmptyKeyword', () => {
      // Arrange
      const { result } = render();
      act(() => result.current.toolbar.onSearch('apple'));

      // Act
      act(() => result.current.toolbar.onSearch(undefined));

      // Assert
      expect(result.current.toolbar.keyword).toBe('');
    });
  });
});
