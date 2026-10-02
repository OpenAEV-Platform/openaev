import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import type { FindingOutput, RelatedFindingOutput, SearchPaginationInput } from '../../../utils/api-types';
import FindingContextLink from './FindingContextLink';
import { buildOccurrencesFilter, occurrenceTargetLink, occurrenceTargets } from './FindingOccurrencesUtils';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: {
    'height': 50,
    // Slightly larger pivot chips (inject / context / targets) than the ultra-dense 20px table
    // default, so they read as tappable buttons without breaking the row rhythm.
    '& .MuiChip-root': { height: 24 },
  },
}));

interface Props {
  searchFindings: (input: SearchPaginationInput) => Promise<{ data: Page<RelatedFindingOutput> }>;
  finding: Pick<FindingOutput, 'finding_type' | 'finding_value'>;
  contextId?: string;
}

// One row per occurrence (= per inject): the inject leads, followed by its execution context
// (simulation / scenario / atomic) and a single merged targets column, closed by one "Seen" date -
// per-occurrence first/last seen collapse to the same detection event, so two date columns would
// just repeat each other.
const FindingOccurrencesList = ({ searchFindings, finding, contextId }: Props) => {
  const { classes } = useStyles();
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);

  const availableFilterNames = [
    'finding_created_at',
    'finding_updated_at',
    'finding_asset_groups',
    'finding_assets',
    'finding_inject_id',
    'finding_simulation',
    'finding_scenario',
  ];

  const [occurrences, setOccurrences] = useState<RelatedFindingOutput[]>([]);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`finding-occurrences-${finding.finding_type}-${finding.finding_value}-${contextId}`, buildSearchPagination({
    // Most recent occurrence first: it tells whether the finding is still alive.
    sorts: initSorting('finding_updated_at', 'DESC'),
    filterGroup: buildOccurrencesFilter(finding),
  }));

  const searchOccurrencesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchFindings(input).finally(() => {
      setLoading(false);
    });
  };

  const headers = [
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (occurrence: RelatedFindingOutput) => <FindingContextLink finding={occurrence} type={INJECT} />,
    },
    {
      field: 'finding_context',
      label: 'Context',
      isSortable: false,
      value: (occurrence: RelatedFindingOutput) => {
        if (occurrence.finding_simulation) {
          return <FindingContextLink finding={occurrence} type={SIMULATION} />;
        }
        if (occurrence.finding_scenario) {
          return <FindingContextLink finding={occurrence} type={SCENARIO} />;
        }
        return (
          <Typography variant="body2" color="textSecondary" component="span">
            {t('Atomic testing')}
          </Typography>
        );
      },
    },
    {
      field: 'finding_targets',
      label: 'Impacted targets',
      isSortable: false,
      value: (occurrence: RelatedFindingOutput) => (
        <ItemTargets
          targets={occurrenceTargets(occurrence)}
          getTargetLink={occurrenceTargetLink}
        />
      ),
    },
    {
      field: 'finding_updated_at',
      label: 'Seen',
      isSortable: true,
      value: (occurrence: RelatedFindingOutput) => <>{nsdt(occurrence.finding_updated_at)}</>,
    },
  ];

  const inlineStyles: Record<string, CSSProperties> = ({
    finding_inject: { width: '30%' },
    finding_context: { width: '22%' },
    finding_targets: { width: '30%' },
    finding_updated_at: { width: '18%' },
  });

  return (
    <div>
      <PaginationComponentV2
        fetch={searchOccurrencesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setOccurrences}
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
        {loading ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} /> : occurrences.map(occurrence => (
          <ListItem
            key={occurrence.finding_id}
            classes={{ root: classes.item }}
            divider
          >
            <ListItemIcon>
              <FindingIcon findingType={occurrence.finding_type} tooltip />
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
                      {header.value && header.value(occurrence)}
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
