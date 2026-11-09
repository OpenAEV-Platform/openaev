import { type ReactNode, useEffect, useState } from 'react';

import { fetchCatalogScenarios } from '../../../actions/catalog/scenario-catalog-actions';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../components/common/marketplace/MarketplaceFacetSidebar';
import MarketplaceLoadMore from '../../../components/common/marketplace/MarketplaceLoadMore';
import MarketplaceSortSelect from '../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import useAuthorFacetSection from '../../../components/common/marketplace/useAuthorFacetSection';
import useMarketplaceView from '../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type CatalogItemOutput, type Domain } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED } from './scenario/ScenarioType';
import ScenarioCardGrid from './ScenarioCardGrid';
import { domainIcon, facetDomains } from './scenarioDomains';
import ScenariosActiveList from './ScenariosActiveList';
import ScenariosHeaderActions from './ScenariosHeaderActions';
import ScenariosHero from './ScenariosHero';
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

interface Props { tabs?: ReactNode }

const ScenariosActive = ({ tabs }: Props) => {
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

  const domains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const domainName = (id: string) => domains.find(domain => domain.domain_id === id)?.domain_name ?? id;

  const facets: MarketplaceFacet[] = [
    {
      filterKey: 'scenario_domains',
      label: 'Domains',
      values: facetDomains(domains).map(domain => domain.domain_id),
      valueLabel: id => t(domainName(id)),
      icon: id => domainIcon(domainName(id)),
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
    loading: active.loading && active.searchPaginationInput.page === 0,
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
      tabs={tabs}
      hero={<ScenariosHero />}
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
          resultsCount
          infiniteScroll
          // Export and import act on this list, so they sit with it, not in the Hero shared by both tabs.
          topBarButtons={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
            >
              <ScenariosHeaderActions
                totalElements={active.totalElements}
                scenarios={active.scenarios}
                onImported={active.reload}
              />
              <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
            </div>
          )}
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
        ? <ScenarioCardGrid scenarios={items.scenarios} loading={items.loading} onDelete={items.onDelete} catalogItems={catalogItems} />
        : (
            <ScenariosActiveList
              {...items}
              sortHelpers={active.queryableHelpers.sortHelpers}
              selectAll={selection.selectAll}
              onToggleSelectAll={selection.handleToggleSelectAll}
            />
          )}
      <MarketplaceLoadMore
        hasMore={active.scenarios.length < active.totalElements}
        loading={active.loading && active.searchPaginationInput.page > 0}
        paused={active.loading && active.searchPaginationInput.page === 0}
        onLoadMore={() => active.queryableHelpers.paginationHelpers.handleChangePage(active.searchPaginationInput.page + 1)}
      />
    </MarketplaceBrowser>
  );
};

export default ScenariosActive;
