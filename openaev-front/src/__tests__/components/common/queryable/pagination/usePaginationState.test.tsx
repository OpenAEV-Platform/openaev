import { act, renderHook } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import usePaginationState, { useLocalPaginationState } from '../../../../../components/common/queryable/pagination/usePaginationState';

// Controlled usage, as in useQueryableWithLocalStorage: the caller owns page and size.
const useControlledPaginationState = () => {
  const [pagination, setPagination] = useState({
    page: 0,
    size: 20,
  });
  return usePaginationState(pagination.page, pagination.size, (page, size) => setPagination({
    page,
    size,
  }));
};

/**
 * Lists patch their rows locally on single create/delete: incrementTotalElements
 * keeps the "1–N of X" counter in step with them without a refetch (#7769).
 */
describe('incrementTotalElements', () => {
  describe.each([
    ['usePaginationState', useControlledPaginationState],
    ['useLocalPaginationState', () => useLocalPaginationState(20)],
  ])('%s', (_name, hook) => {
    it('should add and remove rows from the total', () => {
      // Arrange
      const { result } = renderHook(hook);
      act(() => result.current.handleChangeTotalElements(5));

      // Act
      act(() => result.current.incrementTotalElements(1));
      act(() => result.current.incrementTotalElements(-1));
      act(() => result.current.incrementTotalElements(-1));

      // Assert
      expect(result.current.getTotalElements()).toBe(4);
    });

    it('should never go below zero', () => {
      // Arrange
      const { result } = renderHook(hook);

      // Act
      act(() => result.current.incrementTotalElements(-1));

      // Assert
      expect(result.current.getTotalElements()).toBe(0);
    });

    it('should step back to the new last page when its only row is removed', () => {
      // Arrange
      const { result } = renderHook(hook);
      act(() => result.current.handleChangeTotalElements(21));
      act(() => result.current.handleChangePage(1));

      // Act
      act(() => result.current.incrementTotalElements(-1));

      // Assert
      expect(result.current.getTotalElements()).toBe(20);
      expect(result.current.page).toBe(0);
    });

    it('should keep the page when it still holds rows', () => {
      // Arrange
      const { result } = renderHook(hook);
      act(() => result.current.handleChangeTotalElements(22));
      act(() => result.current.handleChangePage(1));

      // Act
      act(() => result.current.incrementTotalElements(-1));

      // Assert
      expect(result.current.getTotalElements()).toBe(21);
      expect(result.current.page).toBe(1);
    });
  });
});
