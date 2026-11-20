import { type ReactElement } from 'react';

import { useFormatter } from '../../../i18n';
import { type FacetSection, FacetSidebar } from '../../facets/FacetFilters';
import { type FacetState } from './useMarketplaceFilters';

export interface MarketplaceFacet<K extends string> {
  key: K;
  label: string;
  // Without values, the facet lists those of the items.
  values?: string[];
  valueLabel?: (value: string) => string;
  icon?: (value: string) => ReactElement | undefined;
}

type Props<K extends string> = FacetState<K> & { facets: MarketplaceFacet<K>[] };

const MarketplaceFacetSidebar = <K extends string>({ facets, filters, counts, values, onToggle }: Props<K>) => {
  const { t } = useFormatter();
  const sections: FacetSection[] = facets.map((facet) => {
    const facetCounts = counts(facet.key);
    return {
      id: facet.key,
      label: t(facet.label),
      rows: (facet.values ?? values(facet.key)).map((value) => {
        const icon = facet.icon?.(value);
        return {
          value,
          label: t(facet.valueLabel?.(value) ?? value),
          count: facetCounts[value] ?? 0,
          icon: icon && (() => icon),
          checked: filters.values[facet.key].includes(value),
          onToggle: () => onToggle(facet.key, value),
        };
      }),
    };
  });

  return <FacetSidebar sections={sections.filter(section => section.rows.length > 0)} />;
};

export default MarketplaceFacetSidebar;
