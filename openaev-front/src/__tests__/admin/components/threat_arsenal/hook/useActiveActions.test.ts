import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { bulkDeleteThreatArsenalActions, searchThreatArsenalActions } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import useActiveActions from '../../../../../admin/components/threat_arsenal/hook/useActiveActions';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../../utils/api-types';

const { filterHelpers, textSearchHelpers, queryable } = vi.hoisted(() => ({
  filterHelpers: {
    handleClearAllFilters: vi.fn(),
    handleRemoveFilterByKey: vi.fn(),
    handleAddFilterWithEmptyValue: vi.fn(),
  },
  textSearchHelpers: { handleTextSearch: vi.fn() },
  queryable: {
    searchPaginationInput: {
      page: 0,
      size: 20,
    } as SearchPaginationInput,
  },
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({
  bulkDeleteThreatArsenalActions: vi.fn(() => Promise.resolve()),
  fetchThreatArsenalAuthorCounts: vi.fn(() => Promise.resolve({ data: [] })),
  searchThreatArsenalActions: vi.fn(() => Promise.resolve({ data: {} })),
}));
vi.mock('../../../../../actions/AttackPattern', () => ({ fetchAttackPatterns: vi.fn() }));
vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, string>) => (values
      ? key.replace('{count}', values.count)
      : key),
  }),
}));
vi.mock('../../../../../store', () => ({ useHelper: () => [] }));
vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../utils/hooks/useDataLoader', () => ({ default: vi.fn() }));
vi.mock('../../../../../utils/bulkOperations', () => ({ useBulkOperationsFinishedCount: () => 0 }));
vi.mock('../../../../../admin/components/common/filters/MitreFilter', () => ({ MITRE_FILTER_KEY: 'injector_contract_attack_patterns' }));
vi.mock('../../../../../admin/components/common/domains/useDomainIconFilter', () => ({ default: () => ({ iconBarOrderedDomains: [] }) }));
vi.mock('../../../../../admin/components/threat_arsenal/useThreatArsenalFacetCounts', () => ({ default: () => null }));
vi.mock('../../../../../components/common/facets/ContractFacets', () => ({ useAuthorOptions: () => [] }));
vi.mock('../../../../../components/common/queryable/useQueryableWithLocalStorage', () => ({
  useQueryableWithLocalStorage: () => ({
    searchPaginationInput: queryable.searchPaginationInput,
    queryableHelpers: {
      filterHelpers,
      textSearchHelpers,
      paginationHelpers: { getTotalElements: () => 3 },
    },
  }),
}));

const action = (id: string): ThreatArsenalAction => ({ injector_contract_id: id } as ThreatArsenalAction);
const A = action('a');
const B = action('b');
const C = action('c');

const renderActive = () => {
  const hook = renderHook(() => useActiveActions());
  act(() => hook.result.current.setThreatArsenalActions([A, B, C]));
  return hook;
};

