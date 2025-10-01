import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { isExistFilter } from './FilterUtils';

const updateFilters = (filters: FilterGroup, updateFn: (filter: Filter) => Filter): FilterGroup => {
  return {
    ...filters,
    filters: filters.filters?.map(updateFn),
  } as FilterGroup;
};

export const handleSwitchMode = (filters: FilterGroup) => {
  return {
    ...filters,
    mode: filters.mode === 'and' ? 'or' : 'and',
  } as FilterGroup;
};

export const handleAddFilterWithEmptyValueUtil = (filterGroup: FilterGroup, filter: Filter) => {
  const filters = isExistFilter(filterGroup, filter.key)
    ? filterGroup.filters ?? []
    : [
        ...filterGroup.filters ?? [],
        filter,
      ];
  return {
    ...filterGroup,
    filters,
  };
};

export const handleAddSingleValueFilterUtil = (filters: FilterGroup, key: string, value: string) => {
  return updateFilters(filters, f => (f.key === key
    ? {
        ...f,
        values: [value],
      }
    : f));
};

export const handleAddMultipleValueFilterUtil = (filters: FilterGroup, key: string, values: string[]) => {
  return updateFilters(filters, f => (f.key === key
    ? {
        ...f,
        values,
      }
    : f));
};

export const handleChangeOperatorFiltersUtil = (filters: FilterGroup, key: string, operator: Filter['operator']) => {
  return updateFilters(filters, f => (f.key === key
    ? {
        ...f,
        operator,
        values: operator && ['empty', 'not_empty'].includes(operator) ? [] : f.values,
      }
    : f));
};

export const handleRemoveFilterUtil = (filters: FilterGroup, key: string) => {
  return {
    ...filters,
    filters: filters.filters?.filter(f => f.key !== key),
  };
};
