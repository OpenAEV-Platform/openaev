import { useCallback, useEffect, useRef, useState } from 'react';

import { type Pagination } from '../../../../utils/api-types';
import { type PaginationHelpers } from './PaginationHelpers';

export const ROWS_PER_PAGE_OPTIONS = [20, 50, 100];

const usePaginationState = (
  initSize?: number,
  onChange?: (page: number, size: number) => void,
  persistKey?: string): PaginationHelpers => {
  // Load from localStorage if persistKey is provided
  const getInitialState = () => {
    if (persistKey) {
      const saved = localStorage.getItem(persistKey);
      if (saved) {
        const { page: savedPage, size: savedSize } = JSON.parse(saved);
        return {
          page: savedPage,
          size: savedSize,
        };
      }
    }
    return {
      page: 0,
      size: initSize ?? ROWS_PER_PAGE_OPTIONS[0],
    };
  };
  const initialState = getInitialState();
  const [page, setPage] = useState(initialState.page);
  const [size, setSize] = useState(initialState.size);
  const [totalElements, setTotalElements] = useState(0);

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // Persist to localStorage if persistKey is provided
  useEffect(() => {
    if (persistKey) {
      localStorage.setItem(persistKey, JSON.stringify({
        page,
        size,
      }));
    }
  }, [page, size, persistKey]);

  useEffect(() => {
    onChangeRef.current?.(page, size);
  }, [page, size]);

  return {
    handleChangePage: useCallback((newPage: number) => setPage(newPage), []),
    handleChangeRowsPerPage: useCallback((rowsPerPage: number) => {
      setSize(rowsPerPage);
      setPage(0);
    }, []),
    handleChangePagination: useCallback(({ page, size }: Pagination) => {
      setPage(page);
      setSize(size);
    }, []),
    handleChangeTotalElements: useCallback((value: number) => setTotalElements(value), []),
    getTotalElements: () => totalElements,
    page,
    elementsPerPage: size,
  };
};

export default usePaginationState;
