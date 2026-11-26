import { Typography } from '@mui/material';
import { useState } from 'react';

import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type SearchPaginationInput } from '../../../utils/api-types';
import FindingListView from './components/FindingListView';

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

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId, hiddenFields = [], compact = false }: Props) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_updated_at',
    'finding_asset_groups',
    'finding_assets',
    'finding_sources',
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
      <FindingListView
        loading={loading}
        findings={findings}
        queryableHelpers={queryableHelpers}
        hiddenFields={hiddenFields}
      />
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
