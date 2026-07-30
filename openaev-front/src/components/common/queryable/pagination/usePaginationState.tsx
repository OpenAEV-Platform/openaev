import { useCallback, useEffect, useRef, useState } from 'react';

import { type Pagination } from '../../../../utils/api-types';
import { type PaginationHelpers } from './PaginationHelpers';

export const ROWS_PER_PAGE_OPTIONS = [20, 50, 100];
// Keep the historical platform-wide default (the smallest option): heavy lists
// are sized for it, so any change must be a deliberate, screen-by-screen call.
export const DEFAULT_ROWS_PER_PAGE = ROWS_PER_PAGE_OPTIONS[0];

/**
 * Controlled pagination helpers: the single source of truth for page/size is
 * the caller's SearchPaginationInput. Earlier versions duplicated page/size in
 * internal state, which could desync from the input (text search, URI restore
 * and localStorage writes update the input directly). Once desynced, a
 * handleChangePage call whose value matched the stale internal state hit
 * React's setState bail-out and never propagated, leaving lists stuck on an
 * empty out-of-range page until a rows-per-page change forced an update.
 */
const usePaginationState = (
  page: number,
  size: number,
  onChange?: (page: number, size: number) => void,
): PaginationHelpers => {
  const [totalElements, setTotalElements] = useState(0);

  // Refs keep the handler identities stable while always acting on the
  // latest onChange and size from the current render.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const sizeRef = useRef(size);
  sizeRef.current = size;

  return {
    handleChangePage: useCallback((newPage: number) => onChangeRef.current?.(newPage, sizeRef.current), []),
    handleChangeRowsPerPage: useCallback((rowsPerPage: number) => onChangeRef.current?.(0, rowsPerPage), []),
    handleChangePagination: useCallback((pagination: Pagination) => onChangeRef.current?.(pagination.page, pagination.size), []),
    handleChangeTotalElements: useCallback((value: number) => setTotalElements(value), []),
    getTotalElements: () => totalElements,
    page,
    elementsPerPage: size,
  };
};

/**
 * Standalone (uncontrolled) pagination state for components living outside the
 * queryable system (e.g. dashboard list widgets). Only the page size is
 * persisted: restoring a page index points past the end of the list whenever
 * the dataset shrinks, showing a stuck empty page.
 */
export const useLocalPaginationState = (
  initSize?: number,
  persistKey?: string,
): PaginationHelpers => {
  const getInitialSize = () => {
    if (persistKey) {
      const saved = localStorage.getItem(persistKey);
      if (saved) {
        try {
          const { size: savedSize } = JSON.parse(saved);
          if (typeof savedSize === 'number') {
            return savedSize;
          }
        } catch {
          // Corrupted entry: fall back to the default size.
        }
      }
    }
    return initSize ?? DEFAULT_ROWS_PER_PAGE;
  };
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(getInitialSize);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    if (persistKey) {
      localStorage.setItem(persistKey, JSON.stringify({ size }));
    }
  }, [size, persistKey]);

  return {
    handleChangePage: useCallback((newPage: number) => setPage(newPage), []),
    handleChangeRowsPerPage: useCallback((rowsPerPage: number) => {
      setSize(rowsPerPage);
      setPage(0);
    }, []),
    handleChangePagination: useCallback(({ page: newPage, size: newSize }: Pagination) => {
      setPage(newPage);
      setSize(newSize);
    }, []),
    handleChangeTotalElements: useCallback((value: number) => setTotalElements(value), []),
    getTotalElements: () => totalElements,
    page,
    elementsPerPage: size,
  };
};

export default usePaginationState;
