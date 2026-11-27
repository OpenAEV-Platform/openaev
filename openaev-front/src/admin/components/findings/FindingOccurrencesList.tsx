import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { type CSSProperties, useState } from 'react';

import { searchFindingOccurrences } from '../../../actions/findings/finding-actions';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import type { FindingOccurrenceOutput, FindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import FindingContextLink from './FindingContextLink';

interface Props {
  findingId: string;
  findingType: FindingOutput['finding_type'];
  contextId?: string;
}

const inlineStyles: Record<string, CSSProperties> = ({
  finding_occurrence_inject: { width: '32%' },
  finding_occurrence_context: { width: '24%' },
  finding_occurrence_field: { width: '22%' },
  finding_occurrence_date: { width: '22%' },
});

// One row per occurrence: the inject that detected the finding, its execution context
// (simulation / scenario / atomic), the output field it came from, and when it was seen.
const FindingOccurrencesList = ({ findingId, findingType, contextId }: Props) => {
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);
  const [occurrences, setOccurrences] = useState<FindingOccurrenceOutput[]>([]);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`finding-occurrences-${findingId}`, buildSearchPagination({
    // Most recent occurrence first: it tells whether the finding is still alive.
    sorts: initSorting('finding_occurrence_date', 'DESC'),
  }));

  const searchOccurrencesToLoad = (input: SearchPaginationInput): Promise<{ data: Page<FindingOccurrenceOutput> }> => {
    setLoading(true);
    return searchFindingOccurrences(findingId, input).finally(() => {
      setLoading(false);
    });
  };

  const headers = [
    {
      field: 'finding_occurrence_inject',
      label: 'Inject',
      isSortable: false,
      value: (occurrence: FindingOccurrenceOutput) => <FindingContextLink occurrence={occurrence} type={INJECT} />,
    },
    {
      field: 'finding_occurrence_context',
      label: 'Context',
      isSortable: false,
      value: (occurrence: FindingOccurrenceOutput) => {
        if (occurrence.finding_occurrence_simulation) {
          return <FindingContextLink occurrence={occurrence} type={SIMULATION} />;
        }
        if (occurrence.finding_occurrence_scenario) {
          return <FindingContextLink occurrence={occurrence} type={SCENARIO} />;
        }
        if (!occurrence.finding_occurrence_inject) {
          return '-';
        }
        return (
          <Typography variant="body2" color="textSecondary" component="span">
            {t('Atomic testing')}
          </Typography>
        );
      },
    },
    {
      field: 'finding_occurrence_field',
      label: 'Field',
      isSortable: false,
      value: (occurrence: FindingOccurrenceOutput) => <>{emptyFilled(occurrence.finding_occurrence_field)}</>,
    },
    {
      field: 'finding_occurrence_date',
      label: 'Seen',
      isSortable: true,
      value: (occurrence: FindingOccurrenceOutput) => <>{nsdt(occurrence.finding_occurrence_date)}</>,
    },
  ];

  return (
    <div>
      <PaginationComponentV2
        fetch={searchOccurrencesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setOccurrences}
        queryableHelpers={queryableHelpers}
        contextId={contextId}
        searchEnable={false}
        disableFilters
      />
      <List>
        <ListItem sx={{
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
        {loading ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} /> : occurrences.map(occurrence => (
          <ListItem
            key={occurrence.finding_occurrence_id}
            sx={{ height: 50 }}
            divider
          >
            <ListItemIcon>
              <FindingIcon findingType={findingType} tooltip />
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
                      {header.value(occurrence)}
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

export default FindingOccurrencesList;
