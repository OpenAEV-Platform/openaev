import { CheckCircleOutlined, PersonOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { deleteUser, searchUsers } from '../../../../actions/users/User';
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
import { Can } from '../../../../utils/permissions/permissionsContext';
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
  user_firstname: { width: '12%' },
  user_lastname: { width: '12%' },
  user_admin: { width: '10%' },
  user_organization_name: { width: '18%' },
  user_tags: { width: '20%' },
};

const Users = () => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

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
      field: 'user_admin',
      label: 'Admin',
      isSortable: false,
    },
    {
      field: 'user_organization_name',
      label: 'Organization',
      isSortable: false,
    },
    {
      field: 'user_tags',
      label: 'Tags',
      isSortable: false,
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
      'user_admin',
      'user_organization_name',
      'user_tags',
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
                  actions={['Delete']}
                  onSubmitUpdate={() => {}}
                  onSubmitDelete={() => {
                    dispatch(deleteUser(user.user_id)).then(() => {
                      setUsers(users.filter(u => u.user_id !== user.user_id));
                    });
                  }}
                  permissions={{
                    manage: [ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS],
                    delete: [ACTIONS.DELETE, SUBJECTS.TENANT_SETTINGS],
                  }}
                  inList
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
                    <div className={classes.bodyItem} style={inlineStyles.user_admin}>
                      {user.user_admin ? (<CheckCircleOutlined fontSize="small" />) : ('-')}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.user_organization_name}>
                      {user.user_organization_name ?? '-'}
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
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
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
