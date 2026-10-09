import { Button } from '@filigran/design-system';
import { Alert } from '@mui/material';

import MarketplaceBrowser from '../../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../../components/common/marketplace/MarketplaceGrid';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogSidebar from './ThreatArsenalCatalogSidebar';
import useCatalogPayloads from './useCatalogPayloads';

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
  } = useCatalogPayloads();

  return (
    <MarketplaceBrowser
      hero={(
        <Alert severity="info">
          {t('Preview: the catalog content is simulated until the catalog sync is available.')}
        </Alert>
      )}
      sidebar={(
        <ThreatArsenalCatalogSidebar
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
