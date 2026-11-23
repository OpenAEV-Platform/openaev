import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { bulkDeleteThreatArsenalActions } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import useActiveSelection from '../../../../../admin/components/threat_arsenal/hook/useActiveSelection';
import { type FilterHelpers } from '../../../../../components/common/queryable/filter/FilterHelpers';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../../utils/api-types';

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ bulkDeleteThreatArsenalActions: vi.fn(() => Promise.resolve()) }));
vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, string>) => (values
      ? key.replace('{count}', values.count)
      : key),
  }),
}));

const action = (id: string): ThreatArsenalAction => ({ injector_contract_id: id } as ThreatArsenalAction);
const A = action('a');
const B = action('b');
const C = action('c');
const SEARCH: SearchPaginationInput = {
  page: 0,
  size: 20,
};

const filterHelpers = {
  handleRemoveFilterByKey: vi.fn(),
  handleAddFilterWithEmptyValue: vi.fn(),
} as unknown as FilterHelpers;

const renderSelection = () => renderHook(() => useActiveSelection([A, B, C], 3, SEARCH, filterHelpers));

describe('useActiveSelection', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('selection', () => {
    it('given_nothingSelected_should_offerToSelectAll', () => {
      // Act
      const { result } = renderSelection();

      // Assert
      expect(result.current.label).toBe('Select all');
      expect(result.current.checked).toBe(false);
    });

    it('given_oneActionSelected_should_countItAndBePartial', () => {
      // Arrange
      const { result } = renderSelection();

      // Act
      act(() => result.current.onToggleEntity(A));

      // Assert
      expect(result.current.label).toBe('1 action selected');
      expect(result.current.checked).toBe('indeterminate');
      expect(result.current.isOnlyOne).toBe(true);
      expect(result.current.isChecked(A)).toBe(true);
      expect(result.current.isChecked(B)).toBe(false);
    });

    it('given_selectAllWithOneLeftOut_should_countTheOthers', () => {
      // Arrange
      const { result } = renderSelection();

      // Act
      act(() => result.current.handleToggleSelectAll());
      act(() => result.current.onToggleEntity(B));

      // Assert
      expect(result.current.label).toBe('2 actions selected');
      expect(result.current.checked).toBe('indeterminate');
      expect(result.current.isOnlyOne).toBe(false);
      expect(result.current.isChecked(A)).toBe(true);
      expect(result.current.isChecked(B)).toBe(false);
    });
  });

  describe('bulk delete', () => {
    it('given_pickedActions_should_deleteThemAndClearTheSelection', () => {
      // Arrange
      const { result } = renderSelection();
      act(() => result.current.onToggleEntity(A));

      // Act
      act(() => result.current.bulkDelete());

      // Assert
      expect(bulkDeleteThreatArsenalActions).toHaveBeenCalledWith({
        ...SEARCH,
        injector_contract_ids_to_process: ['a'],
        injector_contract_ids_to_ignore: [],
      });
      expect(result.current.count).toBe(0);
    });

    it('given_selectAllWithOneLeftOut_should_deleteAllButIt', () => {
      // Arrange
      const { result } = renderSelection();
      act(() => result.current.handleToggleSelectAll());
      act(() => result.current.onToggleEntity(C));

      // Act
      act(() => result.current.bulkDelete());

      // Assert
      expect(bulkDeleteThreatArsenalActions).toHaveBeenCalledWith(expect.objectContaining({
        injector_contract_ids_to_process: [],
        injector_contract_ids_to_ignore: ['c'],
      }));
    });
  });

  describe('orphaned actions', () => {
    it('given_theScopeIsOff_should_filterOrphansAndSelectThemAll', () => {
      // Arrange
      const { result } = renderSelection();

      // Act
      act(() => result.current.toggleOrphaned());

      // Assert
      expect(result.current.isOrphanedScopeActive).toBe(false);
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledTimes(2);
      expect(result.current.selectAll).toBe(true);
    });
  });
});
