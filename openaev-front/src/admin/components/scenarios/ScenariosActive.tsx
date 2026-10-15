import { useEffect, useState } from 'react';

import { fetchCatalogScenarios } from '../../../actions/catalog/scenario-catalog-actions';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import MarketplaceSortSelect from '../../../components/common/marketplace/MarketplaceSortSelect';
import useAuthorFacetSection from '../../../components/common/marketplace/useAuthorFacetSection';
import useMarketplaceView from '../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type CatalogItemOutput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { scenarioCategories } from './constants';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED } from './scenario/ScenarioType';
import ScenarioCardGrid from './ScenarioCardGrid';
import ScenariosActiveList from './ScenariosActiveList';
import { SCENARIO_SEVERITIES, severityLabelKey } from './scenarioSeverity';
import ScenariosHeaderActions from './ScenariosHeaderActions';
import useActiveScenarios, { ACTIVE_SCENARIO_FILTER_NAMES } from './useActiveScenarios';

const SORT_OPTIONS = [
  {
    field: 'scenario_name',
    label: 'Name',
  },
  {
    field: 'scenario_updated_at',
    label: 'Updated',
  },
];

const ScenariosActive = () => {
  const { t } = useFormatter();
  const canManage = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const { viewMode, handleViewModeChange } = useMarketplaceView('scenarios:view-mode');
  const active = useActiveScenarios();
  const { selection } = active;
  const [catalogItems, setCatalogItems] = useState<Map<string, CatalogItemOutput>>(new Map());
  useEffect(() => {
    fetchCatalogScenarios().then(items => setCatalogItems(new Map(items.map(item => [item.catalog_item_external_id, item]))));
  }, [active.scenarios]);
  const authorSection = useAuthorFacetSection('scenario_author', active.authorOptions, active.searchPaginationInput, active.queryableHelpers.filterHelpers);

  const facets: MarketplaceFacet[] = [
    {
      filterKey: 'scenario_severity',
      label: 'Severity',
      values: SCENARIO_SEVERITIES,
      valueLabel: value => t(severityLabelKey(value)),
    },
    {
      filterKey: 'scenario_category',
      label: 'Category',
      values: [...scenarioCategories.keys()],
      valueLabel: value => t(scenarioCategories.get(value) ?? value),
    },
    {
      filterKey: 'scenario_type',
      label: 'Type',
      values: [SCENARIO_TYPE_TIME_BASED, SCENARIO_TYPE_CHAINED],
    },
    {
      filterKey: 'scenario_platforms',
      label: 'Platform',
      values: ['Windows', 'Linux', 'MacOS'],
      icon: platform => <PlatformIcon platform={platform} width={16} />,
    },
  ];

  const items = {
    scenarios: active.scenarios,
    loading: active.loading,
    canManage,
    numberOfSelectedElements: selection.numberOfSelectedElements,
    isSelected: selection.isSelected,
    onToggleEntity: selection.onToggleEntity,
    onClearSelectedElements: selection.handleClearSelectedElements,
    onBulkDelete: active.bulkDelete,
    onDelete: active.removeScenario,
  };

  return (
    <MarketplaceBrowser
      hero={(
        <MarketplaceHero
          title={t('Scenarios')}
          totalLabel={t('total scenarios')}
          totalElements={active.totalElements}
          stats={[
            {
              id: 'catalog',
              label: t('From the catalog'),
              value: [...catalogItems.values()].filter(item => (item.catalog_item_installed_versions ?? []).length > 0).length,
            },
            {
              id: 'updates',
              label: t('Update available'),
              value: [...catalogItems.values()].filter(item => (item.catalog_item_installed_versions ?? []).length > 0
                && !(item.catalog_item_installed_versions ?? []).includes(item.catalog_item_version)).length,
            },
          ]}
          rightSlot={(
            <ScenariosHeaderActions
              viewMode={viewMode}
              onViewModeChange={handleViewModeChange}
              totalElements={active.totalElements}
              scenarios={active.scenarios}
              onImported={active.reload}
            />
          )}
        />
      )}
      sidebar={(
        // No counts until a scenario facet aggregation endpoint exists.
        <MarketplaceFacetSidebar
          facets={facets}
          facetCounts={null}
          searchPaginationInput={active.searchPaginationInput}
          filterHelpers={active.queryableHelpers.filterHelpers}
          extraSections={[authorSection]}
        />
      )}
      toolbar={(
        <PaginationComponentV2
          fetch={active.search}
          reloadContentCount={active.reloadCount}
          searchPaginationInput={active.searchPaginationInput}
          setContent={active.setScenarios}
          entityPrefix="scenario"
          availableFilterNames={ACTIVE_SCENARIO_FILTER_NAMES}
          queryableHelpers={active.queryableHelpers}
          filtersEndSlot={viewMode === 'grid'
            ? <MarketplaceSortSelect sortHelpers={active.queryableHelpers.sortHelpers} options={SORT_OPTIONS} descendingFields={['scenario_updated_at']} />
            : null}
        />
      )}
    >
      {viewMode === 'grid'
        ? <ScenarioCardGrid {...items} catalogItems={catalogItems} />
        : (
            <ScenariosActiveList
              {...items}
              sortHelpers={active.queryableHelpers.sortHelpers}
              selectAll={selection.selectAll}
              onToggleSelectAll={selection.handleToggleSelectAll}
            />
          )}
    </MarketplaceBrowser>
  );
};

export default ScenariosActive;
