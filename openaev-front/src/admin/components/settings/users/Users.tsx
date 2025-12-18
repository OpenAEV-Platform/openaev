import { CheckCircleOutlined, PersonOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchOrganizations } from '../../../../actions/Organization';
import { searchUsers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { type User, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import SecurityMenu from '../SecurityMenu';
import CreateUser from './CreateUser';
import UserPopover from './UserPopover';

const useStyles = makeStyles()(() => ({
  container: {
    margin: 0,
    padding: '0 200px 50px 0',
  },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItems: {
    display: 'flex',
    alignItems: 'center',
  },
  bodyItem: {
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    paddingRight: 10,
  },
}));

const inlineStyles = {
  user_email: { width: '20%' },
  user_firstname: { width: '15%' },
  user_lastname: { width: '15%' },
  user_organization: {
    width: '15%',
    cursor: 'default',
  },
  user_admin: { width: '10%' },
  user_tags: { width: '25%' },
};

const Users = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  useDataLoader(() => {
    dispatch(fetchOrganizations());
  });

  // Headers
  const headers = [
    {
      field: 'user_email',
      label: 'Email address',
      isSortable: true,
    },
    {
      field: 'user_firstname',
      label: 'Firstname',
      isSortable: true,
    },
    {
      field: 'user_lastname',
      label: 'Lastname',
      isSortable: true,
    },
    {
      field: 'user_organization',
      label: 'Organization',
      isSortable: false,
    },
    {
      field: 'user_admin',
      label: 'Administrator',
      isSortable: true,
    },
    {
      field: 'user_tags',
      label: 'Tags',
      isSortable: true,
    },
  ];

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const [users, setUsers] = useState<UserOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('users', buildSearchPagination({
    sorts: initSorting('user_firstname'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'tags',
    exportKeys: [
      'user_email',
      'user_firstname',
      'user_lastname',
    ],
    exportData: users,
    exportFileName: `${t('Users')}.csv`,
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, {
            label: t('Users'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          disableFilters
          fetch={searchUsers}
          searchPaginationInput={searchPaginationInput}
          setContent={setUsers}
          entityPrefix="user"
          queryableHelpers={queryableHelpers}
          topBarButtons={
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
          }
        />
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon>
              <span
                style={{
                  padding: '0 8px 0 8px',
                  fontWeight: 700,
                  fontSize: 12,
                }}
              >
              &nbsp;
              </span>
            </ListItemIcon>
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
          {users.map(user => (
            <ListItem
              key={user.user_id}
              classes={{ root: classes.item }}
              divider={true}
              secondaryAction={(
                <UserPopover
                  user={user}
                  onUpdate={(result: User) => setUsers(users.map(u => (u.user_id !== result.user_id ? u : result)))}
                  onDelete={(result: string) => setUsers(users.filter(u => (u.user_id !== result)))}
                />
              )}
            >
              <ListItemIcon>
                <PersonOutlined color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div className={classes.bodyItems}>
                    <div className={classes.bodyItem} style={inlineStyles.user_email}>
                      {user.user_email}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_firstname}>
                      {user.user_firstname}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_lastname}>
                      {user.user_lastname}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_organization}>
                      {user.user_organization_name}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_admin}>
                      {user.user_admin ? (<CheckCircleOutlined fontSize="small" />) : ('-')}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_tags}>
                      <ItemTags variant="list" tags={user.user_tags} />
                    </div>
                  </div>
                )}
              />
            </ListItem>
          ))}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateUser
            onCreate={(result: User) => setUsers([result, ...users])}
          />
        </Can>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Users;
