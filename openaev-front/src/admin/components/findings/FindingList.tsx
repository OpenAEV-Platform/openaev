import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { Binoculars } from 'mdi-material-ui';
import { type CSSProperties, useState } from 'react';
import { useNavigate } from 'react-router';

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
}

const inlineStyles: Record<string, CSSProperties> = ({
  finding_type: { width: '15%' },
  finding_value: { width: '31%' },
  finding_assets: { width: '20%' },
  finding_asset_groups: { width: '18%' },
  finding_created_at: { width: '16%' },
});

const FindingList = ({ searchDistinctFindings, filterLocalStorageKey, contextId }: Props) => {
  const navigate = useNavigate();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_asset_groups',
    'finding_assets',
  ];

  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(filterLocalStorageKey, buildSearchPagination({ sorts: initSorting('finding_created_at', 'DESC') }));
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
      value: (finding: AggregatedFindingOutput) => (
        <Tooltip title={finding.finding_value}>
          <span>{finding.finding_value}</span>
        </Tooltip>
      ),
    },
    {
      field: 'finding_assets',
      label: 'Endpoints',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets
          targets={(finding.finding_assets || []).map(asset => ({
            target_id: asset.asset_id,
            target_name: asset.asset_name,
            target_type: 'ASSETS',
          })) as TargetSimple[]}
          variant="reduced-view"
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
          variant="reduced-view"
        />
      ),
    },
    {
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_created_at)}</>,
    },
  ];

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
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={Binoculars} headers={headers} headerStyles={inlineStyles} />
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
                  onClick={() => navigate(`/admin/findings/${finding.finding_id}`)}
                >
                  <ListItemIcon>
                    <FindingIcon findingType={finding.finding_type} />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
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
