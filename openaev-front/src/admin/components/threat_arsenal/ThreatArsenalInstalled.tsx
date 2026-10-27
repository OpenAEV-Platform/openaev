import { Box } from '@mui/material';
import { type ReactNode, useState } from 'react';

import DialogDelete from '../../../components/common/DialogDelete';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import MarketplaceLoadMore from '../../../components/common/marketplace/MarketplaceLoadMore';
import MarketplaceSortSelect from '../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import useMarketplaceView from '../../../components/common/marketplace/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
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
  const { t } = useFormatter();
  const canDelete = useAbility().can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);
  const [openedAction, setOpenedAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal:view-mode-v2');
  const installed = useInstalledActions();
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
    onDuplicate: (result) => {
      installed.prependActions([result]);
      // F22: "Duplicate to edit" opens the copy right away.
      setOpenedAction(result);
    },
    onDelete: installed.removeAction,
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
