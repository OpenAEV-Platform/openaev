import { useEffect, useState } from 'react';

import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { type FilterHelpers } from './FilterHelpers';
import {
  handleAddFilterWithEmptyValueUtil,
  handleAddMultipleValueFilterUtil,
  handleAddSingleValueFilterUtil,
  handleChangeOperatorFiltersUtil,
  handleRemoveFilterUtil,
  handleSwitchMode,
} from './filtersManageStateUtils';
import { emptyFilterGroup } from './FilterUtils';

interface Props {
  filters: FilterGroup;
  latestAddFilterId?: string;
}

const useFiltersState = (
  initFilters: FilterGroup = emptyFilterGroup,
  defaultFilters: FilterGroup = emptyFilterGroup,
  onChange?: (value: FilterGroup) => void,
): [FilterGroup, FilterHelpers] => {
  const [filtersState, setFiltersState] = useState<Props>({ filters: initFilters });
  const helpers: FilterHelpers = {
    // Switch filter group operator
    handleSwitchMode: () => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleSwitchMode(prevState.filters),
      }));
    },
    // Add Filter
    handleAddFilterWithEmptyValue: (filter: Filter) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddFilterWithEmptyValueUtil(prevState.filters, filter),
      }));
    },
    // Add value to a filter
    handleAddSingleValueFilter: (key: string, value: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddSingleValueFilterUtil(prevState.filters, key, value),
      }));
    },
    // Add multiple value to a filter
    handleAddMultipleValueFilter: (key: string, values: string[]) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleAddMultipleValueFilterUtil(prevState.filters, key, values),
      }));
    },
    // Change operator in filter
    handleChangeOperatorFilters: (key: string, operator: Filter['operator']) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleChangeOperatorFiltersUtil(prevState.filters, key, operator),
      }));
    },
    // Clear all filters
    handleClearAllFilters: () => {
      setFiltersState({ filters: defaultFilters });
    },
    // Remove a Filter
    handleRemoveFilterByKey: (key: string) => {
      setFiltersState(prevState => ({
        ...prevState,
        filters: handleRemoveFilterUtil(prevState.filters, key),
      }));
    },
  };

  useEffect(() => {
    onChange?.(filtersState.filters);
  }, [filtersState]);

  return [filtersState.filters, helpers];
};

export default useFiltersState;
