import { Checkbox, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { LinkOffOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemIcon, ListItemText, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { exportThreatArsenalCsvMapper } from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import ExportButton from '../../../components/common/ExportButton';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import MarketplaceSortSelect from '../../../components/common/marketplace/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../components/common/marketplace/MarketplaceViewToggle';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { Can, useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ImportUploaderThreatArsenal from './ImportUploaderThreatArsenal';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalHero from './ThreatArsenalHero';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import { THREAT_ARSENAL_LIST_HEADERS, THREAT_ARSENAL_LIST_INLINE_STYLES, THREAT_ARSENAL_SORT_OPTIONS } from './threatArsenalListConfig';
import ThreatArsenalListRow from './ThreatArsenalListRow';
import ThreatArsenalSelectionBar from './ThreatArsenalSelectionBar';
import ThreatArsenalSidebar from './ThreatArsenalSidebar';
import useInstalledActions, { INSTALLED_FILTER_NAMES } from './useInstalledActions';
import useThreatArsenalView from './useThreatArsenalView';

const ThreatArsenalInstalled = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canDeleteThreatArsenal = ability.can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);

  const [selectedThreatArsenalAction, setSelectedThreatArsenalAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const { viewMode, handleViewModeChange } = useThreatArsenalView();

  const {
    threatArsenalActions,
    setThreatArsenalActions,
    replaceAction,
    prependActions,
    removeAction,
    loading,
    fetchActions,
    finishedBulkOperations,
    queryableHelpers,
    searchPaginationInput,
    totalElements,
    iconBarOrderedDomains,
    facetCounts,
    authorOptions,
    hasActiveFilters,
    handleResetFilters,
    handleSelectOrphaned,
    selection,
    bulkDelete,
  } = useInstalledActions();
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    numberOfSelectedElements,
    selectAllLabel,
    isSelectedAction,
    onToggleEntity,
    handleToggleSelectAll,
    handleClearSelectedElements,
  } = selection;

  const exportProps = {
    exportType: 'THREAT_ARSENAL_ACTIONS',
    exportKeys: [],
    exportData: threatArsenalActions,
    searchPaginationInput,
  };

  const renderGridView = () => (
    <MarketplaceGrid
      loading={loading}
      isEmpty={threatArsenalActions.length === 0}
      emptyState={<ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />}
    >
      {threatArsenalActions.map((action) => {
        const flags = computeActionFlags(action);
        return (
          <ThreatArsenalCard
            key={action.injector_contract_id}
            action={action}
            selected={selectedThreatArsenalAction?.injector_contract_id === action.injector_contract_id}
            checked={isSelectedAction(action)}
            anySelected={numberOfSelectedElements > 0}
            onSelect={() => setSelectedThreatArsenalAction(action)}
            onToggleEntity={event => onToggleEntity(action, event)}
            onUpdate={replaceAction}
            onDuplicate={(result: ThreatArsenalAction) => prependActions([result])}
            onDelete={() => removeAction(action.injector_contract_id)}
            disableUpdate={flags.disableUpdate}
            disableDuplicate={flags.disableDuplicate}
            disableJsonExport={flags.disableJsonExport}
            disableDelete={flags.disableDelete}
          />
        );
      })}
    </MarketplaceGrid>
  );

  const renderListView = () => {
    if (!loading && threatArsenalActions.length === 0) {
      return <ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />;
    }
    return (
      <List disablePadding>
        <ListItem
          divider
          secondaryAction={<>&nbsp;</>}
          sx={{ paddingLeft: 2 }}
        >
          {/* Spacers align the header labels with the checkbox + icon columns. */}
          <ListItemIcon style={{ minWidth: 38 }} />
          <ListItemIcon style={{ minWidth: 40 }} />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={THREAT_ARSENAL_LIST_HEADERS}
                inlineStylesHeaders={THREAT_ARSENAL_LIST_INLINE_STYLES}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {/* Skeleton rows mirror the exact real row anatomy (checkbox column,
            inject icon column, then the shared column widths) so the layout
            does not shift when the data lands. */}
        {loading && Array.from({ length: 10 }).map((_, idx) => (
          <Box
            key={idx}
            sx={{
              height: 50,
              display: 'flex',
              alignItems: 'center',
              paddingLeft: 2,
              paddingRight: 7,
              borderBottom: `1px solid ${theme.palette.divider}`,
            }}
          >
            <Box sx={{ minWidth: 38 }}>
              <Skeleton variant="rounded" width={18} height={18} animation="wave" />
            </Box>
            <Box sx={{ minWidth: 40 }}>
              <Skeleton variant="circular" width={26} height={26} animation="wave" />
            </Box>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              flex: 1,
            }}
            >
              {THREAT_ARSENAL_LIST_HEADERS.map(header => (
                <Box
                  key={header.field}
                  style={THREAT_ARSENAL_LIST_INLINE_STYLES[header.field]}
                  sx={{ paddingRight: '10px' }}
                >
                  <Skeleton variant="text" width="70%" height={20} animation="wave" />
                </Box>
              ))}
            </Box>
          </Box>
        ))}
        {!loading && threatArsenalActions.map((action) => {
          const flags = computeActionFlags(action);
          return (
            <ThreatArsenalListRow
              key={action.injector_contract_id}
              action={action}
              checked={isSelectedAction(action)}
              onSelect={() => setSelectedThreatArsenalAction(action)}
              onToggleEntity={event => onToggleEntity(action, event)}
              onUpdate={replaceAction}
              onDuplicate={(result: ThreatArsenalAction) => prependActions([result])}
              onDelete={() => removeAction(action.injector_contract_id)}
              disableUpdate={flags.disableUpdate}
              disableDuplicate={flags.disableDuplicate}
              disableJsonExport={flags.disableJsonExport}
              disableDelete={flags.disableDelete}
            />
          );
        })}
      </List>
    );
  };

  const headerRightSlot = (
    <>
      <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />

      {/* A plain row of actions: this was a ToggleButtonGroup used as a frame,
          which announced a group of choices that never existed. */}
      <Box sx={{
        display: 'flex',
        gap: 1,
      }}
      >
        <ExportButton
          totalElements={totalElements}
          exportProps={exportProps}
          exportCsvMapperFunction={exportThreatArsenalCsvMapper}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <ImportUploaderThreatArsenal
            onImport={prependActions}
          />
        </Can>
      </Box>

      {/* The create button used to ride the pagination row, which the card view
          makes 180px wider than the list view (it adds the sort select there) —
          measured at 1400px wide: 47px of the button visible in list view, 0 in
          card view, with no way to scroll to it. Here it sits beside the import
          icons, one instance, rendered in both views. */}
      {/* No margin here: the header's own row already puts 8px between its
          children — adding one made the gap 16. */}
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
        <CreateThreatArsenalAction
          onCreate={(result: ThreatArsenalAction) => prependActions([result])}
        />
      </Can>
    </>
  );

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: numberOfSelectedElements > 0 ? 12 : 4,
    }}
    >
      <MarketplaceBrowser
        hero={(
          <ThreatArsenalHero
            totalElements={totalElements}
            stats={[]}
            rightSlot={headerRightSlot}
          />
        )}
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
            fetch={fetchActions}
            searchPaginationInput={searchPaginationInput}
            setContent={setThreatArsenalActions}
            entityPrefix="threat_arsenal"
            availableFilterNames={INSTALLED_FILTER_NAMES}
            queryableHelpers={queryableHelpers}
            reloadContentCount={finishedBulkOperations}
            filtersEndSlot={viewMode === 'grid'
              ? (
                // List view sorts via its column headers; the select is grid-only.
                // Sits at the end of the filter row (after the clear-filters icon),
                // matching the OpenCTI card-view sort placement.
                  <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={THREAT_ARSENAL_SORT_OPTIONS} descendingFields={['action_updated_at']} />
                )
              : null}
            leftSlot={(
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 2.5,
                marginRight: 1.5,
                flexShrink: 0,
              }}
              >
                {/* Checkbox only, no text label (#7340): the toolbar must fit
                  on one row at ~1512px, so the former "Select all" /
                  "N selected" caption moves into the tooltip and the
                  accessible name. The selection count stays visible in the
                  floating selection bar once something is selected. This
                  single control drives select-all for BOTH the grid and
                  the list view (the toolbar is shared by the two). */}
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span className="inline-flex">
                      {/* The library box is 16x16 with no padding, so it no
                      longer sets the row's height — the 36px is the row's
                      own now. The mixed state travels as the `checked`
                      value, which is where Radix reads it. */}
                      <Checkbox
                        aria-label={selectAllLabel}
                        checked={
                          (!selectAll && numberOfSelectedElements > 0)
                          || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0)
                            ? 'indeterminate'
                            : selectAll
                        }
                        onCheckedChange={handleToggleSelectAll}
                        disabled={threatArsenalActions.length === 0}
                      />
                    </span>
                  </TooltipTrigger>
                  {selectAllLabel && <TooltipContent>{selectAllLabel}</TooltipContent>}
                </Tooltip>
                {canDeleteThreatArsenal && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <IconButton
                        icon={<LinkOffOutlined fontSize="small" />}
                        aria-label={t('Select orphaned actions')}
                        onClick={handleSelectOrphaned}
                        priority="tertiary"
                        size="sm"
                      />
                    </TooltipTrigger>
                    <TooltipContent>{t('Select orphaned actions (no injector, no payload) to purge them at once')}</TooltipContent>
                  </Tooltip>
                )}
              </Box>
            )}
          />
        )}
      >
        {viewMode === 'grid' ? renderGridView() : renderListView()}
      </MarketplaceBrowser>

      {(selectedThreatArsenalAction !== null) && (
        <ThreatArsenalInformationDrawer
          open={true}
          onClose={() => setSelectedThreatArsenalAction(null)}
          threatArsenalAction={selectedThreatArsenalAction}
        />
      )}

      {isRunTestDrawerOpened && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={selectAll}
          isOnlyOneItemSelected={
            selectAll
              ? Object.keys(deSelectedElements).length === totalElements - 1
              : numberOfSelectedElements === 1
          }
          selectionCount={numberOfSelectedElements}
          selectedElements={selectedElements}
          deSelectedElements={deSelectedElements}
          searchPaginationInput={searchPaginationInput}
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
            bulkDelete();
          }}
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
        onDelete={
          canDeleteThreatArsenal ? () => setBulkDeleteDialogOpened(true) : undefined
        }
      />
    </Box>
  );
};

export default ThreatArsenalInstalled;
