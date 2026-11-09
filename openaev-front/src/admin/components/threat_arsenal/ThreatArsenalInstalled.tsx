import { Box } from '@mui/material';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../actions/AttackPattern';
import { fetchThreatArsenalAction, fetchThreatArsenalActionIdOfPayload } from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import MarketplaceLoadMore from '../../../components/common/marketplace/MarketplaceLoadMore';
import MarketplaceSortSelect from '../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import useMarketplaceView from '../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type AttackPattern, type ThreatArsenalAction, type ThreatArsenalActionFullOutput, type ThreatArsenalActionIdOutput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { Can, useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import ThreatArsenalCardGrid, { type ThreatArsenalItemsProps } from './ThreatArsenalCardGrid';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalHeaderActions from './ThreatArsenalHeaderActions';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalList from './ThreatArsenalList';
import { THREAT_ARSENAL_SORT_OPTIONS } from './threatArsenalListConfig';
import ThreatArsenalSelectAll from './ThreatArsenalSelectAll';
import ThreatArsenalSelectionBar from './ThreatArsenalSelectionBar';
import ThreatArsenalSidebar from './ThreatArsenalSidebar';
import useInstalledActions, { INSTALLED_FILTER_NAMES } from './useInstalledActions';

interface Props { tabs?: ReactNode }

const ThreatArsenalInstalled = ({ tabs }: Props) => {
  const { t, tPick } = useFormatter();
  const canDelete = useAbility().can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [runTestAction, setRunTestAction] = useState<ThreatArsenalAction | null>(null);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal:view-mode-v2');
  const installed = useInstalledActions();
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const attackPatterns: AttackPattern[] = useHelper((helper: AttackPatternHelper) => helper.getAttackPatterns());
  const location = useLocation();
  // A payload just installed from the Available tab, or named by a notification: shown alone, its
  // drawer open. Read once, as the list rewrites the URL (and drops this state) when it loads.
  const openState = location.state as {
    openName?: string;
    openPayloadId?: string;
  } | null;
  const toOpen = useRef(openState?.openName);
  // A link to an action (?action=<id>, e.g. "See where it is used" or a card's Migrate): its drawer
  // opens, on the Versions tab with tab=versions, ready to migrate with focus=migrate.
  const [searchParams] = useSearchParams();
  const linked = useRef({
    actionId: searchParams.get('action'),
    tab: searchParams.get('tab') === 'versions' ? 'versions' as const : undefined,
    focusMigration: searchParams.get('focus') === 'migrate',
  });
  useEffect(() => {
    if (toOpen.current) {
      installed.queryableHelpers.textSearchHelpers.handleTextSearch(toOpen.current);
    } else if (linked.current.actionId) {
      fetchThreatArsenalAction(linked.current.actionId)
        .then((result: { data: ThreatArsenalActionFullOutput }) => {
          toOpen.current = tPick(result.data.action_labels);
          installed.queryableHelpers.textSearchHelpers.handleTextSearch(toOpen.current);
        })
        .catch(() => undefined);
    } else if (openState?.openPayloadId) {
      fetchThreatArsenalActionIdOfPayload(openState.openPayloadId)
        .then((result: { data: ThreatArsenalActionIdOutput }) => fetchThreatArsenalAction(result.data.action_id))
        .then((result: { data: ThreatArsenalActionFullOutput }) => {
          toOpen.current = tPick(result.data.action_labels);
          installed.queryableHelpers.textSearchHelpers.handleTextSearch(toOpen.current);
        })
        .catch(() => undefined);
    }
  }, []);
  useEffect(() => {
    const found = toOpen.current && installed.threatArsenalActions.find(action => tPick(action.action_labels) === toOpen.current);
    if (found) {
      toOpen.current = undefined;
      setOpenedAction(found);
    }
  }, [installed.threatArsenalActions]);
  const { selection } = installed;
  const selectionCount = selection.numberOfSelectedElements;

  const items: ThreatArsenalItemsProps = {
    actions: installed.threatArsenalActions,
    // Later pages load under the list, which stays in place.
    loading: installed.loading && installed.searchPaginationInput.page === 0,
    emptyState: <ThreatArsenalEmptyState hasFilters={installed.hasActiveFilters} onResetFilters={installed.handleResetFilters} />,
    isChecked: selection.isSelectedAction,
    onSelect: setOpenedAction,
    onToggle: selection.onToggleEntity,
    onUpdate: installed.replaceAction,
    onDuplicate: (result, open = true) => {
      installed.prependActions([result]);
      // F22: "Duplicate to edit" opens the copy right away; a customized copy opens its editor instead.
      if (open) setOpenedAction(result);
    },
    onDelete: installed.removeAction,
    onRunTest: setRunTestAction,
  };
  const partiallySelected = (!selection.selectAll && selectionCount > 0)
    || (selection.selectAll && Object.keys(selection.deSelectedElements ?? {}).length > 0);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: selectionCount > 0 ? 12 : 4,
    }}
    >
      <MarketplaceBrowser
        tabs={tabs}
        hero={(
          <MarketplaceHero
            title={t('Threat Arsenal')}
            subtitle={t('Browse, install and version the actions your simulations run.')}
            rightSlot={(
              <ThreatArsenalHeaderActions
                totalElements={installed.totalElements}
                actions={installed.threatArsenalActions}
                searchPaginationInput={installed.searchPaginationInput}
                onAdd={installed.prependActions}
              />
            )}
          />
        )}
        sidebar={(
          <ThreatArsenalSidebar
            domainElements={installed.iconBarOrderedDomains}
            authorOptions={installed.authorOptions}
            facetCounts={installed.facetCounts}
            searchPaginationInput={installed.searchPaginationInput}
            filterHelpers={installed.queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <PaginationComponentV2
            resultsCount
            infiniteScroll
            topBarButtons={<MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />}
            fetch={installed.fetchActions}
            searchPaginationInput={installed.searchPaginationInput}
            setContent={installed.setThreatArsenalActions}
            entityPrefix="threat_arsenal"
            availableFilterNames={INSTALLED_FILTER_NAMES}
            attackPatterns={attackPatterns}
            queryableHelpers={installed.queryableHelpers}
            reloadContentCount={installed.finishedBulkOperations}
            // List view sorts via its column headers; the select is grid-only.
            filtersEndSlot={viewMode === 'grid'
              ? <MarketplaceSortSelect sortHelpers={installed.queryableHelpers.sortHelpers} options={THREAT_ARSENAL_SORT_OPTIONS} descendingFields={['action_updated_at']} />
              : null}
            // Selection is a list view feature.
            leftSlot={viewMode === 'list' ? (
              <ThreatArsenalSelectAll
                label={selection.selectAllLabel}
                checked={partiallySelected ? 'indeterminate' : selection.selectAll}
                disabled={installed.threatArsenalActions.length === 0}
                onToggle={selection.handleToggleSelectAll}
                onSelectOrphans={canDelete ? installed.handleToggleOrphaned : undefined}
                orphansActive={installed.isOrphanedScopeActive}
              />
            ) : null}
          />
        )}
      >
        {viewMode === 'grid'
          ? <ThreatArsenalCardGrid {...items} selectedActionId={openedAction?.injector_contract_id} />
          : <ThreatArsenalList {...items} sortHelpers={installed.queryableHelpers.sortHelpers} />}
        <MarketplaceLoadMore
          hasMore={installed.threatArsenalActions.length < installed.totalElements}
          loading={installed.loading && installed.searchPaginationInput.page > 0}
          paused={installed.loading && installed.searchPaginationInput.page === 0}
          onLoadMore={() => installed.queryableHelpers.paginationHelpers.handleChangePage(installed.searchPaginationInput.page + 1)}
        />
      </MarketplaceBrowser>

      {openedAction !== null && (
        <ThreatArsenalInformationDrawer
          open
          onClose={() => setOpenedAction(null)}
          threatArsenalAction={openedAction}
          initialTab={openedAction.injector_contract_id === linked.current.actionId ? linked.current.tab : undefined}
          focusMigration={openedAction.injector_contract_id === linked.current.actionId && linked.current.focusMigration}
          onRunTest={setRunTestAction}
        />
      )}

      {isRunTestDrawerOpened && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={selection.selectAll}
          isOnlyOneItemSelected={
            selection.selectAll
              ? Object.keys(selection.deSelectedElements).length === installed.totalElements - 1
              : selectionCount === 1
          }
          selectionCount={selectionCount}
          selectedElements={selection.selectedElements}
          deSelectedElements={selection.deSelectedElements}
          searchPaginationInput={installed.searchPaginationInput}
          open={isRunTestDrawerOpened}
          onClose={() => setRunTestDrawerOpened(false)}
        />
      )}

      {runTestAction && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={false}
          isOnlyOneItemSelected
          selectionCount={1}
          selectedElements={{ [runTestAction.injector_contract_id]: runTestAction }}
          deSelectedElements={{}}
          searchPaginationInput={installed.searchPaginationInput}
          open
          onClose={() => setRunTestAction(null)}
        />
      )}

      <Can I={ACTIONS.DELETE} a={SUBJECTS.THREAT_ARSENALS}>
        <DialogDelete
          open={isBulkDeleteDialogOpened}
          handleClose={() => setBulkDeleteDialogOpened(false)}
          handleSubmit={() => {
            setBulkDeleteDialogOpened(false);
            installed.bulkDelete();
          }}
          text={
            selectionCount === 1
              ? t('Do you want to delete this action?')
              : `${t('Do you want to delete the selected actions?')} (${selectionCount})`
          }
        />
      </Can>

      <ThreatArsenalSelectionBar
        count={selectionCount}
        totalElements={installed.totalElements}
        hidden={isRunTestDrawerOpened}
        onClear={selection.handleClearSelectedElements}
        onRunTest={() => setRunTestDrawerOpened(true)}
        onDelete={canDelete ? () => setBulkDeleteDialogOpened(true) : undefined}
      />
    </Box>
  );
};

export default ThreatArsenalInstalled;
