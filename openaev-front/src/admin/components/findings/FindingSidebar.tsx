import { useCallback, useMemo } from 'react';

import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import {
  ACTIONABLE_FINDING_CATEGORIES,
  INFORMATIVE_FINDING_CATEGORY,
} from './findingAggregationCategories';

const CATEGORY_FILTER_KEY = 'finding_aggregation_category';
const SOURCE_TYPE_FILTER_KEY = 'finding_source_type';

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
  sourceTypes: string[];
}

const FindingSidebar = ({ searchPaginationInput, filterHelpers, sourceTypes }: Props) => {
  const { t } = useFormatter();
  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );
  const categoryValues = useMemo(
    () => filters.find((filter: Filter) => filter.key === CATEGORY_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const sourceTypeValues = useMemo(
    () => filters.find((filter: Filter) => filter.key === SOURCE_TYPE_FILTER_KEY)?.values ?? [],
    [filters],
  );

  const setValues = useCallback((key: string, next: string[]) => {
    const existing = filters.find((filter: Filter) => filter.key === key);
    if (next.length === 0) {
      if (existing?.id) {
        filterHelpers.handleRemoveFilterById(existing.id);
      } else {
        filterHelpers.handleRemoveFilterByKey(key);
      }
      return;
    }
    if (existing?.id) {
      filterHelpers.handleUpdateFilterById(existing.id, {
        values: next,
        mode: 'or',
      });
      return;
    }
    filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key,
      operator: 'eq',
      values: next,
      // Checkbox values within one facet are alternatives; separate facets remain combined by
      // the enclosing filter group's AND mode.
      mode: 'or',
    });
  }, [filterHelpers, filters]);

  const row = useCallback((category: typeof INFORMATIVE_FINDING_CATEGORY): FacetRow => {
    const Icon = category.icon;
    return {
      value: category.value,
      label: t(category.label),
      checked: categoryValues.includes(category.value),
      icon: () => <Icon />,
      onToggle: () => setValues(
        CATEGORY_FILTER_KEY,
        categoryValues.includes(category.value)
          ? categoryValues.filter(value => value !== category.value)
          : [...categoryValues, category.value],
      ),
    };
  }, [categoryValues, setValues, t]);

  const availableSourceTypes = useMemo(
    () => [...new Set([...sourceTypes, ...sourceTypeValues])].sort((left, right) => left.localeCompare(right)),
    [sourceTypeValues, sourceTypes],
  );

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
    {
      id: 'source-type',
      label: t('Source type'),
      rows: availableSourceTypes.map(sourceType => ({
        value: sourceType,
        label: sourceType,
        checked: sourceTypeValues.includes(sourceType),
        icon: () => <InjectIcon type={sourceType} />,
        onToggle: () => setValues(
          SOURCE_TYPE_FILTER_KEY,
          sourceTypeValues.includes(sourceType)
            ? sourceTypeValues.filter(value => value !== sourceType)
            : [...sourceTypeValues, sourceType],
        ),
      })),
    },
  ];

  return <FacetSidebar sections={sections.filter(section => section.rows.length > 0)} />;
};

export default FindingSidebar;
