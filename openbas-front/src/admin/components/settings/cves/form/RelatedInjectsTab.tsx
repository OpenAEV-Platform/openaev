import { HubOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { buildFilter } from '../../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Header } from '../../../../../components/common/SortHeadersList';
import FindingIcon from '../../../../../components/FindingIcon';
import ItemTargets from '../../../../../components/ItemTargets';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import type { FilterGroup, FindingOutput, SearchPaginationInput, TargetSimple } from '../../../../../utils/api-types';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<FindingOutput> }>;
  finding: FindingOutput;
  additionalHeaders?: Header[];
  additionalFilterNames?: string[];
  contextId?: string;
}

const RelatedInjectsTab = ({ searchFindings, finding, contextId, additionalHeaders = [], additionalFilterNames = [] }: Props) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_created_at',
    'finding_asset_groups',
    'finding_assets',
    ...additionalFilterNames,
  ];

  const [findings, setFindings] = useState<FindingOutput[]>([]);

  const baseFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildFilter('finding_value', [finding.finding_value], 'eq'),
    ],
  };

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`related-injects-${finding.finding_type}-${finding.finding_value}-${contextId}`, buildSearchPagination({
    sorts: initSorting('finding_created_at', 'DESC'),
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
      label: 'Endpoints',
      isSortable: false,
      value: (finding: FindingOutput) => (
        <ItemTargets targets={(finding.finding_assets || []).map(asset => ({
          target_id: asset.asset_id,
          target_name: asset.asset_name,
          target_type: 'ASSETS',
        })) as TargetSimple[]}
        />
      ),
    },
    ...additionalHeaders,
  ];

  const basis = `${50 / (additionalHeaders.length - 1)}%`;
  const inlineStyles: Record<string, CSSProperties> = ({
    finding_assets: { width: basis },
    ...additionalHeaders.reduce((acc, header) => {
      acc[header.field] = { width: basis };
      return acc;
    }, {} as Record<string, CSSProperties>),
  });

  return (
    <Box pt={theme.spacing(2)}>
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
    </Box>
  );
};

export default RelatedInjectsTab;
