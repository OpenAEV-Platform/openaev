import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip, Typography } from '@mui/material';
import { Binoculars } from 'mdi-material-ui';
import { type CSSProperties, useState } from 'react';
import { Link } from 'react-router';

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
import { type AggregatedFindingOutput, type SearchPaginationInput, type TargetSimple } from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import FindingTriageControl from './FindingTriageControl';
import getFindingTypeLabel from './FindingTypeLabel';

interface Props {
  searchDistinctFindings: (input: SearchPaginationInput) => Promise<{ data: Page<AggregatedFindingOutput> }>;
  filterLocalStorageKey: string;
  contextId?: string;
  // Column fields to hide (e.g. ['finding_asset_groups']) — defaults to showing all columns.
  hiddenFields?: string[];
  // Compact mode for embedding in a narrow container (e.g. the attack-path drawer): hides the
  // search/filters/pagination top bar. Defaults to false so the full-page usage is unchanged.
  compact?: boolean;
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

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId, hiddenFields = [], compact = false }: Props) => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);

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
    return searchDistinctFindings(input)
      .then((res) => {
        setTotal(res.data.totalElements);
        return res;
      })
      .finally(() => {
        setLoading(false);
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
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_updated_at)}</>,
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

  return (
    <>
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
      />
      <List>
        <ListItem
          sx={{
            textTransform: 'uppercase',
            paddingTop: 0,
          }}
        >
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