describe('useActiveActions', () => {
  beforeEach(() => {
    queryable.searchPaginationInput = {
      page: 0,
      size: 20,
    };
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  describe('search', () => {
    it('given_aSearch_should_askForDomainSections', async () => {
      // Arrange
      const { result } = renderActive();

      // Act
      await act(() => result.current.fetchActions({
        page: 1,
        size: 20,
      }));

      // Assert
      expect(searchThreatArsenalActions).toHaveBeenCalledWith({
        page: 1,
        size: 20,
        group_by_primary_domain: true,
      });
      expect(result.current.loading).toBe(false);
    });

    it.each([
      ['no filter', {}, false],
      ['a text search', { textSearch: 'mimikatz' }, true],
      ['a filter', {
        filterGroup: {
          mode: 'and' as const,
          filters: [{
            id: 'f',
            key: 'action_tags',
            mode: 'or' as const,
            operator: 'eq' as const,
            values: ['t'],
          }],
        },
      }, true],
    ])('given_%s_should_tellWhetherFiltersAreOn', (_, input, expected) => {
      // Arrange
      queryable.searchPaginationInput = {
        page: 0,
        size: 20,
        ...input,
      };

      // Act
      const { result } = renderActive();

      // Assert
      expect(result.current.hasActiveFilters).toBe(expected);
    });

    it('given_reset_should_clearFiltersAndText', () => {
      // Arrange
      const { result } = renderActive();

      // Act
      act(() => result.current.resetFilters());

      // Assert
      expect(filterHelpers.handleClearAllFilters).toHaveBeenCalledOnce();
      expect(textSearchHelpers.handleTextSearch).toHaveBeenCalledWith('');
    });
  });

  describe('changes made on the page', () => {
    it('given_aCreatedAction_should_putItFirst', () => {
      // Arrange
      const { result } = renderActive();
      const created = action('new');

      // Act
      act(() => result.current.addAction(created));

      // Assert
      expect(result.current.threatArsenalActions).toEqual([created, A, B, C]);
    });

    it('given_anUpdatedAction_should_replaceItInPlace', () => {
      // Arrange
      const { result } = renderActive();
      const updated = {
        ...B,
        action_labels: { en: 'renamed' },
      };

      // Act
      act(() => result.current.replaceAction(updated));

      // Assert
      expect(result.current.threatArsenalActions).toEqual([A, updated, C]);
    });

    it('given_aDeletedAction_should_removeIt', () => {
      // Arrange
      const { result } = renderActive();

      // Act
      act(() => result.current.removeAction('a'));

      // Assert
      expect(result.current.threatArsenalActions).toEqual([B, C]);
    });
  });

  describe('selection', () => {
    it('given_nothingSelected_should_offerToSelectAll', () => {
      // Act
      const { result } = renderActive();

      // Assert
      expect(result.current.selection.label).toBe('Select all');
      expect(result.current.selection.checked).toBe(false);
    });

    it('given_oneActionSelected_should_countItAndBePartial', () => {
      // Arrange
      const { result } = renderActive();

      // Act
      act(() => result.current.selection.onToggleEntity(A));

      // Assert
      expect(result.current.selection.label).toBe('1 action selected');
      expect(result.current.selection.checked).toBe('indeterminate');
      expect(result.current.selection.isChecked(A)).toBe(true);
      expect(result.current.selection.isChecked(B)).toBe(false);
    });

    it('given_selectAllWithOneLeftOut_should_countTheOthers', () => {
      // Arrange
      const { result } = renderActive();

      // Act
      act(() => result.current.selection.handleToggleSelectAll());
      act(() => result.current.selection.onToggleEntity(B));

      // Assert
      expect(result.current.selection.label).toBe('2 actions selected');
      expect(result.current.selection.checked).toBe('indeterminate');
      expect(result.current.selection.isChecked(A)).toBe(true);
      expect(result.current.selection.isChecked(B)).toBe(false);
    });
  });

  describe('bulk delete', () => {
    it('given_pickedActions_should_deleteThemAndClearTheSelection', () => {
      // Arrange
      const { result } = renderActive();
      act(() => result.current.selection.onToggleEntity(A));

      // Act
      act(() => result.current.bulkDelete());

      // Assert
      expect(bulkDeleteThreatArsenalActions).toHaveBeenCalledWith(expect.objectContaining({
        injector_contract_ids_to_process: ['a'],
        injector_contract_ids_to_ignore: [],
      }));
      expect(result.current.selection.count).toBe(0);
    });

    it('given_selectAllWithOneLeftOut_should_deleteAllButIt', () => {
      // Arrange
      const { result } = renderActive();
      act(() => result.current.selection.handleToggleSelectAll());
      act(() => result.current.selection.onToggleEntity(C));

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
      const { result } = renderActive();

      // Act
      act(() => result.current.toggleOrphaned());

      // Assert
      expect(result.current.isOrphanedScopeActive).toBe(false);
      expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledTimes(2);
      expect(result.current.selection.selectAll).toBe(true);
    });
  });
});
