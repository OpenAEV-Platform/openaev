import { useEffect, useState } from 'react';

import {
  type CatalogFacetFilters,
  type CatalogSort,
  CONNECTOR_TYPE_ORDER,
  DEPLOYMENT_BUILT_IN,
  DEPLOYMENT_EXTERNAL,
  EMPTY_FACET_FILTERS,
  type FacetGroupId,
  STATUS_COMMUNITY,
  STATUS_FILIGRAN,
} from './catalog-facets';

const SORT_VALUES: CatalogSort[] = ['name_asc', 'name_desc', 'deployed_desc'];
const DEFAULT_SORT: CatalogSort = 'name_asc';
const STATUS_VALUES: string[] = [STATUS_FILIGRAN, STATUS_COMMUNITY];
const DEPLOYMENT_VALUES: string[] = [DEPLOYMENT_EXTERNAL, DEPLOYMENT_BUILT_IN];
const TYPE_VALUES: string[] = CONNECTOR_TYPE_ORDER;

// Deduplicated so hand-crafted URLs with repeated values (type=COLLECTOR,COLLECTOR)
// cannot produce duplicate filter chips or duplicate React keys.
const parseListParam = (value: string | null): string[] => {
  if (!value) return [];
  return [...new Set(value.split(',').map(v => v.trim()).filter(v => v.length > 0))];
};

/**
 * Marketplace filter state (facets, search keyword, sort) persisted in the URL
 * query string, so filters survive reloads and can be shared as links. Invalid
 * values found in the URL are silently dropped; the URL is written back in a
 * canonical form (sorted comma-separated values, params omitted when empty).
 */
const useCatalogFilters = () => {
  const [filters, setFilters] = useState<CatalogFacetFilters>(() => {
    const params = new URLSearchParams(window.location.search);
    return {
      types: parseListParam(params.get('type')).filter(value => TYPE_VALUES.includes(value)),
      // Use cases are free-form catalog values: unknown ones simply match nothing.
      useCases: parseListParam(params.get('useCase')),
      status: parseListParam(params.get('status')).filter(value => STATUS_VALUES.includes(value)),
      deployment: parseListParam(params.get('deployment')).filter(value => DEPLOYMENT_VALUES.includes(value)),
    };
  });
  const [keyword, setKeyword] = useState<string>(
    () => new URLSearchParams(window.location.search).get('search') ?? '',
  );
  const [sort, setSort] = useState<CatalogSort>(() => {
    const value = new URLSearchParams(window.location.search).get('sort');
    return SORT_VALUES.find(mode => mode === value) ?? DEFAULT_SORT;
  });

  useEffect(() => {
    const params = new URLSearchParams();
    if (keyword) params.set('search', keyword);
    // Values are sorted so the same logical filter set always produces the
    // same canonical URL regardless of selection order.
    if (filters.types.length > 0) params.set('type', [...filters.types].sort().join(','));
    if (filters.useCases.length > 0) params.set('useCase', [...filters.useCases].sort().join(','));
    if (filters.status.length > 0) params.set('status', [...filters.status].sort().join(','));
    if (filters.deployment.length > 0) params.set('deployment', [...filters.deployment].sort().join(','));
    if (sort !== DEFAULT_SORT) params.set('sort', sort);

    const queryString = params.toString();
    const newUrl = queryString ? `${window.location.pathname}?${queryString}` : window.location.pathname;
    // replaceState (not navigate): filter changes should not pollute the
    // browser history, but a reload / back to this entry restores the filters.
    window.history.replaceState({}, '', newUrl);
  }, [filters, keyword, sort]);

  const onToggleFacet = (groupId: FacetGroupId, value: string) => {
    setFilters(prev => ({
      ...prev,
      [groupId]: prev[groupId].includes(value)
        ? prev[groupId].filter(v => v !== value)
        : [...prev[groupId], value],
    }));
  };

  const onClearFacets = () => setFilters(EMPTY_FACET_FILTERS);

  return {
    filters,
    keyword,
    setKeyword,
    sort,
    setSort,
    onToggleFacet,
    onClearFacets,
  };
};

export default useCatalogFilters;
