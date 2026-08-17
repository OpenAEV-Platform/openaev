import { Box, Button, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Popover, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material';
import { Binoculars, Cog } from 'mdi-material-ui';
import { type CSSProperties, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { archiveFindingsBulk, fetchFindingArchiveDays, updateFindingArchiveDays } from '../../../actions/findings/finding-actions';
import { triageFindingsBulk } from '../../../actions/findings/finding-triage-actions';
import { type UserHelper } from '../../../actions/helper';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../components/Empty';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { useHelper } from '../../../store';
import { type AggregatedFindingOutput, type FindingArchiveBulkItemOutput, type FindingTriageBulkItemOutput, type SearchPaginationInput, type TargetSimple } from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import InjectIcon from '../common/injects/InjectIcon';
import FindingBulkActionBar from './FindingBulkActionBar';
import FindingTriageControl from './FindingTriageControl';
import getFindingTypeLabel from './FindingTypeLabel';

const DEFAULT_ARCHIVE_DAYS = 30;

// Not a real filterable column (see FindingDistinctSearchService#extractArchivedSpecification on
// the backend): injected directly into the search request's filterGroup by the Active/Archived
// tabs below, never exposed as a user-selectable filter chip like availableFilterNames.
const ARCHIVED_FILTER_KEY = 'finding_archived';

type TriageStatus = NonNullable<AggregatedFindingOutput['finding_triage_status']>;
type ArchiveTab = 'active' | 'archived';

interface Props {
  searchDistinctFindings: (input: SearchPaginationInput) => Promise<{ data: Page<AggregatedFindingOutput> }>;
  filterLocalStorageKey: string;
  contextId?: string;
  // Column fields to hide (e.g. ['finding_asset_groups']) — defaults to showing all columns.
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

const inlineStyles: Record<string, CSSProperties> = ({
  finding_type: { width: '11%' },
  finding_value: { width: '20%' },
  finding_assets: { width: '14%' },
  finding_asset_groups: { width: '12%' },
  finding_source: { width: '9%' },
  finding_created_at: { width: '12%' },
  finding_updated_at: { width: '12%' },
  finding_triage_status: { width: '10%' },
});

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId, hiddenFields = [], compact = false, showArchiveTabs = false }: Props) => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);

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

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_updated_at',
    'finding_human_updated_at',
    'finding_asset_groups',
    'finding_assets',
    'finding_triage_status',
    'finding_source',
  ];

  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
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
    `${filterLocalStorageKey}-v2`,
    buildSearchPagination({
      sorts: initSorting('finding_updated_at', 'DESC'),
      ...(compact ? { size: compactPageSize } : {}),
    }),
  );
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
  } = useEntityToggle<AggregatedFindingOutput>('finding', findings, total);

  const handleArchiveTabChange = (tab: ArchiveTab) => {
    if (tab === archiveTab) return;
    handleClearSelectedElements();
    setArchiveTab(tab);
    setReloadContentCount(c => c + 1);
  };

  const selectedFindingIds = () => (selectAll
    ? findings.map(f => f.finding_id).filter(id => !(id in (deSelectedElements || {})))
    : Object.keys(selectedElements));

  const bulkTriage = (status: TriageStatus, justification: string): Promise<FindingTriageBulkItemOutput[]> => {
    const ids = selectedFindingIds();
    return triageFindingsBulk(ids, status, justification).then((res: { data: FindingTriageBulkItemOutput[] }) => {
      const successIds = new Set(res.data.filter(r => r.success && r.finding_id).map(r => r.finding_id));
      setFindings(current => current.map(f => (successIds.has(f.finding_id)
        ? {
            ...f,
            finding_triage_status: status,
          }
        : f)));
      handleClearSelectedElements();
      return res.data;
    });
  };

  const bulkArchive = (archived: boolean): Promise<FindingArchiveBulkItemOutput[]> => {
    const ids = selectedFindingIds();
    return archiveFindingsBulk({
      finding_ids: ids,
      archived,
    }).then((res: { data: FindingArchiveBulkItemOutput[] }) => {
      const byId = new Map(res.data.filter(r => r.success && r.finding_id).map(r => [r.finding_id, r.finding_archived_at ?? null]));
      setFindings(current => current.map(f => (byId.has(f.finding_id)
        ? {
            ...f,
            finding_archived_at: byId.get(f.finding_id) ?? undefined,
          }
        : f)));
      handleClearSelectedElements();
      return res.data;
    });
  };

  const headers = [
    {
      field: 'finding_type',
      label: 'Type',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => (
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
        <Tooltip title={finding.finding_value}>
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
              fontSize: 12,
              lineHeight: '18px',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              verticalAlign: 'middle',
            })}
          >
            {finding.finding_value}
          </Box>
        </Tooltip>
      ),
    },
    {
      field: 'finding_assets',
      label: 'Asset',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets
          targets={(finding.finding_assets || []).map(asset => ({
            target_id: asset.asset_id,
            target_name: asset.asset_name,
            target_type: 'ASSETS',
            // Category + platform drive the chip glyph (taxonomy icon, or the OS brand icon
            // for host-like endpoints) - same rendering as the asset pages.
            target_category: asset.asset_category,
            target_subtype: asset.endpoint_platform,
          })) as TargetSimple[]}
        />
      ),
    },
    {
      field: 'finding_asset_groups',
      label: 'Asset groups',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets
          targets={(finding.finding_asset_groups || []).map(group => ({
            target_id: group.asset_group_id,
            target_name: group.asset_group_name,
            target_type: 'ASSETS_GROUPS',
          })) as TargetSimple[]}
        />
      ),
    },
    {
      field: 'finding_source',
      label: 'Source',
      isSortable: false,
      // Manual findings (created via the API without a real inject/injector behind them) fall
      // back to the generic "unknown" glyph, same convention as InjectIcon when a type is missing.
      value: (finding: AggregatedFindingOutput) => (
        <InjectIcon
          type={finding.finding_source?.injector_type}
          tooltip={<>{finding.finding_source?.injector_name ?? t('Manual')}</>}
          variant="list"
        />
      ),
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
      // A finding whose first and last occurrence coincide has never been re-detected by a
      // subsequent scan/run: surface it as "New" so a first-time-only detection is visually
      // distinguishable from one that is still recurring (part of the finding lifecycle - see
      // finding_created_at "First seen" above). @CreationTimestamp/@UpdateTimestamp each call
      // Instant.now() independently at flush time, so real Hibernate-persisted rows can differ
      // by a few microseconds/milliseconds even on first insert - a strict string equality check
      // (reliable only for hand-seeded rows sharing one literal timestamp) misses these. A small
      // tolerance window makes the check robust for real-world data too.
      value: (finding: AggregatedFindingOutput) => {
        const isNew = Math.abs(
          new Date(finding.finding_updated_at).getTime() - new Date(finding.finding_created_at).getTime(),
        ) < 1000;
        if (!isNew) {
          return <>{nsdt(finding.finding_updated_at)}</>;
        }
        const label = t('New');
        const color = 'info.main';
        return (
          <Box sx={{ display: 'inline-block' }}>
            <Typography
              variant="caption"
              sx={{
                display: 'block',
                color,
                fontWeight: 700,
                letterSpacing: 1,
                lineHeight: 1.4,
              }}
            >
              {label.toUpperCase()}
            </Typography>
            <Box sx={{
              border: '1px solid',
              borderColor: color,
              borderRadius: 1,
              px: 1,
              py: 0.25,
              display: 'inline-block',
            }}
            >
              {nsdt(finding.finding_updated_at)}
            </Box>
          </Box>
        );
      },
    },
    {
      field: 'finding_triage_status',
      label: 'Triage status',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => (
        <FindingTriageControl
          variant="inList"
          findingId={finding.finding_id}
          status={finding.finding_triage_status}
          onStatusChange={(newStatus) => {
            // Optimistic local update: avoids a full page refetch for a single-row change.
            // If a triage-history tab is opened later for this finding, it fetches on mount
            // (see FindingComments-style tabs), so it will never read stale data from here.
            setFindings(current => current.map(f => (f.finding_id === finding.finding_id
              ? {
                  ...f,
                  finding_triage_status: newStatus,
                }
              : f)));
          }}
        />
      ),
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
  const exportProps = {
    exportType: 'FINDING',
    exportKeys: visibleHeaders.map(h => h.field),
    exportData: findings,
    exportFileName: `${t('Findings')}.csv`,
  };
  const topBarButtons = compact
    ? null
    : (
        <Box display="flex" gap={1} alignItems="center">
          <ExportButton totalElements={total} exportProps={exportProps} />
          {user?.user_admin && (
            <>
              <Tooltip title={t('Finding settings')}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<Cog fontSize="small" />}
                  onClick={e => setSettingsAnchorEl(e.currentTarget)}
                >
                  {t('Settings')}
                </Button>
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
                  minWidth: 260,
                }}
                >
                  <Typography variant="subtitle2">{t('Archive findings after (days)')}</Typography>
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
        </Box>
      );

  return (
    <>
      {showArchiveTabs && (
        <Tabs
          value={archiveTab}
          onChange={(_e, value: ArchiveTab) => handleArchiveTabChange(value)}
          sx={{ mb: 1 }}
        >
          <Tab label={t('Active')} value="active" />
          <Tab label={t('Archived')} value="archived" />
        </Tabs>
      )}
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
        topBarButtons={topBarButtons}
        reloadContentCount={reloadContentCount}
      />
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
      </List>
      {/* Compact mode has no pager: if the run produced more findings than one compact page, say so
          explicitly (with the total) so the list never reads as "this inject has N findings". */}
      {compact && !loading && total > findings.length && (
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
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
