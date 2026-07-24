import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
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
import ContractOutputElementType from './ContractOutputElementType';

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
  finding_type: { width: '13%' },
  finding_value: { width: '27%' },
  finding_assets: { width: '17%' },
  finding_asset_groups: { width: '15%' },
  finding_created_at: { width: '14%' },
  finding_updated_at: { width: '14%' },
});

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId, hiddenFields = [], compact = false }: Props) => {
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_updated_at',
    'finding_asset_groups',
    'finding_assets',
  ];

  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
  // Default sort on last seen: the most recent activity is what tells whether a finding is still
  // alive or has been solved. The storage key is suffixed (-v2) so browsers that persisted the
  // previous "first seen" default pick up the new one instead of restoring the stale sort.
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(`${filterLocalStorageKey}-v2`, buildSearchPagination({ sorts: initSorting('finding_updated_at', 'DESC') }));
  const searchFindingsToload = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchDistinctFindings(input).finally(() => {
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
          {t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type)}
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
      label: 'Assets',
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
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_created_at)}</>,
    },
    {
      field: 'finding_updated_at',
      label: 'Last seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_updated_at)}</>,
    },
  ];

  const visibleHeaders = headers.filter(h => !hiddenFields.includes(h.field));

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
                inlineStylesHeaders={inlineStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={Binoculars} headers={visibleHeaders} headerStyles={inlineStyles} />
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
                              ...inlineStyles[header.field],
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
    </>
  );
};

export default FindingList;
