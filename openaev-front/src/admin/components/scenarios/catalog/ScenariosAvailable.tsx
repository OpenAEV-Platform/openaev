import { Button, Switch } from '@filigran/design-system';
import { GetAppOutlined, LibraryBooksOutlined, RouteOutlined, UpdateOutlined } from '@mui/icons-material';
import { type ReactNode, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchCatalogSettings, updateCatalogSettings } from '../../../../actions/catalog/payload-catalog-actions';
import {
  fetchCatalogScenarioFacets,
  fetchCatalogScenarios,
  installAllCatalogScenarios,
  SCENARIO_AUTHOR_FILTER,
  SCENARIO_INSTALLATION_FILTER,
  SCENARIO_INSTALLED,
  SCENARIO_NOT_INSTALLED,
  SCENARIO_PLATFORM_FILTER,
  SCENARIO_TYPE_FILTER,
  scenarioCatalogFilterSchemas,
  searchCatalogScenarios,
} from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import MarketplaceBrowser from '../../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceGrid from '../../../../components/common/marketplace/MarketplaceGrid';
import MarketplaceHero from '../../../../components/common/marketplace/MarketplaceHero';
import MarketplaceLoadMore from '../../../../components/common/marketplace/MarketplaceLoadMore';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/MarketplaceViewToggle';
import useMarketplaceCatalog from '../../../../components/common/marketplace/useMarketplaceCatalog';
import useMarketplaceView from '../../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SCENARIO_TYPE_LABELS } from '../constants';
import useScenarioLaunch from '../useScenarioLaunch';
import CatalogScenarioInstallDrawer from './CatalogScenarioInstallDrawer';
import ScenarioCatalogCard from './ScenarioCatalogCard';
import ScenarioCatalogDrawer from './ScenarioCatalogDrawer';

const SORT_OPTIONS = [
  {
    field: 'catalog_item_name',
    label: 'Name',
  },
  {
    field: 'catalog_item_published_at',
    label: 'Published',
  },
];

interface Props { tabs?: ReactNode }

