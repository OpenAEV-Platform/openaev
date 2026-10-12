import { type FunctionComponent, type ReactElement, useMemo } from 'react';

import { type SearchPaginationInput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import { type FacetSection, FacetSidebar } from '../facets/FacetFilters';
import useFacetFilterValues from '../facets/useFacetFilterValues';
import { type FilterHelpers } from '../queryable/filter/FilterHelpers';

export interface MarketplaceFacet {
  filterKey: string;
  label: string;
  /** Fixed universe of values; defaults to the values present in the counts. */
  values?: string[];
  valueLabel?: (value: string) => string;
  icon?: (value: string) => ReactElement;
}

interface Props {
  facets: MarketplaceFacet[];
  /** Value counts per filter key under the current search (null until loaded). */
  facetCounts: Record<string, Record<string, number>> | null;
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
  /** Sections built by the caller (e.g. an author facet), appended after the others. */
  extraSections?: FacetSection[];
}

// Facet sidebar over "eq" filters of the search input, one section per facet.
const MarketplaceFacetSidebar: FunctionComponent<Props> = ({ facets, facetCounts, searchPaginationInput, filterHelpers, extraSections = [] }) => {
  const { t } = useFormatter();
  const { valuesOf, toggleValue } = useFacetFilterValues(searchPaginationInput, filterHelpers);

  const sections: FacetSection[] = useMemo(() => facets.map((facet) => {
    const counts = facetCounts?.[facet.filterKey];
    const values = facet.values ?? Object.keys(counts ?? {}).sort();
    return {
      id: facet.filterKey,
      label: t(facet.label),
      rows: values.map(value => ({
        value,
        label: facet.valueLabel ? facet.valueLabel(value) : t(value),
        count: counts ? counts[value] ?? 0 : undefined,
        icon: facet.icon ? () => facet.icon!(value) : undefined,
        checked: valuesOf(facet.filterKey).includes(value),
        onToggle: () => toggleValue(facet.filterKey, value),
      })),
    };
  }).filter(section => section.rows.length > 0), [facets, facetCounts, valuesOf, toggleValue, t]);

  return <FacetSidebar sections={[...sections, ...extraSections.filter(section => section.rows.length > 0)]} />;
};

export default MarketplaceFacetSidebar;
