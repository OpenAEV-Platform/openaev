import { useCallback, useMemo } from 'react';

import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import {
  ACTIONABLE_FINDING_CATEGORIES,
  INFORMATIVE_FINDING_CATEGORY,
} from './findingAggregationCategories';

const CATEGORY_FILTER_KEY = 'finding_aggregation_category';

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

const FindingSidebar = ({ searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();
  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );
  const values = useMemo(
    () => filters.find((filter: Filter) => filter.key === CATEGORY_FILTER_KEY)?.values ?? [],
    [filters],
  );

  const setValues = useCallback((next: string[]) => {
    const existing = filters.find((filter: Filter) => filter.key === CATEGORY_FILTER_KEY);
    if (next.length === 0) {
      if (existing?.id) {
        filterHelpers.handleRemoveFilterById(existing.id);
      } else {
        filterHelpers.handleRemoveFilterByKey(CATEGORY_FILTER_KEY);
      }
      return;
    }
    if (existing?.id) {
      filterHelpers.handleUpdateValuesById(existing.id, next);
      return;
    }
    filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key: CATEGORY_FILTER_KEY,
      operator: 'eq',
      values: next,
      mode: 'and',
    });
  }, [filterHelpers, filters]);

  const row = useCallback((category: typeof INFORMATIVE_FINDING_CATEGORY): FacetRow => {
    const Icon = category.icon;
    return {
      value: category.value,
      label: t(category.label),
      checked: values.includes(category.value),
      icon: () => <Icon />,
      onToggle: () => setValues(
        values.includes(category.value)
          ? values.filter(value => value !== category.value)
          : [...values, category.value],
      ),
    };
  }, [setValues, t, values]);

  const sections: FacetSection[] = [
    {
      id: 'actionable',
      label: t('Actionable'),
      rows: ACTIONABLE_FINDING_CATEGORIES.map(row),
    },
    {
      id: 'informative',
      label: t('Informative'),
      rows: [row(INFORMATIVE_FINDING_CATEGORY)],
    },
  ];

  return <FacetSidebar sections={sections} />;
};

export default FindingSidebar;
