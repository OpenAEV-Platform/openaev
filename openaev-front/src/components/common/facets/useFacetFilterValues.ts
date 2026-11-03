import { useCallback, useMemo } from 'react';

import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import { type FilterHelpers } from '../queryable/filter/FilterHelpers';
import { generateFilterId } from '../queryable/filter/FilterUtils';

// Reads and toggles the values of a facet backed by an "eq" filter of the search input.
const useFacetFilterValues = (searchPaginationInput: SearchPaginationInput, filterHelpers: FilterHelpers) => {
  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const valuesOf = useCallback(
    (key: string) => filters.find((f: Filter) => f.key === key)?.values ?? [],
    [filters],
  );

  const setFilterValues = useCallback(
    (key: string, values: string[]) => {
      const existing = filters.find((f: Filter) => f.key === key);
      if (values.length === 0) {
        if (existing?.id) {
          filterHelpers.handleRemoveFilterById(existing.id);
        } else {
          filterHelpers.handleRemoveFilterByKey(key);
        }
        return;
      }
      if (existing?.id) {
        filterHelpers.handleUpdateValuesById(existing.id, values);
        return;
      }
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key,
        operator: 'eq',
        values,
        mode: 'and',
      });
    },
    [filterHelpers, filters],
  );

  const toggleValue = useCallback(
    (key: string, value: string) => {
      const current = valuesOf(key);
      setFilterValues(key, current.includes(value) ? current.filter(v => v !== value) : [...current, value]);
    },
    [setFilterValues, valuesOf],
  );

  return {
    filters,
    valuesOf,
    toggleValue,
  };
};

export default useFacetFilterValues;
