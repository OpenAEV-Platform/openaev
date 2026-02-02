import { useCallback, useEffect, useRef, useState } from 'react';

import { type PaginationHelpers } from './PaginationHelpers';

export const ROWS_PER_PAGE_OPTIONS = [20, 50, 100];

const usePaginationState = (initSize?: number, onChange?: (page: number, size: number) => void): PaginationHelpers => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(initSize ?? ROWS_PER_PAGE_OPTIONS[0]);
  const [totalElements, setTotalElements] = useState(0);

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const helpers: PaginationHelpers = {
    handleChangePage: useCallback((newPage: number) => setPage(newPage), []),
    handleChangeRowsPerPage: useCallback((rowsPerPage: number) => {
      setSize(rowsPerPage);
      setPage(0);
    }, []),
    handleChangeTotalElements: useCallback((value: number) => setTotalElements(value), []),
    getTotalElements: () => totalElements,
  };

  useEffect(() => {
    onChangeRef.current?.(page, size);
  }, [page, size]);

  return helpers;
};

export default usePaginationState;
