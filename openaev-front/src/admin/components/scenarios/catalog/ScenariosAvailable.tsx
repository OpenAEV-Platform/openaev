import { Button } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useEffect, useState } from 'react';

import { fetchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceResultsCount from '../../../../components/common/marketplace/toolbar/MarketplaceResultsCount';
import MarketplaceSortSelect from '../../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ScenariosHero from '../page/ScenariosHero';
import ScenariosTabs from '../page/ScenariosTabs';
import {
  type CatalogScenarioFilters,
  type CatalogScenarioSort,
  type FacetKey,
  filterScenarios,
  hasFilters,
  NO_FILTERS,
  type SortField,
} from './catalogScenarioFilters';
import CatalogScenarioInstallDrawer from './CatalogScenarioInstallDrawer';
import ScenarioCatalogCard from './ScenarioCatalogCard';
import ScenarioCatalogDrawer from './ScenarioCatalogDrawer';
import ScenariosCatalogSidebar from './ScenariosCatalogSidebar';

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

// The catalogue scenarios the tenant has not installed, filtered and sorted in memory: the catalogue is small.
const ScenariosAvailable = () => {
  const { t } = useFormatter();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const [scenarios, setScenarios] = useState<CatalogScenarioOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<CatalogScenarioFilters>(NO_FILTERS);
  const [sort, setSort] = useState<CatalogScenarioSort>({
    field: 'catalog_scenario_name',
    asc: true,
  });
  const [opened, setOpened] = useState<CatalogScenarioOutput | null>(null);
  const [installing, setInstalling] = useState<CatalogScenarioOutput | null>(null);

  useEffect(() => {
    fetchCatalogScenarios()
      .then(setScenarios)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const sortHelpers: SortHelpers = {
    getSortBy: () => sort.field,
    getSortAsc: () => sort.asc,
    handleDirectedSort: (field, asc) => setSort({
      field: field as SortField,
      asc,
    }),
    handleSort: field => setSort({
      field: field as SortField,
      asc: field === sort.field ? !sort.asc : true,
    }),
  };
  const toggleFacet = (key: FacetKey, value: string) => setFilters(previous => ({
    ...previous,
    [key]: previous[key].includes(value) ? previous[key].filter(v => v !== value) : [...previous[key], value],
  }));
  const shown = filterScenarios(scenarios, filters, sort);
  const filtered = hasFilters(filters);

  const install = (scenario: CatalogScenarioOutput) => {
    setOpened(null);
    setInstalling(scenario);
  };
  // An installed scenario leaves this tab for the Active one.
  const markInstalled = (installed: CatalogScenarioOutput) => {
    setScenarios(previous => previous.filter(scenario => scenario.catalog_scenario_external_id !== installed.catalog_scenario_external_id));
    setInstalling(null);
    MESSAGING$.notifySuccess(t('{name} v{version} installed', {
      name: installed.catalog_scenario_name ?? installed.catalog_scenario_external_id,
      version: installed.catalog_scenario_version,
    }));
  };

  return (
    <>
      <MarketplaceBrowser
        hero={<ScenariosHero />}
        tabs={<ScenariosTabs active="available" />}
        sidebar={<ScenariosCatalogSidebar scenarios={scenarios} filters={filters} onToggle={toggleFacet} />}
        toolbar={(
          <div style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
          }}
          >
            <SearchFilter
              variant="small"
              keyword={filters.keyword}
              onChange={keyword => setFilters(previous => ({
                ...previous,
                keyword: keyword ?? '',
              }))}
            />
            <MarketplaceSortSelect sortHelpers={sortHelpers} options={SORT_OPTIONS} descendingFields={['catalog_scenario_published_at']} />
            <div style={{ marginLeft: 'auto' }}>
              <MarketplaceResultsCount count={shown.length} />
            </div>
          </div>
        )}
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
                <Button type="button" priority="secondary" onClick={() => setFilters(NO_FILTERS)}>{t('Reset filters')}</Button>
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
