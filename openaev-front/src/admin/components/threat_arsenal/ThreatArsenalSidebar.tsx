import { useMemo } from 'react';

import {
  type AuthorOption,
  buildAuthorRows,
  buildStatusRows,
  useAuthorFacetFilter,
} from '../../../components/common/facets/ContractFacets';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import useFacetFilterValues from '../../../components/common/facets/useFacetFilterValues';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type SearchPaginationInput } from '../../../utils/api-types';
import { type IconBarElement } from '../common/domains/IconBar-model';
import { type ThreatArsenalFacetCounts } from './useThreatArsenalFacetCounts';

const PLATFORM_FILTER_KEY = 'action_platforms';
const STATUS_FILTER_KEY = 'action_payload_status';
const AUTHOR_FILTER_KEY = 'action_author';
const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

interface Props {
  /** Domain facet rows (with live counts + icons), already ordered. */
  domainElements: IconBarElement[];
  /** Distinct authors present in the loaded page (id + label + type). */
  authorOptions: AuthorOption[];
  /** Platform + status counts under the current filters (null until loaded). */
  facetCounts: ThreatArsenalFacetCounts | null;
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

// The Threat Arsenal "basket": a sticky faceted sidebar (mirrors the
// integrations marketplace CatalogSidebar) whose rows toggle REAL backend
// filters through `filterHelpers`. The generic "Add filter" bar still handles
// every other property (injectors, tags, dates...).
const ThreatArsenalSidebar = ({ domainElements, authorOptions, facetCounts, searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();

  const { filters, valuesOf, toggleValue } = useFacetFilterValues(searchPaginationInput, filterHelpers);
  const platformValues = valuesOf(PLATFORM_FILTER_KEY);
  const statusValues = valuesOf(STATUS_FILTER_KEY);
  const {
    authorValues,
    noAuthorActive,
    toggleAuthorValue,
    toggleNoAuthor,
  } = useAuthorFacetFilter(AUTHOR_FILTER_KEY, filters, filterHelpers);

  const sections: FacetSection[] = useMemo(() => {
    const domainRows: FacetRow[] = domainElements.map(element => ({
      value: element.type ?? element.name,
      label: t(element.name),
      count: element.count ?? 0,
      icon: element.icon,
      checked: element.color === 'success',
      onToggle: element.function,
    }));

    // Counts stay undefined (no badge, row clickable) until the aggregation
    // endpoint answers; afterwards zero-count rows grey out like the domain facet.
    const platformRows: FacetRow[] = PLATFORMS.map(platform => ({
      value: platform,
      label: t(platform),
      count: facetCounts ? facetCounts.platforms[platform] ?? 0 : undefined,
      icon: () => <PlatformIcon platform={platform} width={16} />,
      checked: platformValues.includes(platform),
      onToggle: () => toggleValue(PLATFORM_FILTER_KEY, platform),
    }));

    const statusRows = buildStatusRows({
      t,
      statusValues,
      statusCounts: facetCounts?.statuses,
      toggle: value => toggleValue(STATUS_FILTER_KEY, value),
    });

    const authorRows = buildAuthorRows({
      authorOptions,
      authorValues,
      noAuthorActive,
      toggleAuthorValue,
      toggleNoAuthor,
      noAuthorLabel: t('No author'),
    });

    return [
      {
        id: 'domains',
        label: t('Domains'),
        rows: domainRows,
      },
      {
        id: 'platforms',
        label: t('Platform'),
        rows: platformRows,
      },
      {
        id: 'status',
        label: t('Status'),
        rows: statusRows,
      },
      {
        id: 'author',
        label: t('Author'),
        rows: authorRows,
      },
    ].filter(section => section.rows.length > 0);
  }, [domainElements, authorOptions, facetCounts, platformValues, statusValues, authorValues, noAuthorActive, toggleValue, toggleAuthorValue, toggleNoAuthor, t]);

  return <FacetSidebar sections={sections} />;
};

export default ThreatArsenalSidebar;
