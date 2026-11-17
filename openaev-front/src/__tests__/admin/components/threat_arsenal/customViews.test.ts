import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { sameViewFilters, toViewFilters, useCustomViews } from '../../../../admin/components/threat_arsenal/customViews';

const approvedOnWindows = {
  filterGroup: {
    mode: 'and' as const,
    filters: [
      {
        id: 'a1',
        key: 'action_payload_approval_status',
        mode: 'or' as const,
        operator: 'eq' as const,
        values: ['APPROVED'],
      },
      {
        id: 'p1',
        key: 'action_platforms',
        mode: 'or' as const,
        operator: 'contains' as const,
        values: ['Windows', 'Linux'],
      },
    ],
  },
  textSearch: 'mimikatz',
};

describe('customViews', () => {
  afterEach(() => window.localStorage.clear());

  describe('sameViewFilters', () => {
    it('given the same filters with other ids and orders should be the same', () => {
      // Arrange
      const reordered = {
        filterGroup: {
          mode: 'and' as const,
          filters: [
            {
              ...approvedOnWindows.filterGroup.filters[1],
              id: 'other',
              values: ['Linux', 'Windows'],
            },
            {
              ...approvedOnWindows.filterGroup.filters[0],
              id: 'again',
            },
          ],
        },
        textSearch: 'mimikatz',
      };

      // Act / Assert
      expect(sameViewFilters(approvedOnWindows, reordered)).toBe(true);
    });

    it('given another value or search should differ', () => {
      // Arrange / Act / Assert
      expect(sameViewFilters(approvedOnWindows, {
        ...approvedOnWindows,
        textSearch: '',
      })).toBe(false);
      expect(sameViewFilters({}, { textSearch: '' })).toBe(true);
    });
  });

  it('given filters to keep should drop their generated ids', () => {
    // Arrange / Act
    const kept = toViewFilters(approvedOnWindows);

    // Assert
    expect(kept.filterGroup?.filters?.map(filter => filter.id)).toEqual([undefined, undefined]);
    expect(kept.textSearch).toBe('mimikatz');
  });

  it('given a person creating, changing and deleting a view should keep it per person', () => {
    // Arrange
    const { result } = renderHook(() => useCustomViews('me'));

    // Act
    let id = '';
    act(() => {
      id = result.current.add('Approved on Windows', approvedOnWindows).id;
    });
    act(() => result.current.saveFilters(id, { textSearch: 'powershell' }));
    act(() => result.current.rename(id, 'PowerShell'));

    // Assert
    expect(result.current.byId(id)?.label).toBe('PowerShell');
    expect(result.current.byId(id)?.filters.textSearch).toBe('powershell');
    expect(result.current.tabs.map(view => view.id)).toEqual([id]);
    expect(renderHook(() => useCustomViews('someone else')).result.current.views).toEqual([]);

    // Act
    act(() => result.current.remove(id));

    // Assert
    expect(result.current.views).toEqual([]);
  });
});
