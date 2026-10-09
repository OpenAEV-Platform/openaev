import { RouteOutlined } from '@mui/icons-material';

import {
  fetchCatalogScenarioFacets,
  installCatalogScenario,
  SCENARIO_AUTHOR_FILTER,
  SCENARIO_CATEGORY_FILTER,
  SCENARIO_PLATFORM_FILTER,
  SCENARIO_SEVERITY_FILTER,
  SCENARIO_TYPE_FILTER,
  searchCatalogScenarios,
} from '../../../../actions/catalog/scenario-catalog-actions';
import { type CatalogScenario } from '../../../../actions/catalog/scenario-catalog-preview';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
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
import { scenarioCategories } from '../constants';
import { SCENARIO_SEVERITIES, severityLabelKey } from '../scenarioSeverity';
import ScenarioCatalogCard from './ScenarioCatalogCard';

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

const ScenariosAvailable = () => {
  const { t } = useFormatter();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const {
    items,
    setItems,
    loading,
    fetchItems,
    queryableHelpers,
    searchPaginationInput,
    facetCounts,
    isInstalled,
    install,
  } = useMarketplaceCatalog<CatalogScenario>({
    storageKey: 'scenarios-catalog',
    defaultSorts: [{
      property: 'catalog_item_published_at',
      direction: 'DESC',
    }],
    search: searchCatalogScenarios,
    fetchFacets: fetchCatalogScenarioFacets,
    installOne: installCatalogScenario,
    // Scenarios are installed one by one, with their targets (F6, F24).
    installAll: () => Promise.resolve([]),
  });

  const facets: MarketplaceFacet[] = [
    {
      filterKey: SCENARIO_SEVERITY_FILTER,
      label: 'Severity',
      values: SCENARIO_SEVERITIES,
      valueLabel: value => t(severityLabelKey(value)),
    },
    {
      filterKey: SCENARIO_CATEGORY_FILTER,
      label: 'Category',
      valueLabel: value => t(scenarioCategories.get(value) ?? value),
    },
    {
      filterKey: SCENARIO_TYPE_FILTER,
      label: 'Type',
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
      hero={<MarketplacePreviewNotice />}
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
          fetch={fetchItems}
          searchPaginationInput={searchPaginationInput}
          setContent={setItems}
          disableFilters
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
        loading={loading}
        isEmpty={items.length === 0}
        emptyState={<EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />}
      >
        {items.map(item => (
          <ScenarioCatalogCard
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

export default ScenariosAvailable;
