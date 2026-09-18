import { HubOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useEffect, useState } from 'react';

import { fetchStableFindingLocations } from '../../../actions/findings/finding-actions';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import type { Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import type { FindingLocationOutput, FindingOutput } from '../../../utils/api-types';

interface Props { finding: Pick<FindingOutput, 'finding_id'> }

/**
 * Lists the distinct locations consolidated under the stable Finding. Location belongs to an
 * occurrence, so rows here are facets of the current Finding rather than links to sibling
 * identities.
 */
const AlsoDetectedOnPanel = ({ finding }: Props) => {
  const theme = useTheme();
  const { nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState(true);
  const [locations, setLocations] = useState<FindingLocationOutput[]>([]);

  useEffect(() => {
    setLoading(true);
    fetchStableFindingLocations(finding.finding_id)
      .then(response => setLocations(response.data))
      .finally(() => setLoading(false));
  }, [finding.finding_id]);

  const headers: Header[] = [
    {
      field: 'finding_location',
      label: 'Asset',
      isSortable: false,
      value: (location: FindingLocationOutput) => (
        <>{location.finding_location ?? location.finding_location_key ?? '-'}</>
      ),
    },
    {
      field: 'finding_location_occurrences',
      label: 'Occurrences',
      isSortable: false,
      value: (location: FindingLocationOutput) => <>{location.finding_location_occurrences}</>,
    },
    {
      field: 'finding_location_first_seen',
      label: 'First seen',
      isSortable: false,
      value: (location: FindingLocationOutput) => <>{nsdt(location.finding_location_first_seen)}</>,
    },
    {
      field: 'finding_location_last_seen',
      label: 'Last seen',
      isSortable: false,
      value: (location: FindingLocationOutput) => <>{nsdt(location.finding_location_last_seen)}</>,
    },
  ];

  const inlineStyles: Record<string, CSSProperties> = {
    finding_location: { width: '40%' },
    finding_location_occurrences: { width: '15%' },
    finding_location_first_seen: { width: '22.5%' },
    finding_location_last_seen: { width: '22.5%' },
  };

  return (
    <div style={{ padding: theme.spacing(0, 1, 0, 0) }}>
      <List>
        <ListItem style={{ paddingTop: 0 }}>
          <ListItemIcon />
          <ListItemText
            primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {headers.map(header => (
                  <div
                    key={header.field}
                    style={{
                      ...bodyItemsStyles.bodyItem,
                      ...inlineStyles[header.field],
                      fontWeight: 600,
                    }}
                  >
                    {header.label}
                  </div>
                ))}
              </div>
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} />
          : locations.map(location => (
              <ListItem
                key={`${location.finding_location_type ?? 'informative'}:${location.finding_location_key ?? ''}`}
                divider
              >
                <ListItemIcon>
                  <HubOutlined />
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
                          {header.value && header.value(location)}
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

export default AlsoDetectedOnPanel;
