import { Button } from '@filigran/design-system';

import {
  CATALOG_AUTHOR_FILTER,
  CATALOG_DOMAIN_FILTER,
  CATALOG_PLATFORM_FILTER,
  fetchCatalogPayloadFacets,
  installAllCatalogPayloads,
  installCatalogPayloads,
  searchCatalogPayloads,
} from '../../../../actions/catalog/payload-catalog-actions';
import { type CatalogPayload } from '../../../../actions/catalog/payload-catalog-preview';
import MarketplaceBrowser from '../../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceGrid from '../../../../components/common/marketplace/MarketplaceGrid';
import MarketplacePreviewNotice from '../../../../components/common/marketplace/MarketplacePreviewNotice';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import useMarketplaceCatalog from '../../../../components/common/marketplace/useMarketplaceCatalog';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';

const CATALOG_SORT_OPTIONS = [
  {
    field: 'catalog_item_name',
    label: 'Name',
  },
  {
    field: 'catalog_item_published_at',
    label: 'Published',
  },
];

const CATALOG_FACETS: MarketplaceFacet[] = [
  {
    filterKey: CATALOG_DOMAIN_FILTER,
    label: 'Domains',
  },
  {
    filterKey: CATALOG_PLATFORM_FILTER,
    label: 'Platform',
    values: ['Windows', 'Linux', 'MacOS'],
    icon: platform => <PlatformIcon platform={platform} width={16} />,
  },
  {
    filterKey: CATALOG_AUTHOR_FILTER,
    label: 'Author',
  },
];

const ThreatArsenalAvailable = () => {
  const { t } = useFormatter();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const {
    items,
    setItems,
    loading,
    fetchItems,
    queryableHelpers,
    searchPaginationInput,
    facetCounts,
    hasActiveFilters,
    handleResetFilters,
    isInstalled,
    install,
    installAll,
  } = useMarketplaceCatalog<CatalogPayload>({
    storageKey: 'threat-arsenal-catalog',
    defaultSorts: [{
      property: 'catalog_item_published_at',
      direction: 'DESC',
    }],
    search: searchCatalogPayloads,
    fetchFacets: fetchCatalogPayloadFacets,
    installOne: id => installCatalogPayloads([id]),
    installAll: installAllCatalogPayloads,
  });

  return (
    <MarketplaceBrowser
      hero={<MarketplacePreviewNotice />}
      sidebar={(
        <MarketplaceFacetSidebar
          facets={CATALOG_FACETS}
          facetCounts={facetCounts}
          searchPaginationInput={searchPaginationInput}
          filterHelpers={queryableHelpers.filterHelpers}
        />
      )}
      toolbar={(
        <PaginationComponentV2
          fetch={fetchItems}
          searchPaginationInput={searchPaginationInput}
          setContent={setItems}
          disableFilters
          queryableHelpers={queryableHelpers}
          filtersEndSlot={(
            <MarketplaceSortSelect
              sortHelpers={queryableHelpers.sortHelpers}
              options={CATALOG_SORT_OPTIONS}
              descendingFields={['catalog_item_published_at']}
            />
          )}
          topBarButtons={canInstall
            ? <Button onClick={installAll}>{t('Install all')}</Button>
            : null}
        />
      )}
    >
      <MarketplaceGrid
        loading={loading}
        isEmpty={items.length === 0}
        emptyState={<ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />}
      >
        {items.map(item => (
          <ThreatArsenalCatalogCard
            key={item.catalog_item_external_id}
            item={item}
            installed={isInstalled(item.catalog_item_external_id)}
            canInstall={canInstall}
            onInstall={() => install(item.catalog_item_external_id)}
          />
        ))}
      </MarketplaceGrid>
    </MarketplaceBrowser>
  );
};

export default ThreatArsenalAvailable;
