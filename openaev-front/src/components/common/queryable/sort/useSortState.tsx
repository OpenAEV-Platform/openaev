import { useCallback, useEffect, useRef, useState } from 'react';

import { type SortField } from '../../../../utils/api-types';
import { type SortHelpers } from './SortHelpers';

const computeDirection = (direction?: string) => {
  if (direction) {
    return direction === 'ASC';
  }
  return false;
};

const useSortState = (initSorts: SortField[] = [], onChange?: (sorts: SortField[]) => void) => {
  const [sortBy, setSortBy] = useState(initSorts?.[0]?.property ?? '');
  const [sortAsc, setSortAsc] = useState(computeDirection(initSorts?.[0]?.direction));

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const helpers: SortHelpers = {
    handleSort: useCallback((field: string) => {
      setSortBy(field);
      setSortAsc(prev => !prev);
    }, []),
    getSortBy: useCallback(() => sortBy, [sortBy]),
    getSortAsc: useCallback(() => sortAsc, [sortAsc]),
  };

  useEffect(() => {
    onChangeRef.current?.([{
      property: sortBy,
      direction: sortAsc ? 'ASC' : 'DESC',
    }]);
  }, [sortBy, sortAsc]);

  return helpers;
};

export default useSortState;
