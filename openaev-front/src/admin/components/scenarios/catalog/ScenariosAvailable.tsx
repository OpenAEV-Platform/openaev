import { Button } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchCatalogScenarioFacetCounts, installAllCatalogScenarios, searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import {
  CATALOG_ITEM_DEFAULT_SORT,
  CATALOG_ITEM_DESCRIPTION,
  CATALOG_ITEM_NAME,
  CATALOG_ITEM_PLATFORM_FACET,
  CATALOG_ITEM_PLATFORMS,
  CATALOG_ITEM_PUBLISHED,
  CATALOG_ITEM_SORT_OPTIONS,
} from '../../../../components/common/marketplace/filter/catalogItemSearch';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceSearch from '../../../../components/common/marketplace/filter/useMarketplaceSearch';
import MarketplaceInstallActions from '../../../../components/common/marketplace/install/MarketplaceInstallActions';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import MarketplaceList, { type MarketplaceColumn } from '../../../../components/common/marketplace/results/MarketplaceList';
import MarketplaceLoadMore from '../../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceSortSelect from '../../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import useMarketplaceView from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import { SCENARIO_BASE_URL } from '../../../../constants/BaseUrls';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { scenarioCategoryLabel } from '../constants';
import ScenariosHero from '../page/ScenariosHero';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import CatalogScenarioAction from './CatalogScenarioAction';
import { CATALOG_SCENARIO_INSTALLATION, installationLabel, INSTALLED, NOT_INSTALLED, scenarioUrl, UPDATE_AVAILABLE } from './catalogScenarioInstallation';
import CatalogScenarioInstallDrawer from './CatalogScenarioInstallDrawer';
import { CATALOG_SCENARIO_TYPES, catalogScenarioType, scenarioTypeOf } from './catalogScenarioType';
import ScenarioCatalogCard from './ScenarioCatalogCard';
import ScenarioCatalogDrawer from './ScenarioCatalogDrawer';

const TYPE = 'catalog_item_scenario_type';
const CATEGORY = 'catalog_item_category';
const AUTHOR = 'catalog_item_author';

const FACETS: MarketplaceFacet[] = [
  {
    key: CATALOG_SCENARIO_INSTALLATION,
    label: 'Installation',
    values: [INSTALLED, NOT_INSTALLED, UPDATE_AVAILABLE],
    valueLabel: installationLabel,
  },
  {
    key: TYPE,
    label: 'Type',
    values: CATALOG_SCENARIO_TYPES,
    valueLabel: scenarioTypeOf,
    icon: type => <ScenarioTypeIcon type={scenarioTypeOf(type)} />,
  },
  CATALOG_ITEM_PLATFORM_FACET,
  {
    key: CATEGORY,
    label: 'Category',
    valueLabel: scenarioCategoryLabel,
  },
  {
    key: AUTHOR,
    label: 'Author',
  },
];

