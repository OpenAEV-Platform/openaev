import { RouteOutlined } from '@mui/icons-material';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import {
  fetchCatalogScenarioFacets,
  SCENARIO_AUTHOR_FILTER,
  SCENARIO_CATEGORY_FILTER,
  SCENARIO_PLATFORM_FILTER,
  SCENARIO_SEVERITY_FILTER,
  SCENARIO_TYPE_FILTER,
  searchCatalogScenarios,
} from '../../../../actions/catalog/scenario-catalog-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import MarketplaceBrowser from '../../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceGrid from '../../../../components/common/marketplace/MarketplaceGrid';
import MarketplaceSortSelect from '../../../../components/common/marketplace/MarketplaceSortSelect';
import useMarketplaceCatalog from '../../../../components/common/marketplace/useMarketplaceCatalog';
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
  const navigate = useNavigate();
  const [installing, setInstalling] = useState<CatalogItemOutput | null>(null);
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
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
            canInstall={canInstall}
            onInstall={() => setInstalling(item)}
          />
        ))}
      </MarketplaceGrid>
      {installing && (
        <CatalogScenarioInstallDrawer
          item={installing}
          version={installing.catalog_item_version}
          onClose={() => setInstalling(null)}
          onInstalled={scenarioId => navigate(`/admin/scenarios/${scenarioId}`)}
        />
      )}
    </MarketplaceBrowser>
  );
};

export default ScenariosAvailable;
