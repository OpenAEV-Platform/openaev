import { Button, Switch } from '@filigran/design-system';
import { CategoryOutlined } from '@mui/icons-material';
import { type ReactNode, useEffect, useState } from 'react';

import {
  type AvailableAction,
  availableActionFilterSchemas,
  fetchAvailableActionFacets,
  fetchAvailableActions,
  searchAvailableActions,
} from '../../../../actions/catalog/injector-contract-catalog-actions';
import {
  CATALOG_AUTHOR_FILTER,
  CATALOG_DOMAIN_FILTER,
  CATALOG_PLATFORM_FILTER,
  fetchCatalogSettings,
  installAllCatalogPayloads,
  installCatalogPayload,
  updateCatalogSettings,
} from '../../../../actions/catalog/payload-catalog-actions';
import MarketplaceBrowser from '../../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceHero from '../../../../components/common/marketplace/MarketplaceHero';
import MarketplaceLoadMore from '../../../../components/common/marketplace/MarketplaceLoadMore';
import MarketplaceSections from '../../../../components/common/marketplace/MarketplaceSections';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/MarketplaceViewToggle';
import useMarketplaceCatalog from '../../../../components/common/marketplace/useMarketplaceCatalog';
import useMarketplaceView from '../../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import { TO_CLASSIFY } from '../../../../utils/domains/domainUtils';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import notifyPayloadInstalled from './notifyPayloadInstalled';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogDrawer from './ThreatArsenalCatalogDrawer';
import ThreatArsenalContractCatalogCard from './ThreatArsenalContractCatalogCard';

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

interface Props { tabs?: ReactNode }

// Same section as the Active tab: the first domain by name.
const domainOf = ({ payload, contract }: AvailableAction) => (payload
  ? payload.catalog_item_category ?? undefined
  : (contract?.contract_domains ?? []).filter(name => name !== TO_CLASSIFY).sort()[0]);

const byDomain = (a: AvailableAction, b: AvailableAction) => {
  const [first, second] = [domainOf(a), domainOf(b)];
  if (first === second) return 0;
  if (!first) return 1;
  if (!second) return -1;
  return first.localeCompare(second);
};

const ThreatArsenalAvailable = ({ tabs }: Props) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [available, setAvailable] = useState<AvailableAction[]>([]);
  const [autoInstall, setAutoInstall] = useState(false);
  const [openedItem, setOpenedItem] = useState<CatalogItemOutput | null>(null);
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const [installingAll, setInstallingAll] = useState(false);
  const refreshAvailable = () => fetchAvailableActions().then(setAvailable);
  useEffect(() => {
    refreshAvailable();
    fetchCatalogSettings().then(({ data }) => setAutoInstall(!!data.payload_auto_install));
  }, []);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal-catalog-view');
  const versionOf = (externalId: string) => available.find(item => item.payload?.catalog_item_external_id === externalId)?.payload?.catalog_item_version ?? '';
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
    markInstalled,
    install,
    installAll,
  } = useMarketplaceCatalog<AvailableAction>({
    storageKey: 'threat-arsenal-catalog',
    defaultSorts: [{
      property: 'catalog_item_published_at',
      direction: 'DESC',
    }],
    search: searchAvailableActions,
    fetchFacets: fetchAvailableActionFacets,
    installOne: id => installCatalogPayload(id, versionOf(id)).finally(refreshAvailable),
    installAll: () => installAllCatalogPayloads().finally(refreshAvailable),
  });

  const notifyInstalled = (item: CatalogItemOutput, version: string) => {
    const name = item.catalog_item_name ?? item.catalog_item_external_id;
    notifyPayloadInstalled(String(t('{name} v{version} installed', {
      name,
      version,
    })), name, String(t('Open')));
  };
  const installOne = (id: string, item: CatalogItemOutput) => {
    setInstallingIds(previous => new Set([...previous, id]));
    install(id)
      .then(() => notifyInstalled(item, item.catalog_item_version))
      .finally(() => setInstallingIds(previous => new Set([...previous].filter(other => other !== id))));
  };
  const installEverything = () => {
    setInstallingAll(true);
    installAll()
      .then(ids => MESSAGING$.notifySuccess(String(t('{count} payloads installed', { count: ids.length }))))
      .finally(() => setInstallingAll(false));
  };

  return (
    <MarketplaceBrowser
      tabs={tabs}
      hero={(
        <MarketplaceHero
          title={t('Threat Arsenal')}
          subtitle={t('Browse, install and version the actions your simulations run.')}
          rightSlot={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 16,
            }}
            >
              {canChangeSettings && (
                <Switch
                  label={t('Install new actions automatically')}
                  checked={autoInstall}
                  onCheckedChange={(checked: boolean) => updateCatalogSettings({ payload_auto_install: checked })
                    .then(({ data }) => setAutoInstall(!!data.payload_auto_install))}
                />
              )}
              {canInstall && <Button onClick={installEverything} loading={installingAll}>{t('Install all')}</Button>}
            </div>
          )}
        />
      )}
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
          resultsCount
          infiniteScroll
          fetch={fetchItems}
          searchPaginationInput={searchPaginationInput}
          setContent={setItems}
          propertySchemas={availableActionFilterSchemas(facetCounts)}
          queryableHelpers={queryableHelpers}
          filtersEndSlot={(
            <MarketplaceSortSelect
              sortHelpers={queryableHelpers.sortHelpers}
              options={CATALOG_SORT_OPTIONS}
              descendingFields={['catalog_item_published_at']}
            />
          )}
          topBarButtons={<MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />}
        />
      )}
    >
      <MarketplaceSections
        items={[...items].sort(byDomain)}
        sectionKey={domainOf}
        sectionLabel={domain => (domain ? t(domain) : t('Other'))}
        sectionIcon={(domain) => {
          const icon = domain ? DOMAIN_ICON_MAP[domain] : undefined;
          return icon ? icon() : <CategoryOutlined />;
        }}
        view={viewMode}
        overlineLabel="Domain"
        loading={loading && searchPaginationInput.page === 0}
        emptyState={<ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} catalog />}
        renderItem={({ id, payload, contract }) => {
          if (contract) {
            return <ThreatArsenalContractCatalogCard key={`contract-${id}`} contract={contract} />;
          }
          return payload && (
            <ThreatArsenalCatalogCard
              key={id}
              item={payload}
              installed={isInstalled(id)}
              installing={installingIds.has(id)}
              canInstall={canInstall}
              onInstall={() => installOne(id, payload)}
              onOpen={() => setOpenedItem(payload)}
            />
          );
        }}
      />
      <MarketplaceLoadMore
        hasMore={items.length < queryableHelpers.paginationHelpers.getTotalElements()}
        loading={loading && searchPaginationInput.page > 0}
        paused={loading && searchPaginationInput.page === 0}
        onLoadMore={() => queryableHelpers.paginationHelpers.handleChangePage(searchPaginationInput.page + 1)}
      />
      <ThreatArsenalCatalogDrawer
        item={openedItem}
        canInstall={canInstall}
        onClose={() => setOpenedItem(null)}
        onInstalled={(version) => {
          if (openedItem) {
            markInstalled([openedItem.catalog_item_external_id]);
            notifyInstalled(openedItem, version);
          }
          refreshAvailable();
        }}
      />
    </MarketplaceBrowser>
  );
};

export default ThreatArsenalAvailable;
