import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { FileDownloadOutlined, GridViewOutlined, InfoOutlined, ViewListOutlined } from '@mui/icons-material';
import { Box, Button, Checkbox, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Popover, Tab, Tabs, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { Binoculars, Cog } from 'mdi-material-ui';
import { type CSSProperties, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { archiveFindingsBulk, fetchFindingArchiveDays, updateFindingArchiveDays } from '../../../actions/findings/finding-actions';
import { triageFindingsBulk } from '../../../actions/findings/finding-triage-actions';
import { type UserHelper } from '../../../actions/helper';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../components/Empty';
import { FDS } from '../../../components/fds-tokens.generated';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { useHelper } from '../../../store';
import { type AggregatedFindingOutput, type FindingArchiveBulkItemOutput, type FindingTriageBulkItemOutput, type SearchPaginationInput } from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import InjectIcon from '../common/injects/InjectIcon';
import FindingBulkActionBar from './FindingBulkActionBar';
import FindingCard from './FindingCard';
import FindingHero from './FindingHero';
import { findingAssetLabel, findingSources, type StableFindingRow } from './FindingRowUtils';
import FindingSidebar from './FindingSidebar';
import FindingSortSelect from './FindingSortSelect';
import getFindingTypeLabel from './FindingTypeLabel';
import useFindingFacetCounts from './useFindingFacetCounts';

const DEFAULT_ARCHIVE_DAYS = 30;

// Not a real filterable column (see FindingDistinctSearchService#extractArchivedSpecification on
// the backend): injected directly into the search request's filterGroup by the Active/Archived
// tabs below, never exposed as a user-selectable filter chip like availableFilterNames.
const ARCHIVED_FILTER_KEY = 'finding_archived';

type TriageStatus = NonNullable<AggregatedFindingOutput['finding_triage_status']>;
type ArchiveTab = 'active' | 'archived';
type ViewMode = 'grid' | 'list';
type StableFindingListItem = StableFindingRow;

const VIEW_MODE_STORAGE_KEY = 'findings:view-mode';

const readViewMode = (): ViewMode => {
  if (typeof window === 'undefined') return 'list';
  return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'grid' ? 'grid' : 'list';
};

interface Props {
  searchDistinctFindings: (input: SearchPaginationInput) => Promise<{ data: Page<StableFindingListItem> }>;
  filterLocalStorageKey: string;
  contextId?: string;
  // Column fields to hide — defaults to showing all columns.
  hiddenFields?: string[];
  // Compact mode for embedding in a narrow container (e.g. the attack-path drawer): hides the
  // search/filters/pagination top bar. Defaults to false so the full-page usage is unchanged.
  compact?: boolean;
  // Shows the Active/Archived tabs and filters the query server-side accordingly (see
  // FindingDistinctSearchService#extractArchivedSpecification). Only enabled on the main Finding
  // page (Findings.tsx): scoped views (by simulation/scenario/inject/endpoint) call a different
  // backend method that does not implement this filter, and would silently ignore it if sent.
  showArchiveTabs?: boolean;
}

// US.1 list columns: Type, Value, Asset, Source, First seen, Last seen.
const inlineStyles: Record<string, CSSProperties> = ({
  finding_type: { width: '13%' },
  finding_value: { width: '25%' },
  finding_asset_name: { width: '22%' },
  finding_source: { width: '14%' },
  finding_created_at: { width: '13%' },
  finding_updated_at: { width: '13%' },
});

const csvCell = (value: string) => (/[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId, hiddenFields = [], compact = false, showArchiveTabs = false }: Props) => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);
  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);

  const { user } = useHelper((helper: UserHelper) => ({ user: helper.getMe() }));

  // Archive settings: number of days of inactivity after which a finding is treated as archived
  // by the backend (see FindingDistinctSearchService#extractArchivedSpecification), driving the
  // Active/Archived tabs. Configurable per-tenant, only shown/editable to admins, from the
  // settings menu on the non-compact (full page) list.
  const [archiveDaysDraft, setArchiveDaysDraft] = useState<string>(String(DEFAULT_ARCHIVE_DAYS));
  const [settingsAnchorEl, setSettingsAnchorEl] = useState<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!compact) {
      fetchFindingArchiveDays().then((res: { data: { finding_archive_days?: number } }) => {
        const days = res.data.finding_archive_days ?? DEFAULT_ARCHIVE_DAYS;
        setArchiveDaysDraft(String(days));
      });
    }
  }, [compact]);

  const saveArchiveDays = () => {
    const days = parseInt(archiveDaysDraft, 10);
    if (!Number.isNaN(days) && days > 0) {
      updateFindingArchiveDays({ finding_archive_days: days }).then(() => {
        setSettingsAnchorEl(null);
      });
    }
  };

  // US.1 filter bar: the quick filters (Severity, Type, Asset) plus Value, Asset group, Source,
  // First seen and Last seen.
  const availableFilterNames = [
    'finding_severity',
    'finding_type',
    'finding_assets',
    'finding_value',
    'finding_asset_groups',
    'finding_source',
    'finding_created_at',
    'finding_updated_at',
  ];

  const [findings, setFindings] = useState<StableFindingListItem[]>([]);
  // Total across all pages, tracked in compact mode (no pager) so we can tell the user when the list is
  // truncated instead of silently hiding findings beyond the page.
  const [total, setTotal] = useState<number>(0);
  // Compact mode drops the pager, so raise the page size well above the default to cover most scans; a
  // "showing X of N" note still appears if a run produces more than this.
  const compactPageSize = 100;
  // Active/Archived tabs: which occurrences the query should return. Only relevant when
  // showArchiveTabs is set (main Finding page); the filter is injected into the request below,
  // never exposed as a regular user-selectable filter chip.
  const [archiveTab, setArchiveTab] = useState<ArchiveTab>('active');
  // Bumped on every tab switch to force PaginationComponentV2 to refetch (its own effect only
  // reacts to searchPaginationInput/contextId identity changes, neither of which the tab switch
  // touches - see reloadContentCount in PaginationComponentV2).
  const [reloadContentCount, setReloadContentCount] = useState(0);
  // Default sort on last seen: the most recent activity is what tells whether a finding is still
  // alive or has been solved. The storage key is suffixed (-v2) so browsers that persisted the
  // previous "first seen" default pick up the new one instead of restoring the stale sort.
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    `${filterLocalStorageKey}-v3`,
    buildSearchPagination({
      sorts: initSorting('finding_updated_at', 'DESC'),
      ...(compact ? { size: compactPageSize } : {}),
    }),
  );
  const facetInput = useMemo<SearchPaginationInput>(() => ({
    ...searchPaginationInput,
    filterGroup: {
      mode: searchPaginationInput.filterGroup?.mode ?? 'and',
      filters: [
        ...(searchPaginationInput.filterGroup?.filters ?? []),
        ...(showArchiveTabs
          ? [{
              id: ARCHIVED_FILTER_KEY,
              key: ARCHIVED_FILTER_KEY,
              operator: 'eq' as const,
              values: [archiveTab === 'archived' ? 'true' : 'false'],
            }]
          : []),
      ],
    },
  }), [archiveTab, searchPaginationInput, showArchiveTabs]);
  const facetCounts = useFindingFacetCounts(facetInput);
  const searchFindingsToload = (input: SearchPaginationInput) => {
    setLoading(true);
    // Injects the (non-user-facing) archived-status filter server-side so the "Archived" tab
    // doesn't fetch (and then hide) every archived finding just to filter it out client-side -
    // see FindingDistinctSearchService#extractArchivedSpecification.
    const effectiveInput = showArchiveTabs
      ? {
          ...input,
          filterGroup: {
            mode: input.filterGroup?.mode ?? 'and',
            filters: [
              ...(input.filterGroup?.filters ?? []),
              {
                id: ARCHIVED_FILTER_KEY,
                key: ARCHIVED_FILTER_KEY,
                operator: 'eq' as const,
                values: [archiveTab === 'archived' ? 'true' : 'false'],
              },
            ],
          },
        }
      : input;
    return searchDistinctFindings(effectiveInput)
      .then((res) => {
        setTotal(res.data.totalElements);
        return res;
      })
      .finally(() => {
        setLoading(false);
      });
  };

  // Bulk selection: checkboxes + select-all + floating action bar (Triage / Archive), full
  // Finding page only (not the compact/embedded drawer usage) - mirrors the Scenarios page
  // pattern (useEntityToggle). No frontend permission gating here: the backend enforces
  // MANAGE_FINDING_TRIAGE / MANAGE_FINDING_ARCHIVE per-item and reports failures individually
  // (same convention as the single-row FindingTriageControl, which is also ungated client-side).
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<StableFindingListItem>('finding', findings, total);

  const handleArchiveTabChange = (tab: ArchiveTab) => {
    if (tab === archiveTab) return;
    handleClearSelectedElements();
    setArchiveTab(tab);
    setReloadContentCount(c => c + 1);
  };

  const handleViewModeChange = (_: unknown, value: ViewMode | null) => {
    if (!value) return;
    setViewMode(value);
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
  };

  const loadSelectedFindings = (): Promise<StableFindingListItem[]> => {
    if (!selectAll) {
      return Promise.resolve(Object.values(selectedElements));
    }

    const pageSize = 1000;
    const loadPage = (page: number) => searchDistinctFindings({
      ...facetInput,
      page,
      size: pageSize,
    });
    return loadPage(0).then(async (firstPage) => {
      const remainingPages = await Promise.all(
        Array.from(
          { length: Math.max(firstPage.data.totalPages - 1, 0) },
          (_, index) => loadPage(index + 1),
        ),
      );
      return [firstPage, ...remainingPages]
        .flatMap(response => response.data.content)
        .filter(finding => !(finding.finding_id in (deSelectedElements || {})));
    });
  };

  const bulkTriage = (status: TriageStatus, justification: string): Promise<FindingTriageBulkItemOutput[]> => {
    return loadSelectedFindings().then((selectedFindings) => {
      const legacyToStableId = new Map(
        selectedFindings
          .filter(finding => finding.finding_legacy_id)
          .map(finding => [finding.finding_legacy_id as string, finding.finding_id]),
      );
      return triageFindingsBulk([...legacyToStableId.keys()], status, justification).then((res: { data: FindingTriageBulkItemOutput[] }) => {
        const successIds = new Set(
          res.data
            .filter(r => r.success && r.finding_id)
            .map(r => legacyToStableId.get(r.finding_id as string))
            .filter((id): id is string => Boolean(id)),
        );
        setFindings(current => current.map(f => (successIds.has(f.finding_id)
          ? {
              ...f,
              finding_triage_status: status,
            }
          : f)));
        handleClearSelectedElements();
        return res.data;
      });
    });
  };

  const bulkArchive = (archived: boolean): Promise<FindingArchiveBulkItemOutput[]> => {
    return loadSelectedFindings().then((selectedFindings) => {
      const legacyToStableId = new Map(
        selectedFindings
          .filter(finding => finding.finding_legacy_id)
          .map(finding => [finding.finding_legacy_id as string, finding.finding_id]),
      );
      return archiveFindingsBulk({
        finding_ids: [...legacyToStableId.keys()],
        archived,
      }).then((res: { data: FindingArchiveBulkItemOutput[] }) => {
        const byId = new Map(
          res.data
            .filter(r => r.success && r.finding_id)
            .map(r => [legacyToStableId.get(r.finding_id as string), r.finding_archived_at ?? null]),
        );
        setFindings(current => current.map(f => (byId.has(f.finding_id)
          ? {
              ...f,
              finding_archived_at: byId.get(f.finding_id) ?? undefined,
            }
          : f)));
        handleClearSelectedElements();
        return res.data;
      });
    });
  };

  const headers = [
    {
      field: 'finding_type',
      label: 'Type',
      isSortable: true,
      value: (finding: StableFindingListItem) => (
        <span style={{
          fontWeight: 600,
          textTransform: 'uppercase',
          fontSize: 11,
          letterSpacing: '0.05em',
        }}
        >
          {getFindingTypeLabel(t, finding.finding_type, finding.finding_cloud_provider)}
        </span>
      ),
    },
    {
      field: 'finding_value',
      label: 'Value',
      isSortable: true,
      // Findings are technical values (ports, sockets, hostnames, credentials...): render them
      // as inline code, mirroring the <pre> block of the finding overview page.
      value: (finding: AggregatedFindingOutput) => (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box
              component="code"
              sx={theme => ({
                display: 'inline-block',
                maxWidth: '95%',
                padding: '2px 8px',
                borderRadius: 1,
                backgroundColor: theme.palette.background.accent,
                border: `1px solid ${theme.palette.divider}`,
                fontFamily: 'Consolas, monaco, monospace',
                fontSize: FDS.scalars['--text-3'],
                lineHeight: '18px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                verticalAlign: 'middle',
              })}
            >
              {finding.finding_value}
            </Box>
          </TooltipTrigger>
          {finding.finding_value && <TooltipContent>{finding.finding_value}</TooltipContent>}
        </Tooltip>
      ),
    },
    {
      field: 'finding_asset_name',
      label: 'Asset',
      isSortable: true,
      value: (finding: StableFindingListItem) => (
        <Box
          component="span"
          sx={{
            fontFamily: 'monospace',
            overflowWrap: 'anywhere',
          }}
        >
          {findingAssetLabel(finding)}
        </Box>
      ),
    },
    {
      field: 'finding_source',
      label: 'Source',
      isSortable: false,
      // Every injector that detected the finding: the first one, then "+N" (US.1). Not sortable.
      value: (finding: StableFindingListItem) => {
        const sources = findingSources(finding);
        if (sources.length === 0) return <>-</>;
        return (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            <InjectIcon
              type={sources[0].injector_type}
              tooltip={<>{sources.map(source => source.injector_name).join(', ')}</>}
              variant="list"
            />
            {sources.length > 1 && <span>{`+${sources.length - 1}`}</span>}
          </Box>
        );
      },
    },
    {
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_created_at)}</>,
    },
    {
      field: 'finding_updated_at',
      label: 'Last seen',
      isSortable: true,
      tooltip: 'finding_last_seen_tooltip',
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_updated_at)}</>,
    },
  ];

  const visibleHeaders = headers.filter(h => !hiddenFields.includes(h.field));
  // Hiding columns (e.g. the compact drawer) leaves the fixed per-column widths summing to < 100%, which
  // squeezes and truncates the last columns (Assets). Rescale the visible columns proportionally so they
  // fill the row; a no-op in full mode where the widths already total 100%.
  const visibleWidthTotal = visibleHeaders.reduce(
    (sum, h) => sum + (parseFloat(String(inlineStyles[h.field]?.width ?? '0')) || 0),
    0,
  );
  const visibleStyles: Record<string, CSSProperties> = Object.fromEntries(
    visibleHeaders.map((h) => {
      const w = parseFloat(String(inlineStyles[h.field]?.width ?? '0')) || 0;
      return [h.field, {
        ...inlineStyles[h.field],
        ...(visibleWidthTotal > 0 && w > 0 ? { width: `${((w / visibleWidthTotal) * 100).toFixed(2)}%` } : {}),
      }];
    }),
  );

  // Export current page as CSV, and (admin-only) let the tenant configure the archive-days
  // threshold used by the "Archived" tab's server-side filter (see
  // FindingDistinctSearchService#extractArchivedSpecification). Hidden in compact mode (embedded
  // drawers).
  // US.2: every finding matching the filters and search (not only the page), in the active sort,
  // with the list columns plus Severity, whatever the view.
  const [exporting, setExporting] = useState(false);
  const exportCsv = () => {
    setExporting(true);
    const pageSize = 1000;
    const loadPage = (page: number) => searchDistinctFindings({
      ...facetInput,
      page,
      size: pageSize,
    });
    loadPage(0)
      .then(async (firstPage) => {
        const remaining = await Promise.all(
          Array.from({ length: Math.max(firstPage.data.totalPages - 1, 0) }, (_, index) => loadPage(index + 1)),
        );
        const rows = [firstPage, ...remaining].flatMap(response => response.data.content);
        const header = ['Type', 'Value', 'Asset', 'Source', 'First seen', 'Last seen', 'Severity'];
        const lines = rows.map(finding => [
          finding.finding_type ?? '',
          finding.finding_value ?? '',
          findingAssetLabel(finding),
          findingSources(finding).map(source => source.injector_name).join(', ') || '-',
          finding.finding_created_at ?? '',
          finding.finding_updated_at ?? '',
          finding.finding_severity ?? 'UNKNOWN',
        ].map(value => csvCell(String(value))).join(','));
        const csv = [header.join(','), ...lines].join('\r\n');
        const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `findings_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
      })
      .finally(() => setExporting(false));
  };
  const actions = compact
    ? null
    : (
        <>
          {showArchiveTabs && viewMode === 'grid' && (
            <FindingSortSelect sortHelpers={queryableHelpers.sortHelpers} />
          )}
          {showArchiveTabs && (
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              size="small"
              onChange={handleViewModeChange}
              aria-label={t('View mode')}
              sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
            >
              <ToggleButton value="grid" aria-label={t('Grid view')}>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <GridViewOutlined fontSize="small" />
                  </TooltipTrigger>
                  <TooltipContent>{t('Grid view')}</TooltipContent>
                </Tooltip>
              </ToggleButton>
              <ToggleButton value="list" aria-label={t('List view')}>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <ViewListOutlined fontSize="small" />
                  </TooltipTrigger>
                  <TooltipContent>{t('List view')}</TooltipContent>
                </Tooltip>
              </ToggleButton>
            </ToggleButtonGroup>
          )}
          <Tooltip>
            <TooltipTrigger asChild>
              <span>
                <IconButton
                  size="small"
                  onClick={exportCsv}
                  disabled={total === 0 || exporting}
                  aria-label={t('Export CSV')}
                >
                  <FileDownloadOutlined fontSize="small" />
                </IconButton>
              </span>
            </TooltipTrigger>
            <TooltipContent>{total === 0 ? t('No finding to export') : t('Export CSV')}</TooltipContent>
          </Tooltip>
          {user?.user_admin && (
            <>
              <Tooltip>
                <TooltipTrigger asChild>
                  <IconButton
                    size="small"
                    onClick={e => setSettingsAnchorEl(e.currentTarget)}
                    aria-label={t('Finding settings')}
                  >
                    <Cog fontSize="small" />
                  </IconButton>
                </TooltipTrigger>
                <TooltipContent>{t('Finding settings')}</TooltipContent>
              </Tooltip>
              <Popover
                open={Boolean(settingsAnchorEl)}
                anchorEl={settingsAnchorEl}
                onClose={() => setSettingsAnchorEl(null)}
                anchorOrigin={{
                  vertical: 'bottom',
                  horizontal: 'right',
                }}
                transformOrigin={{
                  vertical: 'top',
                  horizontal: 'right',
                }}
              >
                <Box sx={{
                  p: 2,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 1,
                  width: 200,
                }}
                >
                  <Typography variant="subtitle2">{t('Archive findings after (days)')}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t('Archived findings are removed from this page after this many days, but remain accessible from the simulations that found them.')}
                  </Typography>
                  <TextField
                    type="number"
                    size="small"
                    value={archiveDaysDraft}
                    onChange={e => setArchiveDaysDraft(e.target.value)}
                    slotProps={{ htmlInput: { min: 1 } }}
                  />
                  <Button variant="contained" size="small" onClick={saveArchiveDays}>
                    {t('Save')}
                  </Button>
                </Box>
              </Popover>
            </>
          )}
        </>
      );

  const archiveTabs = showArchiveTabs && (
    <Tabs
      value={archiveTab}
      onChange={(_e, value: ArchiveTab) => handleArchiveTabChange(value)}
    >
      <Tab label={t('Active')} value="active" />
      <Tab
        value="archived"
        label={(
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            {t('Archived')}
            <Tooltip>
              <TooltipTrigger asChild>
                <InfoOutlined
                  fontSize="small"
                  onClick={event => event.stopPropagation()}
                />
              </TooltipTrigger>
              <TooltipContent>{t('Findings that have been inactive for the configured period. They remain available here before being removed from this page and remain accessible from the simulations that found them.')}</TooltipContent>
            </Tooltip>
          </Box>
        )}
      />
    </Tabs>
  );

  const pagination = (
    <PaginationComponentV2
      fetch={searchFindingsToload}
      searchPaginationInput={searchPaginationInput}
      setContent={setFindings}
      entityPrefix="finding"
      availableFilterNames={availableFilterNames}
      queryableHelpers={queryableHelpers}
      contextId={contextId}
      searchEnable={!compact}
      disableFilters={compact}
      disablePagination={compact}
      topBarButtons={showArchiveTabs ? null : actions}
      leftSlot={showArchiveTabs && viewMode === 'grid'
        ? (
            <>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span>
                    <Checkbox
                      size="small"
                      checked={selectAll}
                      indeterminate={
                        (!selectAll && numberOfSelectedElements > 0)
                        || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0)
                      }
                      onChange={handleToggleSelectAll}
                      disabled={findings.length === 0}
                      slotProps={{ input: { 'aria-label': t('Select all') } }}
                    />
                  </span>
                </TooltipTrigger>
                <TooltipContent>{t('Select all')}</TooltipContent>
              </Tooltip>
            </>
          )
        : undefined}
      reloadContentCount={reloadContentCount}
    />
  );

  const listView = (
    <List>
      <ListItem
        sx={{
          textTransform: 'uppercase',
          paddingTop: 0,
          ...(numberOfSelectedElements > 0
            ? {
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : {}),
        }}
      >
        {!compact && (
          <ListItemIcon style={{ minWidth: 40 }}>
            <Checkbox
              edge="start"
              checked={selectAll}
              disableRipple
              onChange={handleToggleSelectAll}
            />
          </ListItemIcon>
        )}
        {numberOfSelectedElements > 0
          ? (
              <ListItemText
                primary={(
                  <FindingBulkActionBar
                    numberOfSelectedElements={numberOfSelectedElements}
                    onClear={handleClearSelectedElements}
                    onTriage={bulkTriage}
                    onArchive={bulkArchive}
                    archiveAction={archiveTab === 'active' ? 'archive' : 'unarchive'}
                  />
                )}
              />
            )
          : (
              <>
                <ListItemIcon />
                <ListItemText
                  primary={(
                    <SortHeadersComponentV2
                      headers={visibleHeaders}
                      inlineStylesHeaders={visibleStyles}
                      sortHelpers={queryableHelpers.sortHelpers}
                    />
                  )}
                />
              </>
            )}
      </ListItem>
      {loading
        ? <PaginatedListLoader Icon={Binoculars} headers={visibleHeaders} headerStyles={visibleStyles} />
        : findings.map(finding => (
            <ListItem
              key={finding.finding_id}
              sx={{ height: 50 }}
              divider
              disablePadding
              data-testid="finding-row"
            >
              {!compact && (
                <ListItemIcon
                  style={{
                    minWidth: 40,
                    marginLeft: 16,
                  }}
                  onClick={event => onToggleEntity(finding, event)}
                >
                  <Checkbox
                    edge="start"
                    checked={
                      (selectAll && !(finding.finding_id in (deSelectedElements || {})))
                      || finding.finding_id in (selectedElements || {})
                    }
                    disableRipple
                  />
                </ListItemIcon>
              )}
              <ListItemButton
                sx={{ height: 50 }}
                component={Link}
                to={`/admin/findings/${finding.finding_id}`}
              >
                <ListItemIcon>
                  <FindingIcon findingType={finding.finding_type} />
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {visibleHeaders.map(header => (
                        <div
                          key={header.field}
                          style={{
                            ...bodyItemsStyles.bodyItem,
                            ...visibleStyles[header.field],
                          }}
                        >
                          {header.value && header.value(finding)}
                        </div>
                      ))}
                    </div>
                  )}
                />
              </ListItemButton>
            </ListItem>
          ))}
      {!loading && findings.length === 0 && <Empty message={t('No finding found.')} />}
      {!loading && findings.length > 0 && queryableHelpers.sortHelpers.getSortBy() === 'finding_severity_rank' && (
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            display: 'block',
            px: 2,
            py: 1,
          }}
        >
          {t('Sorted by severity (chosen in the grid view).')}
        </Typography>
      )}
    </List>
  );

  const gridView = (
    <>
      {numberOfSelectedElements > 0 && (
        <Box sx={{
          padding: 1,
          border: theme => `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
          backgroundColor: 'background.accent',
        }}
        >
          <FindingBulkActionBar
            numberOfSelectedElements={numberOfSelectedElements}
            onClear={handleClearSelectedElements}
            onTriage={bulkTriage}
            onArchive={bulkArchive}
            archiveAction={archiveTab === 'active' ? 'archive' : 'unarchive'}
          />
        </Box>
      )}
      {!loading && findings.length === 0
        ? <Empty message={t('No finding found.')} />
        : (
            <Box sx={{
              display: 'grid',
              // Same breakpoints as the Integrations catalog grid.
              gridTemplateColumns: {
                xs: 'minmax(0, 1fr)',
                sm: 'repeat(2, minmax(0, 1fr))',
                lg: 'repeat(3, minmax(0, 1fr))',
                xl: 'repeat(4, minmax(0, 1fr))',
              },
              gap: 2,
            }}
            >
              {findings.map(finding => (
                <FindingCard
                  key={finding.finding_id}
                  finding={finding}
                  checked={
                    (selectAll && !(finding.finding_id in (deSelectedElements || {})))
                    || finding.finding_id in (selectedElements || {})
                  }
                  anySelected={numberOfSelectedElements > 0}
                  onToggleEntity={event => onToggleEntity(finding, event)}
                />
              ))}
            </Box>
          )}
    </>
  );

  const content = showArchiveTabs && viewMode === 'grid' ? gridView : listView;

  if (showArchiveTabs && !compact) {
    return (
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <FindingHero
          totalElements={total}
          rightSlot={actions}
          bottomSlot={archiveTabs}
        />
        <Box sx={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 3,
        }}
        >
          <FindingSidebar
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
            facetCounts={facetCounts}
          />
          <Box sx={{
            display: 'flex',
            minWidth: 0,
            flex: 1,
            flexDirection: 'column',
            gap: 2,
          }}
          >
            {pagination}
            {content}
          </Box>
        </Box>
      </Box>
    );
  }

  return (
    <>
      {archiveTabs}
      {pagination}
      {content}
      {/* Compact mode has no pager: if the run produced more findings than one compact page, say so
          explicitly (with the total) so the list never reads as "this inject has N findings". */}
      {compact && !loading && total > findings.length && (
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            display: 'block',
            px: 2,
            py: 1,
          }}
        >
          {t('Showing {shown} of {total} findings — open the inject to see them all.', {
            shown: findings.length,
            total,
          })}
        </Typography>
      )}
    </>
  );
};

export default FindingList;
