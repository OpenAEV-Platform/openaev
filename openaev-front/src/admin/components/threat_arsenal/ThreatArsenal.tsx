import { CategoryOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useState } from 'react';

import DialogDelete from '../../../components/common/DialogDelete';
import MarketplaceBrowser from '../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceLoadMore from '../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceSortSelect from '../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import useMarketplaceView from '../../../components/common/marketplace/toolbar/useMarketplaceView';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { Can, useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../common/domains/DomainsIcons';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import ThreatArsenalSelectAll from './bulk/ThreatArsenalSelectAll';
import ThreatArsenalSelectionBar from './bulk/ThreatArsenalSelectionBar';
import ThreatArsenalCard from './card/ThreatArsenalCard';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import useActiveActions, { ACTIVE_FILTER_NAMES } from './hook/useActiveActions';
import useOpenedAction from './hook/useOpenedAction';
import ThreatArsenalList from './list/ThreatArsenalList';
import { THREAT_ARSENAL_SORT_OPTIONS } from './list/threatArsenalListConfig';
import ThreatArsenalHeaderActions from './page/ThreatArsenalHeaderActions';
import ThreatArsenalHero from './page/ThreatArsenalHero';
import ThreatArsenalSidebar from './page/ThreatArsenalSidebar';
import ThreatArsenalTabs from './page/ThreatArsenalTabs';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import actionDomains from './utils/actionDomains';

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const canDelete = useAbility().can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [runTestAction, setRunTestAction] = useState<ThreatArsenalAction | null>(null);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal:view-mode-v2');
  const active = useActiveActions();
  const { threatArsenalActions, queryableHelpers, searchPaginationInput, totalElements, selection, loading } = active;

  const { openedAction, openedTab, openAction, showVersions, closeAction } = useOpenedAction(
    threatArsenalActions,
    queryableHelpers.textSearchHelpers.handleTextSearch,
  );

  const sections = {
    sectionKey: (action: ThreatArsenalAction) => actionDomains(action, active.domainOptions)[0]?.domain_name,
    sectionLabel: (name: string | undefined) => (name ? t(name) : t('Other')),
    sectionIcon: (name: string | undefined) => (name && DOMAIN_ICON_MAP[name]?.()) || <CategoryOutlined />,
  };
  const loadingFirstPage = loading && searchPaginationInput.page === 0;
  const emptyState = <ThreatArsenalEmptyState hasFilters={active.hasActiveFilters} onResetFilters={active.resetFilters} />;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: selection.count > 0 ? 12 : 4,
    }}
    >
      <MarketplaceBrowser
        hero={(
          <ThreatArsenalHero action={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
              <CreateThreatArsenalAction onCreate={active.addAction} />
            </Can>
          )}
          />
        )}
        tabs={<ThreatArsenalTabs active="active" />}
        sidebar={(
          <ThreatArsenalSidebar
            domainElements={active.iconBarOrderedDomains}
            authorOptions={active.authorOptions}
            facetCounts={active.facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <PaginationComponentV2
            attackPatterns={active.attackPatterns}
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
                  onImport={active.prependActions}
                />
                <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
              </Box>
            )}
            fetch={active.fetchActions}
            searchPaginationInput={searchPaginationInput}
            setContent={active.setThreatArsenalActions}
            entityPrefix="threat_arsenal"
            availableFilterNames={ACTIVE_FILTER_NAMES}
            queryableHelpers={queryableHelpers}
            reloadContentCount={active.finishedBulkOperations}
            filtersEndSlot={viewMode === 'grid'
              ? <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={THREAT_ARSENAL_SORT_OPTIONS} descendingFields={['action_updated_at']} />
              : null}
            leftSlot={viewMode === 'list'
              ? (
                  <ThreatArsenalSelectAll
                    label={selection.label}
                    checked={selection.checked}
                    disabled={threatArsenalActions.length === 0}
                    onToggle={selection.handleToggleSelectAll}
                    onSelectOrphans={canDelete ? active.toggleOrphaned : undefined}
                    orphansActive={active.isOrphanedScopeActive}
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
                sections={sections}
                renderItem={action => (
                  <ThreatArsenalCard
                    key={action.injector_contract_id}
                    action={action}
                    onSelect={() => openAction(action)}
                    onUpdate={active.replaceAction}
                    onDuplicate={active.addAction}
                    onDelete={() => active.removeAction(action.injector_contract_id)}
                    onRunTest={() => setRunTestAction(action)}
                    onShowVersions={showVersions}
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
                isChecked={selection.isChecked}
                onSelect={action => openAction(action)}
                onToggle={selection.onToggleEntity}
                onUpdate={active.replaceAction}
                onDuplicate={active.addAction}
                onDelete={active.removeAction}
                onShowVersions={showVersions}
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
          onClose={closeAction}
          threatArsenalAction={openedAction}
          initialTab={openedTab}
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
                isExclusionMode: selection.selectAll,
                isOnlyOneItemSelected: selection.selectAll
                  ? Object.keys(selection.deSelectedElements).length === totalElements - 1
                  : selection.count === 1,
                selectionCount: selection.count,
                selectedElements: selection.selectedElements,
                deSelectedElements: selection.deSelectedElements,
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
          handleSubmit={() => {
            setBulkDeleteDialogOpened(false);
            active.bulkDelete();
          }}
          text={
            selection.count === 1
              ? t('Do you want to delete this action?')
              : `${t('Do you want to delete the selected actions?')} (${selection.count})`
          }
        />
      </Can>

      <ThreatArsenalSelectionBar
        count={selection.count}
        totalElements={totalElements}
        hidden={isRunTestDrawerOpened}
        onClear={selection.handleClearSelectedElements}
        onRunTest={() => setRunTestDrawerOpened(true)}
        onDelete={canDelete ? () => setBulkDeleteDialogOpened(true) : undefined}
      />
    </Box>
  );
};

export default ThreatArsenal;
