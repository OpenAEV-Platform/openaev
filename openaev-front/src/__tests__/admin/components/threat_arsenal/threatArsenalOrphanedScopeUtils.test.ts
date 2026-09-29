import { beforeEach, describe, expect, it, vi } from 'vitest';

import { hasOrphanedScopeFilters, toggleOrphanedScope } from '../../../../admin/components/threat_arsenal/threatArsenalOrphanedScopeUtils';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { type Filter, type FilterGroup } from '../../../../utils/api-types';

// -- TEST DATA --

const emptyFilter = (key: string): Filter => ({
  id: `${key}-id`,
  key,
  operator: 'empty',
  values: [],
  mode: 'and',
});

const ORPHANED_FILTERS = [emptyFilter('action_injectors'), emptyFilter('action_payload_status')];

const buildFilterGroup = (filters: Filter[]): FilterGroup => ({
  mode: 'and',
  filters,
});

const buildFilterHelpers = (): FilterHelpers => ({
  handleSwitchMode: vi.fn(),
  handleSwitchLocalMode: vi.fn(),
  handleAddFilterWithEmptyValue: vi.fn(),
  handleAddSingleValueFilter: vi.fn(),
  handleAddMultipleValueFilter: vi.fn(),
  handleChangeOperatorFilters: vi.fn(),
  handleClearAllFilters: vi.fn(),
  handleRemoveFilterByKey: vi.fn(),
  handleRemoveFilterById: vi.fn(),
  handleUpdateFilterById: vi.fn(),
  handleAddFilter: vi.fn(),
  handleSwitchLocalModeById: vi.fn(),
  handleChangeOperatorById: vi.fn(),
  handleUpdateValuesById: vi.fn(),
});

describe('threatArsenalOrphanedScopeUtils', () => {
  describe('hasOrphanedScopeFilters', () => {
    it('given_bothEmptyFilters_should_returnTrue', () => {
      // Arrange
      const filterGroup = buildFilterGroup(ORPHANED_FILTERS);

      // Act
      const result = hasOrphanedScopeFilters(filterGroup);

      // Assert
      expect(result).toBe(true);
    });

    it('given_noFilterGroup_should_returnFalse', () => {
      // Arrange & Act
      const result = hasOrphanedScopeFilters(undefined);

      // Assert
      expect(result).toBe(false);
    });

    describe.each([
      ['onlyActionInjectorsEmpty', [emptyFilter('action_injectors')]],
      ['onlyActionPayloadStatusEmpty', [emptyFilter('action_payload_status')]],
      ['actionInjectorsWithNonEmptyOperator', [
        {
          ...emptyFilter('action_injectors'),
          operator: 'eq',
          values: ['injector-id'],
        } as Filter,
        emptyFilter('action_payload_status'),
      ]],
    ])('given_%s', (_, filters) => {
      it('should_returnFalse', () => {
        // Arrange
        const filterGroup = buildFilterGroup(filters);

        // Act
        const result = hasOrphanedScopeFilters(filterGroup);

        // Assert
        expect(result).toBe(false);
      });
    });
  });

  describe('toggleOrphanedScope', () => {
    let filterHelpers: FilterHelpers;
    const handleToggleSelectAll = vi.fn();
    const handleClearSelectedElements = vi.fn();

    beforeEach(() => {
      filterHelpers = buildFilterHelpers();
      handleToggleSelectAll.mockReset();
      handleClearSelectedElements.mockReset();
    });

    it('given_inactiveScope_should_addEmptyFiltersAndSelectAll', () => {
      // Arrange & Act
      toggleOrphanedScope({
        isActive: false,
        filterHelpers,
        selectAll: false,
        handleToggleSelectAll,
        handleClearSelectedElements,
      });

      // Assert
      expect(filterHelpers.handleRemoveFilterByKey).toHaveBeenCalledWith('action_injectors');
      expect(filterHelpers.handleRemoveFilterByKey).toHaveBeenCalledWith('action_payload_status');
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledTimes(2);
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
        key: 'action_injectors',
        operator: 'empty',
      }));
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
        key: 'action_payload_status',
        operator: 'empty',
      }));
      expect(handleToggleSelectAll).toHaveBeenCalledTimes(1);
      expect(handleClearSelectedElements).not.toHaveBeenCalled();
    });

    it('given_inactiveScopeAndSelectAllAlreadyOn_should_notToggleSelectAllOff', () => {
      // Arrange & Act
      toggleOrphanedScope({
        isActive: false,
        filterHelpers,
        selectAll: true,
        handleToggleSelectAll,
        handleClearSelectedElements,
      });

      // Assert
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledTimes(2);
      expect(handleToggleSelectAll).not.toHaveBeenCalled();
    });

    it('given_activeScope_should_removeFiltersAndClearSelection', () => {
      // Arrange & Act
      toggleOrphanedScope({
        isActive: true,
        filterHelpers,
        selectAll: true,
        handleToggleSelectAll,
        handleClearSelectedElements,
      });

      // Assert
      expect(filterHelpers.handleRemoveFilterByKey).toHaveBeenCalledWith('action_injectors');
      expect(filterHelpers.handleRemoveFilterByKey).toHaveBeenCalledWith('action_payload_status');
      expect(filterHelpers.handleAddFilterWithEmptyValue).not.toHaveBeenCalled();
      expect(handleToggleSelectAll).not.toHaveBeenCalled();
      expect(handleClearSelectedElements).toHaveBeenCalledTimes(1);
    });
  });
});
