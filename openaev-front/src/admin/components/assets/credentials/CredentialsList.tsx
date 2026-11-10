import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type ReactElement } from 'react';

import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import ItemTags from '../../../../components/ItemTags';
import { type CredentialOutput } from '../../../../utils/api-types';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import { type CredentialPopoverProps } from './CredentialPopover';
import CredentialStatusChip from './CredentialStatusChip';

// The list is client-side: header labels are rendered without sort handles.
const NOOP_SORT_HELPERS: SortHelpers = {
  handleSort: () => {},
  handleDirectedSort: () => {},
  getSortBy: () => '',
  getSortAsc: () => true,
};

const inlineStyles: Record<string, CSSProperties> = {
  credential_name: { width: '30%' },
  credential_type: { width: '15%' },
  credential_auth_method: { width: '20%' },
  credential_status: { width: '15%' },
  credential_tags_ids: { width: '20%' },
};

interface Props<T extends CredentialOutput> {
  credentials: T[];
  renderActions: ((credential: T) => ReactElement<CredentialPopoverProps>);
  /** Render a column headers row above the list. */
  withHeaders?: boolean;
}

const CredentialsList = <T extends CredentialOutput>({
  credentials,
  renderActions,
  withHeaders = false,
}: Props<T>) => {
  // Standard hooks
  const bodyItemsStyles = useBodyItemsStyles();

  const headers = [
    {
      field: 'credential_name',
      label: 'Name',
      value: (credential: T) => credential.credential_name,
    },
    {
      field: 'credential_type',
      label: 'Type',
      value: (credential: T) => credential.credential_type ? humanizeEnum(credential.credential_type) : '-',
    },
    {
      field: 'credential_auth_method',
      label: 'Auth Method',
      value: (credential: T) => credential.credential_auth_method ? humanizeEnum(credential.credential_auth_method) : '-',
    },
    {
      field: 'credential_status',
      label: 'Status',
      value: (credential: T) => <CredentialStatusChip status={credential.credential_status} variant="list" />,
    },
    {
      field: 'credential_tags_ids',
      label: 'Tags',
      value: (credential: T) => <ItemTags variant="list" limit={1} tags={credential.credential_tags_ids ?? []} />,
    },
  ];

  if (!credentials?.length) {
    return null;
  }
  return (
    <List>
      {withHeaders && (
        <ListItem
          dense
          divider
          secondaryAction={<span>&nbsp;</span>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers.map(header => ({
                  field: header.field,
                  label: header.label,
                  isSortable: false,
                }))}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={NOOP_SORT_HELPERS}
              />
            )}
          />
        </ListItem>
      )}
      {credentials.map(credential => (
        <ListItem
          key={credential.credential_id}
          divider
          secondaryAction={renderActions(credential)}
          sx={{ height: theme => theme.spacing(6.25) }}
        >
          <ListItemIcon>
            <AssetCategoryIcon
              scope="credential"
              category={credential.credential_type ?? null}
              color="primary"
            />
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
                    {header.value(credential)}
                  </div>
                ))}
              </div>
            )}
          />
        </ListItem>
      ))}
    </List>
  );
};

export default CredentialsList;