const ScenariosAvailable = ({ tabs }: Props) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const [installing, setInstalling] = useState<CatalogItemOutput | null>(null);
  const [opened, setOpened] = useState<CatalogItemOutput | null>(null);
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [autoInstall, setAutoInstall] = useState(false);
  useEffect(() => {
    fetchCatalogSettings().then(({ data }) => setAutoInstall(!!data.scenario_auto_install));
  }, []);
  const { launch, targetsDrawer } = useScenarioLaunch();
  const [launchAfterInstall, setLaunchAfterInstall] = useState(false);
  const [installingAll, setInstallingAll] = useState(false);
  const install = (item: CatalogItemOutput, thenLaunch: boolean) => {
    setLaunchAfterInstall(thenLaunch);
    setInstalling(item);
  };
  const { viewMode, handleViewModeChange } = useMarketplaceView('scenarios-catalog-view');
  const {
    items,
    setItems,
    loading,
    fetchItems,
    queryableHelpers,
    searchPaginationInput,
    facetCounts,
  } = useMarketplaceCatalog<CatalogItemOutput>({
    storageKey: 'scenarios-catalog',
    defaultSorts: [{
      property: 'catalog_item_published_at',
      direction: 'DESC',
    }],
    search: searchCatalogScenarios,
    fetchFacets: fetchCatalogScenarioFacets,
  });

  const installEverything = () => {
    setInstallingAll(true);
    installAllCatalogScenarios()
      .then(({ data }) => {
        const installed = String(t('{count} scenarios installed', { count: data.installed.length }));
        MESSAGING$.notifySuccess(data.skipped.length > 0
          ? `${installed}. ${t('{count} to install one by one', { count: data.skipped.length })}`
          : installed);
        return fetchItems({
          ...searchPaginationInput,
          page: 0,
        }).then(({ data: page }) => setItems(page.content));
      })
      .finally(() => setInstallingAll(false));
  };
  // Header stats of the Available tab (F5): catalogue total, not installed, updates available.
  const [catalogStats, setCatalogStats] = useState({
    total: 0,
    notInstalled: 0,
    updates: 0,
  });
  useEffect(() => {
    fetchCatalogScenarios().then((catalog) => {
      const installedOnes = catalog.filter(item => (item.catalog_item_installed_scenarios ?? []).length > 0);
      setCatalogStats({
        total: catalog.length,
        notInstalled: catalog.length - installedOnes.length,
        updates: installedOnes.filter(item => !(item.catalog_item_installed_versions ?? []).includes(item.catalog_item_version)).length,
      });
    });
  }, [items]);

  const facets: MarketplaceFacet[] = [
    {
      filterKey: SCENARIO_INSTALLATION_FILTER,
      label: 'Installation',
      values: [SCENARIO_INSTALLED, SCENARIO_NOT_INSTALLED],
      valueLabel: value => t(value === SCENARIO_INSTALLED ? 'Installed' : 'Not installed'),
    },
    {
      filterKey: SCENARIO_TYPE_FILTER,
      label: 'Type',
      valueLabel: value => t(SCENARIO_TYPE_LABELS[value] ?? value),
    },
    {
      filterKey: SCENARIO_PLATFORM_FILTER,
      label: 'Platform',
      values: ['Windows', 'Linux', 'MacOS'],
      icon: platform => <PlatformIcon platform={platform} width={16} />,
    },
    {
      filterKey: SCENARIO_AUTHOR_FILTER,
      label: 'Author',
    },
  ];

  return (
    <MarketplaceBrowser
      tabs={tabs}
      hero={(
        <MarketplaceHero
          title={t('Scenarios')}
          subtitle={t('Browse, install and version ready-to-run scenarios.')}
          rightSlot={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 16,
            }}
            >
              {canChangeSettings && (
                <Switch
                  label={t('Install new scenarios automatically')}
                  checked={autoInstall}
                  onCheckedChange={(checked: boolean) => updateCatalogSettings({ scenario_auto_install: checked })
                    .then(({ data }) => setAutoInstall(!!data.scenario_auto_install))}
                />
              )}
              {canInstall && <Button onClick={installEverything} loading={installingAll}>{t('Install all')}</Button>}
            </div>
          )}
          stats={[
            {
              id: 'catalog-total',
              label: t('In the catalogue'),
              value: catalogStats.total,
              icon: LibraryBooksOutlined,
            },
            {
              id: 'not-installed',
              label: t('Not installed'),
              value: catalogStats.notInstalled,
              icon: GetAppOutlined,
            },
            {
              id: 'updates',
              label: t('Updates available'),
              value: catalogStats.updates,
              icon: UpdateOutlined,
            },
          ]}
        />
      )}
      sidebar={(
        <MarketplaceFacetSidebar
          facets={facets}
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
          propertySchemas={scenarioCatalogFilterSchemas(facetCounts)}
          topBarButtons={<MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />}
          queryableHelpers={queryableHelpers}
          filtersEndSlot={(
            <MarketplaceSortSelect
              sortHelpers={queryableHelpers.sortHelpers}
              options={SORT_OPTIONS}
              descendingFields={['catalog_item_published_at']}
            />
          )}
        />
      )}
    >
      <MarketplaceGrid
        view={viewMode}
        loading={loading && searchPaginationInput.page === 0}
        isEmpty={items.length === 0}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
      >
        {items.map(item => (
          <ScenarioCatalogCard
            key={item.catalog_item_external_id}
            item={item}
            canInstall={canInstall}
            canLaunch={canLaunch}
            onOpen={() => setOpened(item)}
            onInstall={() => install(item, false)}
            onLaunch={() => {
              const installed = (item.catalog_item_installed_scenarios ?? [])[0];
              if (installed) {
                launch(installed.scenario_id, item.catalog_item_name ?? item.catalog_item_external_id);
              } else {
                install(item, true);
              }
            }}
          />
        ))}
      </MarketplaceGrid>
      <MarketplaceLoadMore
        hasMore={items.length < queryableHelpers.paginationHelpers.getTotalElements()}
        loading={loading && searchPaginationInput.page > 0}
        paused={loading && searchPaginationInput.page === 0}
        onLoadMore={() => queryableHelpers.paginationHelpers.handleChangePage(searchPaginationInput.page + 1)}
      />
      <ScenarioCatalogDrawer
        item={opened}
        canInstall={canInstall}
        onClose={() => setOpened(null)}
        onInstall={(item) => {
          setOpened(null);
          install(item, false);
        }}
      />
      {installing && (
        <CatalogScenarioInstallDrawer
          externalId={installing.catalog_item_external_id}
          name={installing.catalog_item_name ?? installing.catalog_item_external_id}
          version={installing.catalog_item_version}
          thenLaunch={launchAfterInstall}
          chained={installing.catalog_item_scenario_type === 'chained'}
          onClose={() => setInstalling(null)}
          onInstalled={(scenarioId) => {
            if (launchAfterInstall) {
              setInstalling(null);
              launch(scenarioId, installing.catalog_item_name ?? installing.catalog_item_external_id);
            } else {
              navigate(`/admin/scenarios/${scenarioId}`);
            }
          }}
        />
      )}
      {targetsDrawer}
    </MarketplaceBrowser>
  );
};

export default ScenariosAvailable;
