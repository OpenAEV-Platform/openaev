import { HubOutlined } from '@mui/icons-material';
import { Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useState } from 'react';
import { Link } from 'react-router';

import { searchFindingsAlsoDetectedOn } from '../../../actions/findings/finding-actions';
import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Header } from '../../../components/common/SortHeadersList';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { FINDING_BASE_URL } from '../../../constants/BaseUrls';
import type { Finding, FilterGroup, FindingSiblingOutput, SearchPaginationInput, TargetSimple } from '../../../utils/api-types';
import ContractOutputElementType from './ContractOutputElementType';

interface Props { finding: Pick<Finding, 'finding_id' | 'finding_type' | 'finding_value'> }

/**
 * "Also Detected On" panel (finding_triforce_design.md, Task 1): lists every OTHER Finding sharing
 * the same Type + Value as {@link finding}, one row per sibling Location, so a user can navigate
 * from one occurrence of an identical check to its siblings on other assets. Archived siblings are
 * always included (Decision #10), flagged with an "Archived" chip rather than hidden - consistent
 * with the platform's "nothing is ever silently dropped" principle for findings.
 */
const AlsoDetectedOnPanel = ({ finding }: Props) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);
  const [siblings, setSiblings] = useState<FindingSiblingOutput[]>([]);

  const availableFilterNames = ['finding_created_at', 'finding_updated_at'];

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
  } = useQueryableWithLocalStorage(`also-detected-on-${finding.finding_id}`, buildSearchPagination({
    // Last seen first: the most recently re-detected sibling location is the most relevant one.
    sorts: initSorting('finding_updated_at', 'DESC'),
    filterGroup: baseFilter,
  }));

  const searchAlsoDetectedOn = (input: SearchPaginationInput): Promise<{ data: Page<FindingSiblingOutput> }> => {
    setLoading(true);
    return searchFindingsAlsoDetectedOn(finding.finding_id, input).finally(() => setLoading(false));
  };

  const headers: Header[] = [
    {
      field: 'finding_location',
      label: 'Location',
      isSortable: false,
      value: (sibling: FindingSiblingOutput) => (
        <ItemTargets
          targets={sibling.finding_location
            ? [{
                target_id: sibling.finding_location.asset_id,
                target_name: sibling.finding_location.asset_name,
                target_type: 'ASSETS',
                target_category: sibling.finding_location.asset_category,
                target_subtype: sibling.finding_location.endpoint_platform,
              } as TargetSimple]
            : []}
          getTargetLink={target => `/admin/assets/endpoints/${target.target_id}`}
        />
      ),
    },
    {
      field: 'finding_archived',
      label: 'Status',
      isSortable: false,
      value: (sibling: FindingSiblingOutput) => (
        sibling.finding_archived
          ? <Chip label={t('Archived')} size="small" variant="outlined" />
          : <Chip label={t('Active')} size="small" color="success" variant="outlined" />
      ),
    },
    {
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (sibling: FindingSiblingOutput) => <>{nsdt(sibling.finding_created_at)}</>,
    },
    {
      field: 'finding_updated_at',
      label: 'Last seen',
      isSortable: true,
      value: (sibling: FindingSiblingOutput) => <>{nsdt(sibling.finding_updated_at)}</>,
    },
  ];

  const inlineStyles: Record<string, CSSProperties> = {
    finding_location: { width: '34%' },
    finding_archived: { width: '16%' },
    finding_created_at: { width: '25%' },
    finding_updated_at: { width: '25%' },
  };

  return (
    <div style={{ padding: theme.spacing(0, 1, 0, 0) }}>
      <PaginationComponentV2
        fetch={searchAlsoDetectedOn}
        searchPaginationInput={searchPaginationInput}
        setContent={setSiblings}
        entityPrefix="finding"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
      />
      <List>
        <ListItem style={{ paddingTop: 0 }}>
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
          ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} />
          : siblings.map(sibling => (
              <ListItem key={sibling.finding_id} divider disablePadding>
                <ListItemButton component={Link} to={`${FINDING_BASE_URL}/${sibling.finding_id}`}>
                  <ListItemIcon>
                    <FindingIcon findingType={sibling.finding_type} />
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
                            {header.value && header.value(sibling)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
    </div>
  );
};

export default AlsoDetectedOnPanel;