// The catalogue scenarios, installed or not.
const ScenariosAvailable = () => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const notifyInstalled = useNotifyInstalled();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const { viewMode, handleViewModeChange } = useMarketplaceView('scenarios-catalog:view-mode');
  const [opened, setOpened] = useState<CatalogScenarioOutput | null>(null);
  const [installing, setInstalling] = useState<CatalogScenarioOutput | null>(null);
  const catalog = useMarketplaceSearch<CatalogScenarioOutput>({
    storageKey: 'scenarios-catalog',
    defaultSort: CATALOG_ITEM_DEFAULT_SORT,
    search: searchCatalogScenarios,
    fetchFacetCounts: fetchCatalogScenarioFacetCounts,
  });
  const { queryableHelpers, searchPaginationInput } = catalog;

  const install = (scenario: CatalogScenarioOutput) => {
    setOpened(null);
    setInstalling(scenario);
  };
  const markInstalled = (installed: CatalogScenarioOutput, scenarioId: string) => {
    setInstalling(null);
    notifyInstalled(installed.catalog_scenario_name ?? installed.catalog_scenario_external_id, installed.catalog_scenario_version, scenarioUrl(scenarioId));
    catalog.reload();
  };

  const columns: MarketplaceColumn<CatalogScenarioOutput>[] = [
    {
      field: CATALOG_ITEM_NAME,
      label: 'Name',
      sortable: true,
      width: '25%',
      value: scenario => scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id,
    },
    {
      field: TYPE,
      label: 'Type',
      width: '10%',
      value: scenario => t(catalogScenarioType(scenario)),
    },
    {
      field: CATEGORY,
      label: 'Category',
      width: '15%',
      value: scenario => (scenario.catalog_scenario_category ? t(scenarioCategoryLabel(scenario.catalog_scenario_category)) : '–'),
    },
    {
      field: CATALOG_ITEM_DESCRIPTION,
      label: 'Description',
      width: '25%',
      value: scenario => scenario.catalog_scenario_description || '–',
    },
    {
      field: CATALOG_ITEM_PLATFORMS,
      label: 'Platforms',
      width: '10%',
      value: scenario => <MarketplaceCardMeta platforms={scenario.catalog_scenario_platforms} />,
    },
    {
      field: CATALOG_ITEM_PUBLISHED,
      label: 'Published',
      sortable: true,
      width: '15%',
      value: scenario => (scenario.catalog_scenario_published_at ? nsd(scenario.catalog_scenario_published_at) : '–'),
    },
  ];
  const emptyState = (
    <EmptyPlaceholder
      icon={<RouteOutlined />}
      title={catalog.filtered ? t('No scenario matches these filters.') : t('No catalog scenario yet')}
      message={catalog.filtered ? undefined : t('New Filigran scenarios show here as soon as they are published.')}
      action={catalog.filtered && (
        <Button type="button" priority="secondary" onClick={catalog.resetFilters}>{t('Reset filters')}</Button>
      )}
    />
  );

  return (
    <>
      <MarketplaceBrowser
        hero={<ScenariosHero />}
        tabs={<MarketplaceCatalogTabs active="available" baseUrl={SCENARIO_BASE_URL} />}
        sidebar={(
          <MarketplaceFacetSidebar
            facets={FACETS}
            facetCounts={catalog.facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <PaginationComponentV2
            {...catalog.pagination}
            entityPrefix="catalog_scenario"
            availableFilterNames={[TYPE, CATALOG_ITEM_PLATFORMS, CATEGORY, AUTHOR]}
            filtersEndSlot={viewMode === 'grid'
              ? <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={CATALOG_ITEM_SORT_OPTIONS} descendingFields={[CATALOG_ITEM_PUBLISHED]} />
              : null}
            topBarButtons={(
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
              }}
              >
                <MarketplaceInstallActions
                  autoInstallSetting="scenario_auto_install"
                  autoInstallHelp={t('Installs each new Filigran scenario as soon as it is published.')}
                  canInstall={canInstall}
                  installAll={installAllCatalogScenarios}
                  installedMessage={count => t('{count} scenarios installed', { count })}
                  onInstalledAll={catalog.reload}
                />
                <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
              </div>
            )}
          />
        )}
      >
        {viewMode === 'grid'
          ? (
              <MarketplaceSections
                items={catalog.items}
                loading={catalog.loadingFirstPage}
                emptyState={emptyState}
                renderItem={scenario => (
                  <ScenarioCatalogCard
                    key={scenario.catalog_scenario_external_id}
                    scenario={scenario}
                    canInstall={canInstall}
                    onInstall={() => install(scenario)}
                    onOpen={() => setOpened(scenario)}
                  />
                )}
              />
            )
          : (
              <MarketplaceList
                items={catalog.items}
                loading={catalog.loadingFirstPage}
                emptyState={emptyState}
                columns={columns}
                sortHelpers={queryableHelpers.sortHelpers}
                rowKey={scenario => scenario.catalog_scenario_external_id}
                icon={scenario => <ScenarioTypeIcon type={catalogScenarioType(scenario)} />}
                open={scenario => ({ onClick: () => setOpened(scenario) })}
                action={scenario => <CatalogScenarioAction scenario={scenario} canInstall={canInstall} onInstall={() => install(scenario)} size="sm" />}
              />
            )}
        <MarketplaceLoadMore {...catalog.loadMore} />
      </MarketplaceBrowser>
      <ScenarioCatalogDrawer
        scenario={opened}
        canInstall={canInstall}
        onClose={() => setOpened(null)}
        onInstall={install}
      />
      {installing && (
        <CatalogScenarioInstallDrawer
          scenario={installing}
          onClose={() => setInstalling(null)}
          onInstalled={markInstalled}
        />
      )}
    </>
  );
};

export default ScenariosAvailable;
