import { CategoryOutlined, RouteOutlined } from '@mui/icons-material';
import { type ReactNode, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  fetchCatalogScenarioFacets,
  SCENARIO_AUTHOR_FILTER,
  SCENARIO_CATEGORY_FILTER,
  SCENARIO_INSTALLATION_FILTER,
  SCENARIO_INSTALLED,
  SCENARIO_NOT_INSTALLED,
  SCENARIO_PLATFORM_FILTER,
  SCENARIO_SEVERITY_FILTER,
  SCENARIO_TYPE_FILTER,
  scenarioCatalogFilterSchemas,
  searchCatalogScenarios,
} from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
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
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SCENARIO_TYPE_LABELS, scenarioCategories } from '../constants';
import { SCENARIO_SEVERITIES, severityLabelKey } from '../scenarioSeverity';
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
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
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

  const facets: MarketplaceFacet[] = [
    {
      filterKey: SCENARIO_CATEGORY_FILTER,
      label: 'Category',
      valueLabel: value => t(scenarioCategories.get(value) ?? value),
    },
    {
      filterKey: SCENARIO_INSTALLATION_FILTER,
      label: 'Installation',
      values: [SCENARIO_INSTALLED, SCENARIO_NOT_INSTALLED],
      valueLabel: value => t(value === SCENARIO_INSTALLED ? 'Installed' : 'Not installed'),
    },
    {
      filterKey: SCENARIO_SEVERITY_FILTER,
      label: 'Severity',
      values: SCENARIO_SEVERITIES,
      valueLabel: value => t(severityLabelKey(value)),
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
      <MarketplaceSections
        items={items}
        sectionKey={item => item.catalog_item_category}
        sectionLabel={category => (category ? t(scenarioCategories.get(category) ?? category) : t('Other'))}
        sectionIcon={<CategoryOutlined />}
        view={viewMode}
        loading={loading && searchPaginationInput.page === 0}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
        renderItem={item => (
          <ScenarioCatalogCard
            key={item.catalog_item_external_id}
            item={item}
            canInstall={canInstall}
            onOpen={() => setOpened(item)}
            onInstall={() => setInstalling(item)}
          />
        )}
      />
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
          setInstalling(item);
        }}
      />
      {installing && (
        <CatalogScenarioInstallDrawer
          externalId={installing.catalog_item_external_id}
          name={installing.catalog_item_name ?? installing.catalog_item_external_id}
          version={installing.catalog_item_version}
          onClose={() => setInstalling(null)}
          onInstalled={scenarioId => navigate(`/admin/scenarios/${scenarioId}`)}
        />
      )}
    </MarketplaceBrowser>
  );
};

export default ScenariosAvailable;
