import { useCallback, useEffect, useRef, useState } from 'react';

import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { type FilterHelpers } from './FilterHelpers';
import {
  handleAddFilterWithEmptyValueUtil,
  handleAddMultipleValueFilterUtil,
  handleAddSingleValueFilterUtil,
  handleChangeOperatorByIdUtil,
  handleChangeOperatorFiltersUtil,
  handleRemoveFilterByIdUtil, handleRemoveFilterUtil,
  handleSwitchLocalModeByIdUtil,
  handleSwitchLocalModeUtil, handleSwitchMode,
  handleUpdateFilterByIdUtil,
} from './filtersManageStateUtils';
import { emptyFilterGroup, generateFilterId } from './FilterUtils';

interface Props {
  filters: FilterGroup;
  latestAddFilterId?: string;
}

// Utility to ensure that all filters have an ID
const ensureFiltersHaveIds = (filterGroup: FilterGroup): FilterGroup => {
  if (!filterGroup.filters) {
    return filterGroup;
  }

  return {
    ...filterGroup,
    filters: filterGroup.filters.map(filter => ({
      ...filter,
      id: filter.id || generateFilterId(),
    })),
  };
};

const useFiltersState = (
  initFilters: FilterGroup = emptyFilterGroup,
  defaultFilters: FilterGroup = emptyFilterGroup,
  onChange?: (value: FilterGroup) => void,
): [FilterGroup, FilterHelpers] => {
  const [filtersState, setFiltersState] = useState<Props>({ filters: ensureFiltersHaveIds(initFilters) });

  // Use ref to store onChange to avoid triggering useEffect when callback reference changes
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // Use ref for defaultFilters to keep handleClearAllFilters stable
  const defaultFiltersRef = useRef(defaultFilters);
  defaultFiltersRef.current = defaultFilters;

  const helpers: FilterHelpers = {
    // Switch filter group operator
    handleSwitchMode: useCallback(() => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleSwitchMode(prevState.filters),
      }));
    }, []),
    // Switch local mode for a filter
    handleSwitchLocalMode: useCallback((key: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleSwitchLocalModeUtil(prevState.filters, key),
      }));
    }, []),
    // Add Filter
    handleAddFilterWithEmptyValue: useCallback((filter: Filter) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddFilterWithEmptyValueUtil(prevState.filters, filter),
      }));
    }, []),
    // Add value to a filter
    handleAddSingleValueFilter: useCallback((key: string, value: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddSingleValueFilterUtil(prevState.filters, key, value),
      }));
    }, []),
    // Add multiple value to a filter
    handleAddMultipleValueFilter: useCallback((key: string, values: string[]) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddMultipleValueFilterUtil(prevState.filters, key, values),
      }));
    }, []),
    // Change operator in filter
    handleChangeOperatorFilters: useCallback((key: string, operator: Filter['operator']) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleChangeOperatorFiltersUtil(prevState.filters, key, operator),
      }));
    }, []),
    // Clear all filters
    handleClearAllFilters: useCallback(() => {
      setFiltersState({ filters: defaultFiltersRef.current });
    }, []),
    // Remove a Filter
    handleRemoveFilterByKey: useCallback((key: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleRemoveFilterUtil(prevState.filters, key),
      }));
    }, []),
    handleRemoveFilterById: useCallback((filterId: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleRemoveFilterByIdUtil(prevState.filters, filterId),
      }));
    }, []),

    handleUpdateFilterById: useCallback((filterId: string, updates: Partial<Omit<Filter, 'id'>>) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleUpdateFilterByIdUtil(prevState.filters, filterId, updates),
      }));
    }, []),

    handleAddFilter: useCallback((filterData: Omit<Filter, 'id'>) => {
      const newFilter: Filter = {
        ...filterData,
        id: generateFilterId(),
      };
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddFilterWithEmptyValueUtil(prevState.filters, newFilter),
      }));
    }, []),

    handleSwitchLocalModeById: useCallback((filterId: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleSwitchLocalModeByIdUtil(prevState.filters, filterId),
      }));
    }, []),

    handleChangeOperatorById: useCallback((filterId: string, operator: Filter['operator']) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleChangeOperatorByIdUtil(prevState.filters, filterId, operator),
      }));
    }, []),

    handleUpdateValuesById: useCallback((filterId: string, values: string[]) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleUpdateFilterByIdUtil(prevState.filters, filterId, { values }),
      }));
    }, []),
  };

  useEffect(() => {
    onChangeRef.current?.(filtersState.filters);
  }, [filtersState]);

  return [filtersState.filters, helpers];
};

export default useFiltersState;
