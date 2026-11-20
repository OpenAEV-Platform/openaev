import { Button, Switch } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { type ReactNode, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchCatalogSettings, updateCatalogSettings } from '../../../../actions/catalog/payload-catalog-actions';
import {
  fetchCatalogScenarioFacets,
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
import ScenariosHero from '../ScenariosHero';
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
  const { launch, launchDrawer } = useScenarioLaunch();
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
      hero={<ScenariosHero />}
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
          // The auto-install switch and Install all act on this catalogue: they sit with it, not in the Hero.
          topBarButtons={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
            >
              {canChangeSettings && (
                <Switch
                  label={t('Auto-install')}
                  checked={autoInstall}
                  onCheckedChange={(checked: boolean) => updateCatalogSettings({ scenario_auto_install: checked })
                    .then(({ data }) => setAutoInstall(!!data.scenario_auto_install))}
                />
              )}
              {canInstall && <Button priority="secondary" onClick={installEverything} loading={installingAll}>{t('Install all')}</Button>}
              <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
            </div>
          )}
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
                launch({
                  scenario_id: installed.scenario_id,
                  scenario_name: item.catalog_item_name ?? item.catalog_item_external_id,
                  scenario_catalog_id: item.catalog_item_external_id,
                  scenario_version: installed.version,
                });
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
              launch({
                scenario_id: scenarioId,
                scenario_name: installing.catalog_item_name ?? installing.catalog_item_external_id,
                scenario_catalog_id: installing.catalog_item_external_id,
                scenario_version: installing.catalog_item_version,
              });
            } else {
              navigate(`/admin/scenarios/${scenarioId}`);
            }
          }}
        />
      )}
      {launchDrawer}
    </MarketplaceBrowser>
  );
};

export default ScenariosAvailable;
