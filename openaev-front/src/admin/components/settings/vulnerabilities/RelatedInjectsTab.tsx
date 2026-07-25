import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Header } from '../../../../components/common/SortHeadersList';
import FindingIcon from '../../../../components/FindingIcon';
import { useFormatter } from '../../../../components/i18n';
import ItemTargets from '../../../../components/ItemTargets';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ASSET_BASE_URL } from '../../../../constants/BaseUrls';
import type { AggregatedFindingOutput, FilterGroup, RelatedFindingOutput, SearchPaginationInput, TargetSimple } from '../../../../utils/api-types';
import ContractOutputElementType from '../../findings/ContractOutputElementType';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: {
    'height': 50,
    // Slightly larger pivot chips (asset / inject / simulation / scenario) than the ultra-dense
    // 20px table default, so they read as tappable buttons without breaking the row rhythm.
    '& .MuiChip-root': { height: 24 },
  },
}));

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  finding: AggregatedFindingOutput;
  additionalHeaders?: Header[];
  additionalFilterNames?: string[];
  contextId?: string;
}

const RelatedInjectsTab = ({ searchFindings, finding, contextId, additionalHeaders = [], additionalFilterNames = [] }: Props) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const { nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_created_at',
    'finding_updated_at',
    'finding_asset_groups',
    'finding_assets',
    ...additionalFilterNames,
  ];

  const [findings, setFindings] = useState<RelatedFindingOutput[]>([]);

  const baseFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildFilter('finding_value', [finding.finding_value], 'eq'),
      buildFilter('finding_type', [ContractOutputElementType[finding.finding_type as keyof typeof ContractOutputElementType]], 'eq'),
    ],
  };

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`related-injects-${finding.finding_type}-${finding.finding_value}-${contextId}`, buildSearchPagination({
    // Last seen first: the most recent occurrence tells whether the finding is still alive.
    sorts: initSorting('finding_updated_at', 'DESC'),
    filterGroup: baseFilter,
  }));

  const searchFindingsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchFindings(input).finally(() => {
      setLoading(false);
    });
  };
  const headers = [
    {
      field: 'finding_assets',
      label: 'Assets',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => (
        <ItemTargets
          targets={(finding.finding_assets || []).map(asset => ({
            target_id: asset.asset_id,
            target_name: asset.asset_name,
            target_type: 'ASSETS',
          })) as TargetSimple[]}
          getTargetLink={target => `${ASSET_BASE_URL}/${target.target_id}`}
        />
      ),
    },
    ...additionalHeaders,
    {
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (finding: RelatedFindingOutput) => <>{nsdt(finding.finding_created_at)}</>,
    },
    {
      field: 'finding_updated_at',
      label: 'Last seen',
      isSortable: true,
      value: (finding: RelatedFindingOutput) => <>{nsdt(finding.finding_updated_at)}</>,
    },
  ];

  const basis = `${42 / Math.max(additionalHeaders.length, 1)}%`;
  const inlineStyles: Record<string, CSSProperties> = ({
    finding_assets: { width: '22%' },
    finding_created_at: { width: '13%' },
    finding_updated_at: { width: '13%' },
    ...additionalHeaders.reduce((acc, header) => {
      acc[header.field] = { width: basis };
      return acc;
    }, {} as Record<string, CSSProperties>),
  });

  return (
    // No top padding: the gap under the tab bar (when shown) is owned by FindingDetail, so the
    // search input sits directly under the section label when the lone tab is hidden.
    <div style={{ padding: theme.spacing(0, 1, 0, 0) }}>
      <PaginationComponentV2
        fetch={searchFindingsToLoad}
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
          </ListItem>
        ))}
      </List>
    </div>
  );
};

export default RelatedInjectsTab;
