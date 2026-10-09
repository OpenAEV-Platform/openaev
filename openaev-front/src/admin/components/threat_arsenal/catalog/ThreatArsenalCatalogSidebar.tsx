import { type FunctionComponent, useMemo } from 'react';

import {
  CATALOG_AUTHOR_FILTER,
  CATALOG_DOMAIN_FILTER,
  CATALOG_PLATFORM_FILTER,
  type CatalogFacetCounts,
} from '../../../../actions/catalog/payload-catalog-actions';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import useFacetFilterValues from '../../../../components/common/facets/useFacetFilterValues';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type SearchPaginationInput } from '../../../../utils/api-types';

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

interface Props {
  facetCounts: CatalogFacetCounts | null;
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

const ThreatArsenalCatalogSidebar: FunctionComponent<Props> = ({ facetCounts, searchPaginationInput, filterHelpers }) => {
  const { t } = useFormatter();
  const { valuesOf, toggleValue } = useFacetFilterValues(searchPaginationInput, filterHelpers);

  const sections: FacetSection[] = useMemo(() => {
    const rows = (key: string, values: string[], counts: Record<string, number> | undefined, withIcon = false): FacetRow[] =>
      values.map(value => ({
        value,
        label: t(value),
        count: counts ? counts[value] ?? 0 : undefined,
        icon: withIcon ? () => <PlatformIcon platform={value} width={16} /> : undefined,
        checked: valuesOf(key).includes(value),
        onToggle: () => toggleValue(key, value),
      }));
    const sortedKeys = (counts: Record<string, number> | undefined) => Object.keys(counts ?? {}).sort();

    return [
      {
        id: 'domains',
        label: t('Domains'),
        rows: rows(CATALOG_DOMAIN_FILTER, sortedKeys(facetCounts?.domains), facetCounts?.domains),
      },
      {
        id: 'platforms',
        label: t('Platform'),
        rows: rows(CATALOG_PLATFORM_FILTER, PLATFORMS, facetCounts?.platforms, true),
      },
      {
        id: 'author',
        label: t('Author'),
        rows: rows(CATALOG_AUTHOR_FILTER, sortedKeys(facetCounts?.authors), facetCounts?.authors),
      },
    ].filter(section => section.rows.length > 0);
  }, [facetCounts, valuesOf, toggleValue, t]);

  return <FacetSidebar sections={sections} />;
};

export default ThreatArsenalCatalogSidebar;
