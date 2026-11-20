import { Button } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useEffect, useState } from 'react';

import { fetchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import { FACET_PLATFORMS, platformFacetIcon } from '../../../../components/common/facets/facetPlatforms';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceFilters from '../../../../components/common/marketplace/filter/useMarketplaceFilters';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceToolbar from '../../../../components/common/marketplace/toolbar/MarketplaceToolbar';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { scenarioCategories } from '../constants';
import ScenariosHero from '../page/ScenariosHero';
import { SCENARIO_TYPES, ScenarioTypeIcon, type ScenarioTypeValue } from '../scenario/ScenarioType';
import { catalogScenarioFilter, catalogScenarioSorts } from './catalogScenarioFilters';
import CatalogScenarioInstallDrawer from './CatalogScenarioInstallDrawer';
import ScenarioCatalogCard from './ScenarioCatalogCard';
import ScenarioCatalogDrawer from './ScenarioCatalogDrawer';

const SORT_OPTIONS = [
  {
    field: 'catalog_scenario_name',
    label: 'Name',
  },
  {
    field: 'catalog_scenario_published_at',
    label: 'Published',
  },
];

const FACETS: MarketplaceFacet<'types' | 'platforms' | 'categories'>[] = [
  {
    key: 'types',
    label: 'Type',
    values: SCENARIO_TYPES,
    icon: type => () => <ScenarioTypeIcon type={type as ScenarioTypeValue} />,
  },
  {
    key: 'platforms',
    label: 'Platform',
    values: FACET_PLATFORMS,
    icon: platformFacetIcon,
  },
  {
    key: 'categories',
    label: 'Category',
    valueLabel: category => scenarioCategories.get(category) ?? category,
  },
];

// The catalogue scenarios the tenant has not installed.
const ScenariosAvailable = () => {
  const { t } = useFormatter();
  const notifyInstalled = useNotifyInstalled();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const [scenarios, setScenarios] = useState<CatalogScenarioOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [opened, setOpened] = useState<CatalogScenarioOutput | null>(null);
  const [installing, setInstalling] = useState<CatalogScenarioOutput | null>(null);
  const { shown, filtered, resetFilters, facetState, toolbar } = useMarketplaceFilters({
    items: scenarios,
    facetFilter: catalogScenarioFilter,
    sortValues: catalogScenarioSorts,
    defaultSort: 'catalog_scenario_name',
  });

  useEffect(() => {
    fetchCatalogScenarios()
      .then(setScenarios)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const install = (scenario: CatalogScenarioOutput) => {
    setOpened(null);
    setInstalling(scenario);
  };
  // An installed scenario leaves this tab for the Active one.
  const markInstalled = (installed: CatalogScenarioOutput) => {
    setScenarios(previous => previous.filter(scenario => scenario.catalog_scenario_external_id !== installed.catalog_scenario_external_id));
    setInstalling(null);
    notifyInstalled(installed.catalog_scenario_name ?? installed.catalog_scenario_external_id, installed.catalog_scenario_version);
  };

  return (
    <>
      <MarketplaceBrowser
        hero={<ScenariosHero />}
        tabs={<MarketplaceCatalogTabs active="available" baseUrl="/admin/scenarios" />}
        sidebar={<MarketplaceFacetSidebar facets={FACETS} {...facetState} />}
        toolbar={<MarketplaceToolbar {...toolbar} sortOptions={SORT_OPTIONS} descendingFields={['catalog_scenario_published_at']} />}
      >
        <MarketplaceSections
          items={shown}
          loading={loading}
          emptyState={(
            <EmptyPlaceholder
              icon={<RouteOutlined />}
              title={filtered ? t('No scenario matches these filters.') : t('Every catalog scenario is installed')}
              message={filtered ? undefined : t('New Filigran scenarios show here as soon as they are published.')}
              action={filtered && (
                <Button type="button" priority="secondary" onClick={resetFilters}>{t('Reset filters')}</Button>
              )}
            />
          )}
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
