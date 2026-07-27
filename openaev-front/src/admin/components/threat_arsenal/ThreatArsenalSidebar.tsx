import { useCallback, useMemo } from 'react';

import {
  type AuthorOption,
  buildAuthorRows,
  buildStatusRows,
  useAuthorFacetFilter,
} from '../../../components/common/facets/ContractFacets';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
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

  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const platformValues = useMemo(
    () => filters.find((f: Filter) => f.key === PLATFORM_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const statusValues = useMemo(
    () => filters.find((f: Filter) => f.key === STATUS_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const {
    authorValues,
    noAuthorActive,
    toggleAuthorValue,
    toggleNoAuthor,
  } = useAuthorFacetFilter(AUTHOR_FILTER_KEY, filters, filterHelpers);

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

  const toggleValue = useCallback(
    (key: string, current: string[], value: string) => {
      const next = current.includes(value)
        ? current.filter(v => v !== value)
        : [...current, value];
      setFilterValues(key, next);
    },
    [setFilterValues],
  );

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
      onToggle: () => toggleValue(PLATFORM_FILTER_KEY, platformValues, platform),
    }));

    const statusRows = buildStatusRows({
      t,
      statusValues,
      statusCounts: facetCounts?.statuses,
      toggle: value => toggleValue(STATUS_FILTER_KEY, statusValues, value),
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
