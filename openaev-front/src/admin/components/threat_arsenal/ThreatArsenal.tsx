import {
  GridViewOutlined,
  LinkOffOutlined,
  ViewListOutlined,
} from '@mui/icons-material';
import {
  Box,
  Checkbox,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Skeleton,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import {
  bulkDeleteThreatArsenalActions,
  exportThreatArsenalCsvMapper,
  fetchThreatArsenalAuthorCounts,
  searchThreatArsenalActions,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import DialogDelete from '../../../components/common/DialogDelete';
import ExportButton from '../../../components/common/ExportButton';
import { useAuthorFacetOptions } from '../../../components/common/facets/ContractFacets';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import {
  type SearchPaginationInput,
  type ThreatArsenalAction,
} from '../../../utils/api-types';
import { useBulkOperationsFinishedCount } from '../../../utils/bulkOperations';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ImportUploaderThreatArsenal from './ImportUploaderThreatArsenal';
import ThreatArsenalCard from './ThreatArsenalCard';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalHero from './ThreatArsenalHero';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import { THREAT_ARSENAL_LIST_HEADERS, THREAT_ARSENAL_LIST_INLINE_STYLES } from './threatArsenalListConfig';
import ThreatArsenalListRow from './ThreatArsenalListRow';
import ThreatArsenalSelectionBar from './ThreatArsenalSelectionBar';
import ThreatArsenalSidebar from './ThreatArsenalSidebar';
import ThreatArsenalSortSelect from './ThreatArsenalSortSelect';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

type ViewMode = 'grid' | 'list';

const VIEW_MODE_STORAGE_KEY = 'threat-arsenal:view-mode-v2';

const readViewMode = (): ViewMode => {
  if (typeof window === 'undefined') return 'grid';
  const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
  return stored === 'list' ? 'list' : 'grid';
};

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useContext(AbilityContext);
  const canDeleteThreatArsenal = ability.can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS);

  const [selectedThreatArsenalAction, setSelectedThreatArsenalAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [isBulkDeleteDialogOpened, setBulkDeleteDialogOpened] = useState<boolean>(false);
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);
  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);

  const handleViewModeChange = (_: unknown, value: ViewMode | null) => {
    if (!value) return;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
    }
  };

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
    return searchThreatArsenalActions({ ...input }).finally(() => setLoading(false));
  };

  // Massive operations (bulk delete) run detached from this screen: reload the page
  // of actions every time one finishes, so the list and the total stop showing the
  // rows that were deleted in the background. Only live transitions bump this count
  // (the history replayed at startup does not), so mounting never triggers a reload.
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
  } = useEntityToggle<ThreatArsenalAction>(
    'injector_contract',
    threatArsenalActions,
    totalElements,
  );

  const { domainOptions } = useHelper(
    (helper: DomainHelper) => ({ domainOptions: helper.getDomains() }),
  );
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });

  // Platform + status counts come from a global aggregation endpoint (never
  // from `threatArsenalActions`, which only holds the currently loaded page and
  // would yield misleading page-bound counts).
  const facetCounts = useThreatArsenalFacetCounts(searchPaginationInput);

  const availableFilterNames = [
    'action_injectors',
    'action_platforms',
    'action_domains',
    'action_tags',
    'action_payload_status',
    'action_updated_at',
    'action_author',
  ];

  const exportProps = {
    exportType: 'THREAT_ARSENAL_ACTIONS',
    exportKeys: [],
    exportData: threatArsenalActions,
    searchPaginationInput,
  };

  const isSelectedAction = (action: ThreatArsenalAction) =>
    (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
    || action.injector_contract_id in (selectedElements || {});

  const computeRowFlags = (action: ThreatArsenalAction) => {
    // Dummy injectors were removed from the platform (#6779): actions whose
    // injector is not registered yet simply carry no injector type.
    const isUnregistered = action.action_injector_type == null;
    // `payload_collector_type` is optional in api-types, so a loose `!= null`
    // is required: a strict `!== null` would treat `undefined` (field absent
    // for manually created payloads) as "coming from a collector".
    const isFromCollector = action.action_payload?.payload_collector_type != null;
    return {
      // Update is disabled for actions coming from a collector or whose
      // injector is not registered yet.
      disableUpdate: isFromCollector || isUnregistered,
      disableDuplicate: action.action_payload == null || isUnregistered,
      disableJsonExport: action.action_payload == null || isUnregistered,
      // Orphaned actions (their injector was removed, hence the "question mark"
      // card) can always be purged. Otherwise delete is disabled for actions
      // without a payload and for collector actions that are not deprecated.
      disableDelete: isUnregistered
        ? false
        : (action.action_payload == null
          || (isFromCollector && action.action_payload.payload_status !== 'DEPRECATED')),
    };
  };

  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  // Quick "purge orphans" shortcut: narrow the list to the dead "question mark"
  // cards and turn on select-all so the floating Delete action purges them at
  // once. A question-mark card is an action with NO injector AND NO payload:
  //  - no injector (`action_injectors` empty) => its injector was removed, so it
  //    can never run;
  //  - no payload (`action_payload_status` empty) => this excludes manually
  //    created payloads, which have a payload (hence a real icon) and run fine
  //    even when momentarily unlinked from an injector.
  // Scoping via filters keeps the bulk delete from touching healthy actions.
  const handleSelectOrphaned = () => {
    queryableHelpers.filterHelpers.handleRemoveFilterByKey('action_injectors');
    queryableHelpers.filterHelpers.handleRemoveFilterByKey('action_payload_status');
    queryableHelpers.filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key: 'action_injectors',
      operator: 'empty',
      values: [],
      mode: 'and',
    });
    queryableHelpers.filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key: 'action_payload_status',
      operator: 'empty',
      values: [],
      mode: 'and',
    });
    if (!selectAll) {
      handleToggleSelectAll();
    }
  };

  // Fire and forget, like every other massive operation in the platform: the dialog
  // closes and the selection clears immediately, progress is reported by the
  // massive-operations indicator in the top bar, and the list reloads once the
  // operation reaches a terminal state (see `reloadContentCount` below). Returning
  // the promise instead would keep the confirmation dialog in its loading state for
  // the whole deletion - minutes on a large scope, where the request may even
  // outlive the proxy timeout while the backend keeps committing chunk by chunk.
  const handleBulkDelete = () => {
    const input = {
      ...searchPaginationInput,
      injector_contract_ids_to_process: selectAll ? [] : Object.keys(selectedElements),
      injector_contract_ids_to_ignore: selectAll ? Object.keys(deSelectedElements) : [],
    };
    setBulkDeleteDialogOpened(false);
    handleClearSelectedElements();
    // Failures are already surfaced by the shared error notifier, so the rethrow is
    // swallowed here to avoid an unhandled rejection on this detached call.
    bulkDeleteThreatArsenalActions(input).catch(() => {});
  };

  const hasActiveFilters = !!(
    (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || (searchPaginationInput.textSearch && searchPaginationInput.textSearch.length > 0)
  );

  // Full author universe + per-filter counts (backend aggregation), so the
  // sidebar keeps every author visible and greys out the zero-count ones.
  const authorOptions = useAuthorFacetOptions(fetchThreatArsenalAuthorCounts, searchPaginationInput);

  const renderGridView = () => {
    if (loading) {
      return (
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
        }}
        >
          {Array.from({ length: 12 }).map((_, idx) => (
            <Box
              key={idx}
              sx={{
                height: 200,
                borderRadius: 1,
                overflow: 'hidden',
                border: `1px solid ${theme.palette.divider}`,
              }}
            >
              <Skeleton variant="rectangular" height={64} animation="wave" />
              <Box sx={{ padding: 2 }}>
                <Skeleton variant="text" width="80%" height={28} animation="wave" />
                <Skeleton variant="text" width="60%" height={20} animation="wave" />
                <Box sx={{
                  display: 'flex',
                  gap: 1,
                  marginTop: 2,
                }}
                >
                  <Skeleton variant="circular" width={20} height={20} animation="wave" />
                  <Skeleton variant="circular" width={20} height={20} animation="wave" />
                  <Skeleton variant="rectangular" width={60} height={20} animation="wave" sx={{ borderRadius: 1 }} />
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
      );
    }

    if (threatArsenalActions.length === 0) {
      return <ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />;
    }

    return (
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
        }}
      >
        {threatArsenalActions.map((action) => {
          const flags = computeRowFlags(action);
          return (
            <ThreatArsenalCard
              key={action.injector_contract_id}
              action={action}
              selected={selectedThreatArsenalAction?.injector_contract_id === action.injector_contract_id}
              checked={isSelectedAction(action)}
              anySelected={numberOfSelectedElements > 0}
              onSelect={() => setSelectedThreatArsenalAction(action)}
              onToggleEntity={event => onToggleEntity(action, event)}
              onUpdate={(result: ThreatArsenalAction) =>
                setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === action.injector_contract_id ? result : a)))}
              onDuplicate={(result: ThreatArsenalAction) => setThreatArsenalActions(prev => [result, ...prev])}
              onDelete={() => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== action.injector_contract_id))}
              disableUpdate={flags.disableUpdate}
              disableDuplicate={flags.disableDuplicate}
              disableJsonExport={flags.disableJsonExport}
              disableDelete={flags.disableDelete}
            />
          );
        })}
      </Box>
    );
  };

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
          const flags = computeRowFlags(action);
          return (
            <ThreatArsenalListRow
              key={action.injector_contract_id}
              action={action}
              checked={isSelectedAction(action)}
              onSelect={() => setSelectedThreatArsenalAction(action)}
              onToggleEntity={event => onToggleEntity(action, event)}
              onUpdate={(result: ThreatArsenalAction) =>
                setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === action.injector_contract_id ? result : a)))}
              onDuplicate={(result: ThreatArsenalAction) => setThreatArsenalActions(prev => [result, ...prev])}
              onDelete={() => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== action.injector_contract_id))}
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
      <ToggleButtonGroup
        value={viewMode}
        exclusive
        size="small"
        onChange={handleViewModeChange}
        aria-label={t('View mode')}
        sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
      >
        <ToggleButton value="grid" aria-label={t('Grid view')}>
          <Tooltip title={t('Grid view')}>
            <GridViewOutlined fontSize="small" />
          </Tooltip>
        </ToggleButton>
        <ToggleButton value="list" aria-label={t('List view')}>
          <Tooltip title={t('List view')}>
            <ViewListOutlined fontSize="small" />
          </Tooltip>
        </ToggleButton>
      </ToggleButtonGroup>

      <ToggleButtonGroup value="fake" exclusive>
        <ExportButton
          totalElements={totalElements}
          exportProps={exportProps}
          exportCsvMapperFunction={exportThreatArsenalCsvMapper}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <ImportUploaderThreatArsenal
            onImport={results => setThreatArsenalActions(prev => [...results, ...prev])}
          />
        </Can>
      </ToggleButtonGroup>
    </>
  );

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: numberOfSelectedElements > 0 ? 12 : 4,
    }}
    >
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenal'),
          current: true,
        }]}
      />

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <ThreatArsenalHero
          totalElements={totalElements}
          stats={[]}
          rightSlot={headerRightSlot}
        />

        <Box sx={{
          display: 'flex',
          gap: 3,
          alignItems: 'flex-start',
        }}
        >
          <ThreatArsenalSidebar
            domainElements={iconBarOrderedDomains}
            authorOptions={authorOptions}
            facetCounts={facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />

          <Box sx={{
            flex: 1,
            minWidth: 0,
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            <Box sx={{ '& > div:first-of-type': { marginTop: 0 } }}>
              <PaginationComponentV2
                fetch={fetchActions}
                searchPaginationInput={searchPaginationInput}
                setContent={setThreatArsenalActions}
                entityPrefix="threat_arsenal"
                availableFilterNames={availableFilterNames}
                queryableHelpers={queryableHelpers}
                reloadContentCount={finishedBulkOperations}
                filtersEndSlot={viewMode === 'grid'
                  ? (
                      // List view sorts via its column headers; the select is grid-only.
                      // Sits at the end of the filter row (after the clear-filters icon),
                      // matching the OpenCTI card-view sort placement.
                      <ThreatArsenalSortSelect sortHelpers={queryableHelpers.sortHelpers} />
                    )
                  : null}
                topBarButtons={(
                  <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
                    <CreateThreatArsenalAction
                      onCreate={(result: ThreatArsenalAction) => {
                        setThreatArsenalActions(prev => [result, ...prev]);
                      }}
                    />
                  </Can>
                )}
                leftSlot={(
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5,
                    marginRight: 2,
                  }}
                  >
                    <FormControlLabel
                      label={(
                        <Typography
                          variant="body2"
                          sx={{
                            color: 'text.secondary',
                            fontWeight: 500,
                            fontSize: 12.5,
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {(() => {
                            if (numberOfSelectedElements === 0) return t('Select all');
                            if (numberOfSelectedElements === 1) return t('1 action selected');
                            return t('{count} actions selected', { count: numberOfSelectedElements });
                          })()}
                        </Typography>
                      )}
                      control={(
                        <Checkbox
                          size="small"
                          checked={selectAll}
                          indeterminate={
                            (!selectAll && numberOfSelectedElements > 0)
                            || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0)
                          }
                          onChange={handleToggleSelectAll}
                          disabled={threatArsenalActions.length === 0}
                        />
                      )}
                      sx={{
                        'marginLeft': -0.5,
                        'marginRight': 1,
                        '& .MuiFormControlLabel-label': { marginLeft: 0.5 },
                      }}
                    />
                    {canDeleteThreatArsenal && (
                      <Tooltip title={t('Select orphaned actions (no injector, no payload) to purge them at once')}>
                        <IconButton
                          size="small"
                          aria-label={t('Select orphaned actions')}
                          onClick={handleSelectOrphaned}
                          sx={{ color: 'text.secondary' }}
                        >
                          <LinkOffOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Box>
                )}
              />
            </Box>

            {viewMode === 'grid' ? renderGridView() : renderListView()}
          </Box>
        </Box>
      </Box>

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
        onClear={handleClearSelectedElements}
        onRunTest={() => setRunTestDrawerOpened(true)}
        onDelete={
          canDeleteThreatArsenal ? () => setBulkDeleteDialogOpened(true) : undefined
        }
      />
    </Box>
  );
};

export default ThreatArsenal;
