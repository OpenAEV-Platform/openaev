import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import useFacetFilterToggle from '../../../../components/common/facets/useFacetFilterToggle';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { type Filter, type SearchPaginationInput } from '../../../../utils/api-types';

// -- HELPERS --

const KEY = 'finding_type';

const aFilter = (values: string[]): Filter => ({
  id: 'filter-1',
  key: KEY,
  operator: 'eq',
  mode: 'and',
  values,
});

const setup = (filters: Filter[]) => {
  const helpers = {
    handleAddFilterWithEmptyValue: vi.fn(),
    handleRemoveFilterById: vi.fn(),
    handleRemoveFilterByKey: vi.fn(),
    handleUpdateValuesById: vi.fn(),
  };
  const searchPaginationInput = {
    filterGroup: {
      mode: 'and',
      filters,
    },
  } as SearchPaginationInput;
  const { result } = renderHook(() => useFacetFilterToggle(searchPaginationInput, helpers as unknown as FilterHelpers));
  return {
    helpers,
    hook: result.current,
  };
};

// -- TESTS --

describe('useFacetFilterToggle', () => {
  describe('setFilterValues', () => {
    it('should update the values of the existing filter', () => {
      // Arrange
      const { helpers, hook } = setup([aFilter(['ipv4'])]);

      // Act
      hook.setFilterValues(KEY, ['ipv4', 'cve']);

      // Assert
      expect(helpers.handleUpdateValuesById).toHaveBeenCalledWith('filter-1', ['ipv4', 'cve']);
    });

    it('should add an eq filter when none exists for the key', () => {
      // Arrange
      const { helpers, hook } = setup([]);

      // Act
      hook.setFilterValues(KEY, ['ipv4']);

      // Assert
      expect(helpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
        key: KEY,
        operator: 'eq',
        mode: 'and',
        values: ['ipv4'],
      }));
    });

    it('should remove the filter by id when no value is left', () => {
      // Arrange
      const { helpers, hook } = setup([aFilter(['ipv4'])]);

      // Act
      hook.setFilterValues(KEY, []);

      // Assert
      expect(helpers.handleRemoveFilterById).toHaveBeenCalledWith('filter-1');
      expect(helpers.handleRemoveFilterByKey).not.toHaveBeenCalled();
    });

    it('should remove the filter by key when it has no id', () => {
      // Arrange
      const { helpers, hook } = setup([{
        ...aFilter(['ipv4']),
        id: '',
      }]);

      // Act
      hook.setFilterValues(KEY, []);

      // Assert
      expect(helpers.handleRemoveFilterByKey).toHaveBeenCalledWith(KEY);
      expect(helpers.handleRemoveFilterById).not.toHaveBeenCalled();
    });
  });

  describe('toggleValueWithCurrent', () => {
    it.each([
      ['adds a value that is not selected', ['ipv4'], 'cve', ['ipv4', 'cve']],
      ['removes a value that is selected', ['ipv4', 'cve'], 'ipv4', ['cve']],
    ])('should %s', (_name, current, value, expected) => {
      // Arrange
      const { helpers, hook } = setup([aFilter(current)]);

      // Act
      hook.toggleValueWithCurrent(KEY, current, value);

      // Assert
      expect(helpers.handleUpdateValuesById).toHaveBeenCalledWith('filter-1', expected);
    });

    it('should drop the filter when the last value is unselected', () => {
      // Arrange
      const { helpers, hook } = setup([aFilter(['ipv4'])]);

      // Act
      hook.toggleValueWithCurrent(KEY, ['ipv4'], 'ipv4');

      // Assert
      expect(helpers.handleRemoveFilterById).toHaveBeenCalledWith('filter-1');
    });
  });
});
