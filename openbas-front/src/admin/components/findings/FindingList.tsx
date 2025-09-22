import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { type CSSProperties, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import Drawer from '../../../components/common/Drawer';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import FindingIcon from '../../../components/FindingIcon';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type AggregatedFindingOutput, type RelatedFindingOutput, type SearchPaginationInput, type TargetSimple } from '../../../utils/api-types';
import FindingDetail from './FindingDetail';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  searchDistinctFindings: (input: SearchPaginationInput) => Promise<{ data: Page<AggregatedFindingOutput> }>;
  additionalHeaders?: Header[];
  additionalFilterNames?: string[];
  filterLocalStorageKey: string;
  contextId?: string;
}

const FindingList = ({ searchFindings, searchDistinctFindings, filterLocalStorageKey, contextId, additionalHeaders = [], additionalFilterNames = [] }: Props) => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_asset_groups',
    'finding_assets',
  ];

  const [searchParams, setSearchParams] = useSearchParams();
  const openIdParams = searchParams.get('open');

  const [cvssScore, setCvssScore] = useState<number | null>(null);
  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
  const [selectedFinding, setSelectedFinding] = useState<AggregatedFindingOutput | null>(null);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(filterLocalStorageKey, buildSearchPagination({ sorts: initSorting('finding_created_at', 'DESC') }));
  const searchFindingsToload = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchDistinctFindings(input).finally(() => {
      setLoading(false);
    });
  };

  useEffect(() => {
    if (!openIdParams) {
      return;
    }
    setSelectedFinding(findings.find(f => f.finding_id == openIdParams) ?? null);
  }, [openIdParams, findings]);

  const headers = [
    {
      field: 'finding_type',
      label: 'Type',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => finding.finding_type,
    },
    {
      field: 'finding_value',
      label: 'Value',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <Tooltip title={finding.finding_value}><span>{finding.finding_value}</span></Tooltip>,
    },
    {
      field: 'finding_assets',
      label: 'Endpoints',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets targets={(finding.finding_assets || []).map(asset => ({
          target_id: asset.asset_id,
          target_name: asset.asset_name,
          target_type: 'ASSETS',
        })) as TargetSimple[]}
        />
      ),
    },
  ];

  const inlineStyles: Record<string, CSSProperties> = ({
    finding_type: { width: '20%' },
    finding_value: { width: '30%' },
    finding_assets: { width: '30%' },
    finding_tags: { width: '20%' },
  });

  const handleCloseDrawer = () => {
    setSelectedFinding(null);
    setCvssScore(null);
    // Clean URL without causing refresh
    searchParams.delete('open');
    setSearchParams(searchParams, { replace: true });
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
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
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
        {loading ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} /> : findings.map(finding => (
          <ListItem
            key={finding.finding_id}
            classes={{ root: classes.item }}
            divider
            disablePadding
          >
            <ListItemButton
              classes={{ root: classes.item }}
              onClick={() => setSelectedFinding(finding)}
            >
              <ListItemIcon>
                <FindingIcon findingType={finding.finding_type} tooltip />
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
      </List>
      <Drawer
        open={!!selectedFinding}
        handleClose={handleCloseDrawer}
        title={selectedFinding?.finding_value || ''}
        additionalTitle={cvssScore ? 'CVSS' : undefined}
        additionalChipLabel={cvssScore?.toFixed(1)}
      >
        {selectedFinding && (
          <FindingDetail
            selectedFinding={selectedFinding}
            searchFindings={searchFindings}
            contextId={contextId}
            additionalHeaders={additionalHeaders}
            additionalFilterNames={additionalFilterNames}
            onCvssScore={setCvssScore}
          />
        )}
      </Drawer>
    </>
  );
};

export default FindingList;
