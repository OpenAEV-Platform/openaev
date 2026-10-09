import { useEffect, useState } from 'react';

import {
  type CatalogFacetCounts,
  fetchCatalogPayloadFacets,
  installAllCatalogPayloads,
  installCatalogPayloads,
  searchCatalogPayloads,
} from '../../../../actions/catalog/payload-catalog-actions';
import { type CatalogPayload } from '../../../../actions/catalog/payload-catalog-preview';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type SearchPaginationInput } from '../../../../utils/api-types';

const useCatalogPayloads = () => {
  const [items, setItems] = useState<CatalogPayload[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [facetCounts, setFacetCounts] = useState<CatalogFacetCounts | null>(null);
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal-catalog',
    buildSearchPagination({
      sorts: [{
        property: 'catalog_item_published_at',
        direction: 'DESC',
      }],
    }),
  );

  const fetchItems = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchCatalogPayloads(input).finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchCatalogPayloadFacets(searchPaginationInput).then(({ data }) => setFacetCounts(data));
  }, [searchPaginationInput]);

  const markInstalled = (externalIds: string[]) => setInstalledIds(previous => new Set([...previous, ...externalIds]));
  const install = (externalId: string) => installCatalogPayloads([externalId]).then(markInstalled);
  const installAll = () => installAllCatalogPayloads().then(markInstalled);

  const hasActiveFilters = (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || !!searchPaginationInput.textSearch;
  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  return {
    items,
    setItems,
    loading,
    fetchItems,
    queryableHelpers,
    searchPaginationInput,
    facetCounts,
    hasActiveFilters,
    handleResetFilters,
    isInstalled: (externalId: string) => installedIds.has(externalId),
    install,
    installAll,
  };
};

export default useCatalogPayloads;
