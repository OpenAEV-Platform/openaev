import { CategoryOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useState } from 'react';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../actions/AttackPattern';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import {
  bulkDeleteThreatArsenalActions,
  fetchThreatArsenalAuthorCounts,
  searchThreatArsenalActions,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import { useAuthorFacetOptions } from '../../../components/common/facets/ContractFacets';
import MarketplaceBrowser from '../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceLoadMore from '../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceSortSelect from '../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import useMarketplaceView from '../../../components/common/marketplace/toolbar/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type AttackPattern, type SearchPaginationInput, type ThreatArsenalAction } from '../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../utils/bulkOperations';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { Can, useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../common/domains/DomainsIcons';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import { MITRE_FILTER_KEY } from '../common/filters/MitreFilter';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import ThreatArsenalSelectAll from './bulk/ThreatArsenalSelectAll';
import ThreatArsenalSelectionBar from './bulk/ThreatArsenalSelectionBar';
import ThreatArsenalCard from './card/ThreatArsenalCard';
import ThreatArsenalList from './list/ThreatArsenalList';
import { THREAT_ARSENAL_SORT_OPTIONS } from './list/threatArsenalListConfig';
import ThreatArsenalHeaderActions from './page/ThreatArsenalHeaderActions';
import ThreatArsenalHero from './page/ThreatArsenalHero';
import ThreatArsenalSidebar from './page/ThreatArsenalSidebar';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import { hasOrphanedScopeFilters, toggleOrphanedScope } from './threatArsenalOrphanedScopeUtils';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';
import actionDomains from './utils/actionDomains';

const AVAILABLE_FILTER_NAMES = [
  'action_injectors',
  'action_platforms',
  'action_domains',
  'action_tags',
  'action_payload_status',
  'action_updated_at',
  'action_author',
  MITRE_FILTER_KEY,
];

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const canDelete = useAbility().can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [runTestAction, setRunTestAction] = useState<ThreatArsenalAction | null>(null);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal:view-mode-v2');

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({
      sorts: [{
        property: 'action_updated_at',
        direction: 'DESC',
      }],
    }),
  );

  const [loading, setLoading] = useState<boolean>(false);
  const fetchActions = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchThreatArsenalActions({
      ...input,
      group_by_primary_domain: true,
    }).finally(() => setLoading(false));
  };

  // Bulk deletes run detached: reload the actions each time one finishes.
  const finishedBulkOperations = useBulkOperationsFinishedCount();
  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>('injector_contract', threatArsenalActions, totalElements);

  const { domainOptions } = useHelper((helper: DomainHelper) => ({ domainOptions: helper.getDomains() }));
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const attackPatterns: AttackPattern[] = useHelper((helper: AttackPatternHelper) => helper.getAttackPatterns());
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });
  const facetCounts = useThreatArsenalFacetCounts(searchPaginationInput);
  const authorOptions = useAuthorFacetOptions(fetchThreatArsenalAuthorCounts, searchPaginationInput);

  const hasActiveFilters = (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || !!searchPaginationInput.textSearch;
  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  const isOrphanedScopeActive = hasOrphanedScopeFilters(searchPaginationInput.filterGroup);
  const handleToggleOrphaned = () => toggleOrphanedScope({
    isActive: isOrphanedScopeActive,
    filterHelpers: queryableHelpers.filterHelpers,
    selectAll,
    handleToggleSelectAll,
    handleClearSelectedElements,
  });

  // Fire and forget, like every massive operation: the top bar reports progress, the list reloads when it ends.
  const handleBulkDelete = () => {
    const input = {
      ...searchPaginationInput,
      injector_contract_ids_to_process: selectAll ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: selectAll ? Object.keys(deSelectedElements) : [],
    };
    setBulkDeleteDialogOpened(false);
    handleClearSelectedElements();
    bulkDeleteThreatArsenalActions(input).catch(() => {});
  };

  let selectAllLabel = String(t('{count} actions selected', { count: numberOfSelectedElements }));
  if (numberOfSelectedElements === 0) selectAllLabel = t('Select all');
  if (numberOfSelectedElements === 1) selectAllLabel = t('1 action selected');
  const partiallySelected = (!selectAll && numberOfSelectedElements > 0)
    || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0);

  const prependActions = (results: ThreatArsenalAction[]) => setThreatArsenalActions(prev => [...results, ...prev]);
  const sections = {
    sectionKey: (action: ThreatArsenalAction) => actionDomains(action, domainOptions)[0]?.domain_name,
    sectionLabel: (name: string | undefined) => (name ? t(name) : t('Other')),
    sectionIcon: (name: string | undefined) => (name && DOMAIN_ICON_MAP[name]?.()) || <CategoryOutlined />,
  };
  const loadingFirstPage = loading && searchPaginationInput.page === 0;
  const emptyState = <ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />;
  const isChecked = (action: ThreatArsenalAction) => (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
    || action.injector_contract_id in (selectedElements || {});
  const updateAction = (result: ThreatArsenalAction) => setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === result.injector_contract_id ? result : a)));
  const duplicateAction = (result: ThreatArsenalAction) => prependActions([result]);
  const deleteAction = (actionId: string) => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== actionId));

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: numberOfSelectedElements > 0 ? 12 : 4,
    }}
    >
      <MarketplaceBrowser
        hero={<ThreatArsenalHero onCreate={result => prependActions([result])} />}
        sidebar={(
          <ThreatArsenalSidebar
            domainElements={iconBarOrderedDomains}
            authorOptions={authorOptions}
            facetCounts={facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <PaginationComponentV2
            attackPatterns={attackPatterns}
            resultsCount
            infiniteScroll
            topBarButtons={(
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <ThreatArsenalHeaderActions
                  totalElements={totalElements}
                  actions={threatArsenalActions}
                  searchPaginationInput={searchPaginationInput}
                  onImport={prependActions}
                />
                <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
              </Box>
            )}
            fetch={fetchActions}
            searchPaginationInput={searchPaginationInput}
            setContent={setThreatArsenalActions}
            entityPrefix="threat_arsenal"
            availableFilterNames={AVAILABLE_FILTER_NAMES}
            queryableHelpers={queryableHelpers}
            reloadContentCount={finishedBulkOperations}
            filtersEndSlot={viewMode === 'grid'
              ? <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={THREAT_ARSENAL_SORT_OPTIONS} descendingFields={['action_updated_at']} />
              : null}
            leftSlot={viewMode === 'list'
              ? (
                  <ThreatArsenalSelectAll
                    label={selectAllLabel}
                    checked={partiallySelected ? 'indeterminate' : selectAll}
                    disabled={threatArsenalActions.length === 0}
                    onToggle={handleToggleSelectAll}
                    onSelectOrphans={canDelete ? handleToggleOrphaned : undefined}
                    orphansActive={isOrphanedScopeActive}
                  />
                )
              : null}
          />
        )}
      >
        {viewMode === 'grid'
          ? (
              <MarketplaceSections
                items={threatArsenalActions}
                loading={loadingFirstPage}
                emptyState={emptyState}
                {...sections}
                renderItem={action => (
                  <ThreatArsenalCard
                    key={action.injector_contract_id}
                    action={action}
                    onSelect={() => setOpenedAction(action)}
                    onUpdate={updateAction}
                    onDuplicate={duplicateAction}
                    onDelete={() => deleteAction(action.injector_contract_id)}
                    onRunTest={() => setRunTestAction(action)}
                  />
                )}
              />
            )
          : (
              <ThreatArsenalList
                actions={threatArsenalActions}
                loading={loadingFirstPage}
                emptyState={emptyState}
                {...sections}
                sortHelpers={queryableHelpers.sortHelpers}
                isChecked={isChecked}
                onSelect={setOpenedAction}
                onToggle={onToggleEntity}
                onUpdate={updateAction}
                onDuplicate={duplicateAction}
                onDelete={deleteAction}
              />
            )}
        <MarketplaceLoadMore
          hasMore={threatArsenalActions.length < totalElements}
          loading={loading && searchPaginationInput.page > 0}
          paused={loading && searchPaginationInput.page === 0}
          onLoadMore={() => queryableHelpers.paginationHelpers.handleChangePage(searchPaginationInput.page + 1)}
        />
      </MarketplaceBrowser>

      {openedAction !== null && (
        <ThreatArsenalInformationDrawer
          open
          onClose={() => setOpenedAction(null)}
          threatArsenalAction={openedAction}
        />
      )}

      {(isRunTestDrawerOpened || runTestAction) && (
        <ThreatArsenalRunTestDrawer
          {...(runTestAction
            ? {
                isExclusionMode: false,
                isOnlyOneItemSelected: true,
                selectionCount: 1,
                selectedElements: { [runTestAction.injector_contract_id]: runTestAction },
                deSelectedElements: {},
              }
            : {
                isExclusionMode: selectAll,
                isOnlyOneItemSelected: selectAll
                  ? Object.keys(deSelectedElements).length === totalElements - 1
                  : numberOfSelectedElements === 1,
                selectionCount: numberOfSelectedElements,
                selectedElements,
                deSelectedElements,
              })}
          searchPaginationInput={searchPaginationInput}
          open
          onClose={() => {
            setRunTestDrawerOpened(false);
            setRunTestAction(null);
          }}
        />
      )}
      <Can I={ACTIONS.DELETE} a={SUBJECTS.THREAT_ARSENALS}>
        <DialogDelete
          open={isBulkDeleteDialogOpened}
          handleClose={() => setBulkDeleteDialogOpened(false)}
          handleSubmit={handleBulkDelete}
          text={
            numberOfSelectedElements === 1
              ? t('Do you want to delete this action?')
              : `${t('Do you want to delete the selected actions?')} (${numberOfSelectedElements})`
          }
        />
      </Can>

      <ThreatArsenalSelectionBar
        count={numberOfSelectedElements}
        totalElements={totalElements}
        hidden={isRunTestDrawerOpened}
        onClear={handleClearSelectedElements}
        onRunTest={() => setRunTestDrawerOpened(true)}
        onDelete={canDelete ? () => setBulkDeleteDialogOpened(true) : undefined}
      />
    </Box>
  );
};

export default ThreatArsenal;
