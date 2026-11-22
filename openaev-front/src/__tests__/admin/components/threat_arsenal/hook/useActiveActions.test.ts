import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { searchThreatArsenalActions } from '../../../../../actions/threat_arsenals/threatArsenal-actions';
import useActiveActions from '../../../../../admin/components/threat_arsenal/hook/useActiveActions';
import { type SearchPaginationInput, type ThreatArsenalAction } from '../../../../../utils/api-types';

const { filterHelpers, textSearchHelpers, queryable } = vi.hoisted(() => ({
  filterHelpers: { handleClearAllFilters: vi.fn() },
  textSearchHelpers: { handleTextSearch: vi.fn() },
  queryable: {
    searchPaginationInput: {
      page: 0,
      size: 20,
    } as SearchPaginationInput,
  },
}));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ searchThreatArsenalActions: vi.fn(() => Promise.resolve({ data: {} })) }));
vi.mock('../../../../../utils/bulkOperations', () => ({ useBulkOperationsFinishedCount: () => 0 }));
vi.mock('../../../../../admin/components/common/filters/MitreFilter', () => ({ MITRE_FILTER_KEY: 'injector_contract_attack_patterns' }));
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

    it('given_aNewVersionOfAnAction_should_replaceTheSavedOneInPlace', () => {
      // Arrange
      const { result } = renderActive();
      const newVersion = action('b-v2');

      // Act
      act(() => result.current.replaceAction(newVersion, 'b'));

      // Assert
      expect(result.current.threatArsenalActions).toEqual([A, newVersion, C]);
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
});
