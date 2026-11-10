import { Box } from '@mui/material';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../actions/AttackPattern';
import { fetchThreatArsenalAction, fetchThreatArsenalActionIdOfPayload } from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
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
import ThreatArsenalHero from './ThreatArsenalHero';
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
  // Read on every change of the link, not only on load: a Migrate link clicked on a card of this
  // very page changes the URL without mounting the page again.
  const [searchParams] = useSearchParams();
  const linkedActionId = searchParams.get('action');
  const linked = useRef<{
    actionId: string | null;
    tab?: 'versions';
    focusMigration: boolean;
  }>({
    actionId: null,
    focusMigration: false,
  });
  useEffect(() => {
    if (!linkedActionId) return;
    linked.current = {
      actionId: linkedActionId,
      tab: searchParams.get('tab') === 'versions' ? 'versions' : undefined,
      focusMigration: searchParams.get('focus') === 'migrate',
    };
    fetchThreatArsenalAction(linkedActionId)
      .then((result: { data: ThreatArsenalActionFullOutput }) => {
        const name = tPick(result.data.action_labels);
        // Already on the page: open it at once. Otherwise search it, and open it when it shows.
        const shown = installed.threatArsenalActions.find(action => action.injector_contract_id === linkedActionId);
        if (shown) {
          setOpenedAction(null);
          setOpenedAction(shown);
        } else {
          toOpen.current = name;
          installed.queryableHelpers.textSearchHelpers.handleTextSearch(name);
        }
      })
      .catch(() => undefined);
  }, [linkedActionId, searchParams.get('focus'), searchParams.get('tab')]);
  useEffect(() => {
    if (toOpen.current) {
      installed.queryableHelpers.textSearchHelpers.handleTextSearch(toOpen.current);
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
      // A link to an older version opens the action on its latest one: keep the link's tab for it.
      if (linked.current.actionId) linked.current = {
        ...linked.current,
        actionId: found.injector_contract_id,
      };
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
        hero={<ThreatArsenalHero onCreate={(result: ThreatArsenalAction) => installed.prependActions([result])} />}
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
            // Export and Import act on this list: they sit with it, beside the view toggle.
            topBarButtons={(
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
              >
                <ThreatArsenalHeaderActions
                  totalElements={installed.totalElements}
                  actions={installed.threatArsenalActions}
                  searchPaginationInput={installed.searchPaginationInput}
                  onAdd={installed.prependActions}
                />
                <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
              </div>
            )}
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
