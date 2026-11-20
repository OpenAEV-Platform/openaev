import { useCallback } from 'react';

import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import { type FilterHelpers } from '../queryable/filter/FilterHelpers';
import { generateFilterId } from '../queryable/filter/FilterUtils';
import {Option} from "../../../utils/Option";

// Facet rows as "eq" filters of the search: the values set for a key, and toggling one of them.
const useFacetFilterToggle = (searchPaginationInput: SearchPaginationInput, filterHelpers: FilterHelpers) => {
  const filters = searchPaginationInput.filterGroup?.filters ?? [];
  const valuesOf = (key: string): string[] => filters.find((f: Filter) => f.key === key)?.values ?? [];

  const toggle = useCallback((key: string, value: string) => {
    const existing = filters.find((f: Filter) => f.key === key);
    const current = existing?.values ?? [];
    const next = current.includes(value) ? current.filter(v => v !== value) : [...current, value];
    if (next.length === 0) {
      if (existing?.id) {
        filterHelpers.handleRemoveFilterById(existing.id);
      } else {
        filterHelpers.handleRemoveFilterByKey(key);
      }
    } else if (existing?.id) {
      filterHelpers.handleUpdateValuesById(existing.id, next);
    } else {
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key,
        operator: 'eq',
        values: next,
        mode: 'and',
      });
    }
  }, [filterHelpers, filters]);

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

  const toggleValueWithCurrent = useCallback(
      (key: string, current: string[], value: string) => {
        const next = current.includes(value)
            ? current.filter(v => v !== value)
            : [...current, value];
        setFilterValues(key, next);
      },
      [setFilterValues],
  );

  const toggleAndTranslateOptionWithCurrent = useCallback(
      (key: string, current: string[], selectedOption: Option, options: Option[]) => {
        if (selectedOption.id && selectedOption.label) {
          const next = current.includes(selectedOption.label)
              ? current.filter(v => v !== selectedOption.label)
              : [...current, selectedOption.label];
          const translatedNext: string[] = options
              .filter(option => next.includes(option.label!))
              .map(option => option.id!);
          setFilterValues(key, translatedNext);
        }
      },
      [setFilterValues],
  );

  return {
    valuesOf,
    toggle,
    toggleValueWithCurrent,
    toggleAndTranslateOptionWithCurrent,
    setFilterValues,
  };
};

export default useFacetFilterToggle;
