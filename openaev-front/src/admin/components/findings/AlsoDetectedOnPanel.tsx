import { Button } from '@filigran/design-system';
import { HubOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { fetchStableFindingSiblings } from '../../../actions/findings/finding-actions';
import type { Page } from '../../../components/common/queryable/Page';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import type { FindingOutput } from '../../../utils/api-types';
import { type StableFindingRow } from './FindingRowUtils';

const PAGE_SIZE = 20;

interface Props { finding: Pick<FindingOutput, 'finding_id'> }

// Location as shown in the detail (US.4): the asset name, the user name or the cloud resource.
const locationLabel = (sibling: StableFindingRow) => sibling.finding_assets?.[0]?.asset_name
  ?? sibling.finding_location
  ?? sibling.finding_location_key
  ?? '-';

const headers = [
  {
    field: 'finding_location',
    label: 'Location',
    isSortable: false,
  },
  {
    field: 'finding_created_at',
    label: 'First seen',
    isSortable: false,
  },
  {
    field: 'finding_updated_at',
    label: 'Last seen',
    isSortable: false,
  },
];

const inlineStyles: Record<string, CSSProperties> = {
  finding_location: { width: '50%' },
  finding_created_at: { width: '25%' },
  finding_updated_at: { width: '25%' },
};

/**
 * "Also detected on" (US.4): the other findings of the tenant with the same type and value, each
 * at its own location, most recently seen first, 20 at a time. Each is its own finding, with its
 * own detection history: a click opens it.
 */
const AlsoDetectedOnPanel = ({ finding }: Props) => {
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [siblings, setSiblings] = useState<StableFindingRow[]>([]);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    setPage(0);
    setSiblings([]);
  }, [finding.finding_id]);

  useEffect(() => {
    setLoading(true);
    fetchStableFindingSiblings(finding.finding_id, page, PAGE_SIZE)
      .then((response: { data: Page<StableFindingRow> }) => {
        setSiblings(previous => (page === 0 ? response.data.content : [...previous, ...response.data.content]));
        setTotal(response.data.totalElements);
      })
      .finally(() => setLoading(false));
  }, [finding.finding_id, page]);

  if (!loading && siblings.length === 0) {
    return <Box sx={{ color: 'text.secondary' }}>{t('Not detected on any other location')}</Box>;
  }

  return (
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
                  {t(header.label)}
                </div>
              ))}
            </div>
          )}
        />
      </ListItem>
      {siblings.map(sibling => (
        <ListItem key={sibling.finding_id} divider disablePadding>
          <ListItemButton component={Link} to={`/admin/findings/${sibling.finding_id}`}>
            <ListItemIcon>
              <HubOutlined />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.finding_location,
                  }}
                  >
                    {locationLabel(sibling)}
                  </div>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.finding_created_at,
                  }}
                  >
                    {nsdt(sibling.finding_created_at)}
                  </div>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.finding_updated_at,
                  }}
                  >
                    {nsdt(sibling.finding_updated_at)}
                  </div>
                </div>
              )}
            />
          </ListItemButton>
        </ListItem>
      ))}
      {loading && <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} />}
      {!loading && siblings.length < total && (
        <Box sx={{ paddingTop: 1 }}>
          <Button priority="secondary" onClick={() => setPage(page + 1)}>
            {t('Load more')}
          </Button>
        </Box>
      )}
    </List>
  );
};

export default AlsoDetectedOnPanel;
