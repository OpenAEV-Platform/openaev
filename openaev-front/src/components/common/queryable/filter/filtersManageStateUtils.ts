import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { generateFilterId, isExistingFilter } from './FilterUtils';

const normalizeFilterGroupMode = (filterGroup: FilterGroup): FilterGroup => {
  const filterCount = filterGroup.filters?.length ?? 0;
  if (filterCount <= 1) {
    return {
      ...filterGroup,
      mode: 'and',
    };
  }
  return filterGroup;
};

const updateFilters = (filters: FilterGroup, updateFn: (filter: Filter) => Filter): FilterGroup => {
  return normalizeFilterGroupMode({
    ...filters,
    filters: filters.filters?.map(updateFn),
  } as FilterGroup);
};

export const handleSwitchMode = (filters: FilterGroup) => {
  return normalizeFilterGroupMode({
    ...filters,
    mode: filters.mode === 'and' ? 'or' : 'and',
  } as FilterGroup);
};

export const handleAddFilterWithEmptyValueUtil = (filterGroup: FilterGroup, filter: Filter) => {
  const filterWithId = {
    ...filter,
    id: filter.id || generateFilterId(),
  };
  return normalizeFilterGroupMode({
    ...filterGroup,
    filters: [...filterGroup.filters ?? [], filterWithId],
  });
};

export const handleAddSingleValueFilterUtil = (filters: FilterGroup, key: string, value: string) => {
  const existingFilter = isExistingFilter(filters, key);
  if (existingFilter) {
    return updateFilters(filters, f => (f.key === key
      ? {
          ...f,
          values: [value],
        }
      : f));
  } else {
    const newFilter: Filter = {
      id: generateFilterId(),
      key,
      values: [value],
      operator: 'eq',
      mode: 'or',
    };
    return handleAddFilterWithEmptyValueUtil(filters, newFilter);
  }
};

export const handleAddMultipleValueFilterUtil = (filters: FilterGroup, key: string, values: string[]) => {
  const existingFilter = isExistingFilter(filters, key);
  if (existingFilter) {
    return updateFilters(filters, f => (f.key === key
      ? {
          ...f,
          values: Array.from(new Set([...(f.values ?? []), ...values])),
        }
      : f));
  } else {
    const newFilter: Filter = {
      id: generateFilterId(),
      key,
      values,
      operator: 'eq',
      mode: 'or',
    };
    return handleAddFilterWithEmptyValueUtil(filters, newFilter);
  }
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
  return normalizeFilterGroupMode({
    ...filters,
    filters: filters.filters?.filter(f => f.key !== key),
  });
};

export const handleSwitchLocalModeUtil = (filters: FilterGroup, key: string): FilterGroup => {
  return updateFilters(filters, f =>
    f.key === key
      ? {
          ...f,
          mode: f.mode === 'and' ? 'or' : 'and',
        }
      : f,
  );
};

export const handleRemoveFilterByIdUtil = (filters: FilterGroup, filterId: string) => {
  return normalizeFilterGroupMode({
    ...filters,
    filters: filters.filters?.filter(f => f.id !== filterId),
  });
};

function updateFilterById(filters: FilterGroup, filterId: string, updateFn: (filter: Filter) => Filter): FilterGroup {
  return normalizeFilterGroupMode({
    ...filters,
    filters: filters.filters?.map(f => f.id === filterId ? updateFn(f) : f),
  } as FilterGroup);
}

export const handleUpdateFilterByIdUtil = (filters: FilterGroup, filterId: string, updates: Partial<Omit<Filter, 'id'>>) => {
  return updateFilterById(filters, filterId, f => ({
    ...f,
    ...updates,
  }));
};

export const handleSwitchLocalModeByIdUtil = (filters: FilterGroup, filterId: string): FilterGroup => {
  return updateFilterById(filters, filterId, f => ({
    ...f,
    mode: f.mode === 'and' ? 'or' : 'and',
  }));
};

export const handleChangeOperatorByIdUtil = (filters: FilterGroup, filterId: string, operator: Filter['operator']) => {
  return updateFilterById(filters, filterId, f => ({
    ...f,
    operator,
    values: operator && ['empty', 'not_empty'].includes(operator) ? [] : f.values,
  }));
};
