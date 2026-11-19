import { RouteOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useState } from 'react';

import { bulkDeleteScenarios, searchScenarios } from '../../../actions/scenarios/scenario-actions';
import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import MarketplaceBrowser from '../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceLoadMore from '../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceSortSelect from '../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import useMarketplaceView from '../../../components/common/marketplace/toolbar/useMarketplaceView';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type SearchPaginationInput } from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ScenarioCard from './card/ScenarioCard';
import ScenariosList from './list/ScenariosList';
import ScenariosHeaderActions from './page/ScenariosHeaderActions';
import ScenariosHero from './page/ScenariosHero';
import ScenariosSidebar from './page/ScenariosSidebar';
import { type ScenarioRow } from './scenarioRow';

const AVAILABLE_FILTER_NAMES = [
  'scenario_author',
  'scenario_category',
  'scenario_domains',
  'scenario_kill_chain_phases',
  'scenario_name',
  'scenario_platforms',
  'scenario_recurrence',
  'scenario_severity',
  'scenario_tags',
  'scenario_type',
  'scenario_updated_at',
];

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

const Scenarios = () => {
  const { t } = useFormatter();
  const canManage = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const { viewMode, handleViewModeChange } = useMarketplaceView('scenarios:view-mode');
  const [scenarios, setScenarios] = useState<ScenarioRow[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [reloadCount, setReloadCount] = useState<number>(0);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'scenarios',
    buildSearchPagination({ sorts: initSorting('scenario_updated_at', 'DESC') }),
  );
  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchScenarios(input).finally(() => setLoading(false));
  };

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ScenarioRow>('scenario', scenarios, totalElements);

  const isChecked = (scenario: ScenarioRow) => (selectAll && !(scenario.scenario_id in (deSelectedElements || {})))
    || scenario.scenario_id in (selectedElements || {});

  const removeScenarios = (ids: string[]) => {
    setScenarios(prev => prev.filter(s => !ids.includes(s.scenario_id)));
    queryableHelpers.paginationHelpers.handleChangeTotalElements(Math.max(0, totalElements - ids.length));
  };

  const bulkDelete = () => {
    bulkDeleteScenarios({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      scenario_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      scenario_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      removeScenarios(result.data ?? []);
      handleClearSelectedElements();
    });
  };

  const loadingFirstPage = loading && searchPaginationInput.page === 0;
  const emptyState = <EmptyPlaceholder icon={<RouteOutlined />} title={t('No scenario matches these filters.')} />;

  return (
    <MarketplaceBrowser
      hero={<ScenariosHero />}
      sidebar={<ScenariosSidebar searchPaginationInput={searchPaginationInput} filterHelpers={queryableHelpers.filterHelpers} />}
      toolbar={(
        <PaginationComponentV2
          resultsCount
          infiniteScroll
          topBarButtons={(
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <ScenariosHeaderActions
                totalElements={totalElements}
                scenarios={scenarios}
                onImported={() => setReloadCount(count => count + 1)}
              />
              <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
            </Box>
          )}
          fetch={search}
          reloadContentCount={reloadCount}
          searchPaginationInput={searchPaginationInput}
          setContent={setScenarios}
          entityPrefix="scenario"
          availableFilterNames={AVAILABLE_FILTER_NAMES}
          queryableHelpers={queryableHelpers}
          filtersEndSlot={viewMode === 'grid'
            ? <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={SORT_OPTIONS} descendingFields={['scenario_updated_at']} />
            : null}
        />
      )}
    >
      {viewMode === 'grid'
        ? (
            <MarketplaceSections
              items={scenarios}
              loading={loadingFirstPage}
              emptyState={emptyState}
              renderItem={scenario => (
                <ScenarioCard key={scenario.scenario_id} scenario={scenario} onDelete={id => removeScenarios([id])} />
              )}
            />
          )
        : (
            <ScenariosList
              scenarios={scenarios}
              loading={loadingFirstPage}
              emptyState={emptyState}
              sortHelpers={queryableHelpers.sortHelpers}
              canManage={canManage}
              selectAll={selectAll}
              numberOfSelectedElements={numberOfSelectedElements}
              isChecked={isChecked}
              onToggle={onToggleEntity}
              onToggleSelectAll={handleToggleSelectAll}
              onClearSelection={handleClearSelectedElements}
              onBulkDelete={bulkDelete}
              onDelete={id => removeScenarios([id])}
            />
          )}
      <MarketplaceLoadMore
        hasMore={scenarios.length < totalElements}
        loading={loading && searchPaginationInput.page > 0}
        paused={loadingFirstPage}
        onLoadMore={() => queryableHelpers.paginationHelpers.handleChangePage(searchPaginationInput.page + 1)}
      />
    </MarketplaceBrowser>
  );
};

export default Scenarios;
