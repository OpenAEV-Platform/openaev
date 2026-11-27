import { Button } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchCatalogScenarioFacetCounts, installAllCatalogScenarios, searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import { fetchScenarioVersions } from '../../../../actions/scenarios/scenario-update-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import {
  CATALOG_ITEM_DEFAULT_SORT,
  CATALOG_ITEM_DESCRIPTION,
  CATALOG_ITEM_NAME,
  CATALOG_ITEM_PLATFORM_FACET,
  CATALOG_ITEM_PLATFORMS,
  CATALOG_ITEM_PUBLISHED,
} from '../../../../components/common/marketplace/filter/catalogItemSearch';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceSearch from '../../../../components/common/marketplace/filter/useMarketplaceSearch';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import MarketplaceList, { type MarketplaceColumn } from '../../../../components/common/marketplace/results/MarketplaceList';
import MarketplaceLoadMore from '../../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceCatalogToolbar from '../../../../components/common/marketplace/toolbar/MarketplaceCatalogToolbar';
import useMarketplaceView from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import { useFormatter } from '../../../../components/i18n';
import { SCENARIO_BASE_URL } from '../../../../constants/BaseUrls';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useScenarioCatalogStates from '../card/useScenarioCatalogStates';
import { scenarioCategoryLabel } from '../constants';
import useScenarioLaunch from '../launch/useScenarioLaunch';
import ScenariosHero from '../page/ScenariosHero';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import ScenarioUpdateDialog from '../scenario/versions/ScenarioUpdateDialog';
import { type ScenarioUpdateTarget, updateTarget } from '../scenario/versions/scenarioUpdateTargets';
import CatalogScenarioAction from './CatalogScenarioAction';
import { CATALOG_SCENARIO_INSTALLATION, installationLabel, INSTALLED, NOT_INSTALLED, SCENARIOS_CATALOG_KEY, scenarioUrl, UPDATE_AVAILABLE } from './catalogScenarioInstallation';
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
  const notifyInstalled = useNotifyInstalled();
  const navigate = useNavigate();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);
  const { launch, launchDrawer } = useScenarioLaunch();
  const view = useMarketplaceView('scenarios-catalog:view-mode');
  const [opened, setOpened] = useState<CatalogScenarioOutput | null>(null);
  const [installing, setInstalling] = useState<{
    scenario: CatalogScenarioOutput;
    thenLaunch: boolean;
  } | null>(null);
  const catalog = useMarketplaceSearch<CatalogScenarioOutput>({
    storageKey: SCENARIOS_CATALOG_KEY,
    defaultSort: CATALOG_ITEM_DEFAULT_SORT,
    search: searchCatalogScenarios,
    fetchFacetCounts: fetchCatalogScenarioFacetCounts,
  });
  const { queryableHelpers, searchPaginationInput } = catalog;
  const catalogStates = useScenarioCatalogStates(catalog.items.flatMap(item => item.catalog_scenario_installed_scenario_id ?? []));
  const [updating, setUpdating] = useState<ScenarioUpdateTarget | null>(null);
  const updateOf = (scenario: CatalogScenarioOutput) => {
    const installedId = scenario.catalog_scenario_installed_scenario_id;
    if (!canInstall || !installedId || !catalogStates[installedId]?.scenario_catalog_state_newer_version) return undefined;
    // The dialog names the version installed now: read from the scenario's versions.
    return () => fetchScenarioVersions(installedId)
      .then((versions) => {
        const installed = versions.scenario_versions.find(version => version.scenario_version_current)?.scenario_version ?? '';
        setOpened(null);
        setUpdating(updateTarget(installedId, scenario.catalog_scenario_version, installed, t));
      })
      .catch(() => undefined);
  };
  const updated = () => {
    const scenarioId = updating?.scenarioId;
    setUpdating(null);
    catalog.reload();
    if (scenarioId) navigate(scenarioUrl(scenarioId));
  };

  const install = (scenario: CatalogScenarioOutput, thenLaunch = false) => {
    setOpened(null);
    setInstalling({
      scenario,
      thenLaunch,
    });
  };
  // An installed scenario stays listed with its Open link; Install and launch goes on to its launch check.
  const markInstalled = (installed: CatalogScenarioOutput, scenarioId: string) => {
    const name = installed.catalog_scenario_name ?? installed.catalog_scenario_external_id;
    setInstalling(null);
    notifyInstalled(name, installed.catalog_scenario_version, scenarioUrl(scenarioId));
    if (installing?.thenLaunch) {
      catalog.reload();
      launch({
        scenarioId,
        name,
        type: catalogScenarioType(installed),
      });
    } else {
      navigate(scenarioUrl(scenarioId));
    }
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
          <MarketplaceCatalogToolbar
            catalog={catalog}
            view={view}
            entityPrefix="catalog_scenario"
            filterNames={[TYPE, CATALOG_ITEM_PLATFORMS, CATEGORY, AUTHOR]}
            install={{
              autoInstallSetting: 'scenario_auto_install',
              autoInstallHelp: t('Installs each new Filigran scenario as soon as it is published.'),
              canInstall,
              installAll: installAllCatalogScenarios,
              installedMessage: count => t('{count} scenarios installed', { count }),
              onInstalledAll: catalog.reload,
            }}
          />
        )}
      >
        {view.viewMode === 'grid'
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
                    onLaunch={canLaunch ? () => install(scenario, true) : undefined}
                    onOpen={() => setOpened(scenario)}
                    onUpdate={updateOf(scenario)}
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
                action={scenario => (
                  <CatalogScenarioAction
                    scenario={scenario}
                    canInstall={canInstall}
                    onInstall={() => install(scenario)}
                    onLaunch={canLaunch ? () => install(scenario, true) : undefined}
                    onUpdate={updateOf(scenario)}
                    size="sm"
                  />
                )}
              />
            )}
        <MarketplaceLoadMore {...catalog.loadMore} />
      </MarketplaceBrowser>
      {opened && (
        <ScenarioCatalogDrawer
          scenario={opened}
          canInstall={canInstall}
          onClose={() => setOpened(null)}
          onInstall={scenario => install(scenario)}
          onUpdate={updateOf(opened)}
        />
      )}
      {updating && <ScenarioUpdateDialog target={updating} onClose={() => setUpdating(null)} onApplied={updated} />}
      {installing && (
        <CatalogScenarioInstallDrawer
          scenario={installing.scenario}
          thenLaunch={installing.thenLaunch}
          onClose={() => setInstalling(null)}
          onInstalled={markInstalled}
        />
      )}
      {launchDrawer}
    </>
  );
};

export default ScenariosAvailable;
