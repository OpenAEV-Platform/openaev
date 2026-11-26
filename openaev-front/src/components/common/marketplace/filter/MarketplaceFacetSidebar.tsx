import { type ReactElement } from 'react';

import { type CatalogFacetCounts } from '../../../../actions/catalog/catalog-helper';
import { type SearchPaginationInput } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import { type FacetSection, FacetSidebar } from '../../facets/FacetFilters';
import useFacetFilterToggle from '../../facets/useFacetFilterToggle';
import { type FilterHelpers } from '../../queryable/filter/FilterHelpers';

export interface MarketplaceFacet {
  // The filter key of the search.
  key: string;
  label: string;
  // Without values, the facet lists those the counts hold.
  values?: string[];
  valueLabel?: (value: string) => string;
  icon?: (value: string) => ReactElement | undefined;
}

interface Props {
  facets: MarketplaceFacet[];
  // Null until the server answers: the rows show no count meanwhile.
  facetCounts: CatalogFacetCounts | null;
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

// Facet rows as "eq" filters of the search, with the counts the server gives under the other filters.
const MarketplaceFacetSidebar = ({ facets, facetCounts, searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();
  const { valuesOf, toggle } = useFacetFilterToggle(searchPaginationInput, filterHelpers);
  const sections: FacetSection[] = facets.map((facet) => {
    const counts = facetCounts?.[facet.key];
    const checked = valuesOf(facet.key);
    return {
      id: facet.key,
      label: t(facet.label),
      rows: (facet.values ?? Object.keys(counts ?? {}).sort()).map((value) => {
        const icon = facet.icon?.(value);
        return {
          value,
          label: t(facet.valueLabel?.(value) ?? value),
          count: counts ? counts[value] ?? 0 : undefined,
          icon: icon && (() => icon),
          checked: checked.includes(value),
          onToggle: () => toggle(facet.key, value),
        };
      }),
    };
  });

  return <FacetSidebar sections={sections.filter(section => section.rows.length > 0)} />;
};

export default MarketplaceFacetSidebar;
