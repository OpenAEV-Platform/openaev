import { HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import {type AssetOutput, CredentialOutput} from '../../../../utils/api-types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import AssetCategoryIcon from '../AssetCategoryIcon';
import {CredentialPopoverProps} from "./CredentialPopover";

// Header labels are still rendered without sort handles when no sortHelpers
// are provided (client-side lists).
const NOOP_SORT_HELPERS: SortHelpers = {
  handleSort: () => {},
  handleDirectedSort: () => {},
  getSortBy: () => '',
  getSortAsc: () => true,
};

const useStyles = makeStyles()(() => ({
  item: { height: 50 },
  bodyItem: {
    fontSize: 13,
    float: 'left',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  typeChip: {
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 100,
    marginBottom: 5,
  },
}));

interface Props<T extends CredentialOutput> {
  credentials: T[];
  renderActions: ((credential: T) => ReactElement<CredentialPopoverProps>);
  loading?: boolean;
  compact?: boolean;
  /** Render a column headers row above the list. */
  withHeaders?: boolean;
  /** Enables clickable column sorting (pass `queryableHelpers.sortHelpers`). */
  sortHelpers?: SortHelpers;
}

const CredentialsList = <T extends CredentialOutput>({
  credentials,
  renderActions,
  loading = false,
  compact = false,
  withHeaders = false,
  sortHelpers,
}: Props<T>) => {
  // Standard hooks
  const { classes } = useStyles();

  const component = (credential: T) => {
    return renderActions(credential);
  };

  const inlineStyles: Record<string, CSSProperties> = {
    credential_name: { width: compact ? '20%' : '30%' },
    credential_type: { width: '10%' },
    credential_auth_method: { width: '10%' },
    credential_status: { width: '10%' },
    credential_created_by: { width: '10%' },
    credential_created_at: { width: '10%' },
    credential_last_verified_at: { width: '10%' },
    credential_tags_ids: { width: '10%' },
  };

  const headers = [
    {
      field: 'credential_name',
      label: 'Name',
      isSortable: true,
      value: (credential: T) => credential.credential_name,
    },
    {
      field: 'credential_type',
      label: 'Type',
      isSortable: true,
      value: (credential: T) => credential.credential_type,
    },
    {
      field: 'credential_auth_method',
      label: 'Auth Method',
      isSortable: true,
      value: (credential: T) => credential.credential_auth_method,
    },
    {
      field: 'credential_status',
      label: 'Status',
      isSortable: true,
      value: (credential: T) => credential.credential_status,
    },
    {
      field: 'credential_created_by',
      label: 'Created by',
      isSortable: true,
      value: (credential: T) => credential.credential_created_by?.user_name,
    },
    {
      field: 'credential_created_at',
      label: 'Created',
      isSortable: true,
      value: (credential: T) => credential.credential_created_at,
    },
    {
      field: 'credential_last_verified_at',
      label: 'Last verified',
      isSortable: false,
      value: (credential: T) => credential.credential_last_verified_at,
    },
    {
      field: 'credential_tags_ids',
      label: 'Tags',
      isSortable: false,
      value: (credential: T) => <ItemTags variant="list" tags={credential.credential_tags_ids ?? []} />,
    },
  ];

  const headersRow = withHeaders && (
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
              isSortable: header.isSortable && !!sortHelpers,
            }))}
            inlineStylesHeaders={inlineStyles}
            sortHelpers={sortHelpers ?? NOOP_SORT_HELPERS}
          />
        )}
      />
    </ListItem>
  );

  if (loading) {
    return (
      <>
        {headersRow && <List>{headersRow}</List>}
        <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
      </>
    );
  }
  if (credentials == undefined || credentials?.length == 0) {
    return null;
  }
  return (
    <List>
      {headersRow}
      { credentials?.map((credential) => {
        return (
          <ListItem
            key={credential.credential_id}
            classes={{ root: classes.item }}
            divider={true}
            secondaryAction={component(credential)}
          >
            <ListItemIcon>
              <AssetCategoryIcon scope="credential"
                                 category={credential.credential_type ?? null}
                                 color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <>
                  {headers.map(header => (
                    <div
                      key={header.field}
                      className={classes.bodyItem}
                      style={inlineStyles[header.field]}
                    >
                      {header.value(credential)}
                    </div>
                  ))}
                </>
              )}
            />
          </ListItem>
        );
      })}
    </List>
  );
};

export default CredentialsList;
