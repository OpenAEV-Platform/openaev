import { CheckCircleOutlined, GroupsOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { useState } from 'react';
import { useDispatch } from 'react-redux';
import { makeStyles } from 'tss-react/mui';

import { fetchExercises } from '../../../../actions/Exercise';
import { searchGroups } from '../../../../actions/Group';
import { fetchOrganizations } from '../../../../actions/Organization';
import { fetchRoles } from '../../../../actions/roles/roles-actions.js';
import { fetchScenarios } from '../../../../actions/scenarios/scenario-actions';
import { fetchUsers } from '../../../../actions/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store.js';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import SecurityMenu from '../SecurityMenu';
import CreateGroup from './CreateGroup';
import GroupPopover from './GroupPopover';

const useStyles = makeStyles()(() => ({
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
  group_name: { width: '20%' },
  group_users_number: {
    width: '10%',
    cursor: 'default',
  },
  group_roles: {
    width: '20%',
    cursor: 'default',
  },
  group_grants: {
    width: '20%',
    cursor: 'default',
  },
  group_update_date: {
    width: '20%',
    cursor: 'default',
  },
};

const Groups = () => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useDispatch();
  const { t } = useFormatter();

  const roles = useHelper(helper => helper.getRoles());
  const rolesMap = roles.reduce((acc, r) => {
    if (r?.role_id && r?.role_name) acc[r.role_id] = r.role_name;
    return acc;
  }, {});

  useDataLoader(() => {
    dispatch(fetchOrganizations());
    dispatch(fetchUsers());
    dispatch(fetchRoles());
    dispatch(fetchExercises());
    dispatch(fetchScenarios());
  });

  // Headers
  const headers = [
    {
      field: 'group_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'group_users_number',
      label: 'Users',
      isSortable: false,
      value: 0,
    },
    {
      field: 'group_roles',
      label: 'Roles',
    },
    {
      field: 'group_grants',
      label: 'Grants',
    },
  ];

  const [groups, setGroups] = useState([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState({ sorts: initSorting('group_name') });

  // Export
  const exportProps = {
    exportType: 'tags',
    exportKeys: [
      'group_name',
      'group_users_number',
    ],
    exportData: groups,
    exportFileName: `${t('Groups')}.csv`,
  };

  return (
    <div style={{
      display: 'flex',
      overflow: 'auto',
    }}
    >
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, {
            label: t('Groups'),
            current: true,
          }]}
        />
        <PaginationComponent
          fetch={searchGroups}
          searchPaginationInput={searchPaginationInput}
          setContent={setGroups}
          exportProps={exportProps}
        />
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
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
                <SortHeadersComponent
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  searchPaginationInput={searchPaginationInput}
                  setSearchPaginationInput={setSearchPaginationInput}
                />
              )}
            />
            <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
          </ListItem>
          {groups.map(group => (
            <ListItem
              key={group.group_id}
              classes={{ root: classes.item }}
              divider={true}
              secondaryAction={(
                <GroupPopover
                  group={group}
                  groupUsersIds={group.group_users}
                  groupRolesIds={group.group_roles}
                  onUpdate={result => setGroups(groups.map(g => (g.group_id !== result.group_id ? g : result)))}
                  onDelete={result => setGroups(groups.filter(g => (g.group_id !== result)))}
                />
              )}
            >
              <ListItemIcon>
                <GroupsOutlined color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div className={classes.bodyItems}>
                    <div className={classes.bodyItem} style={inlineStyles.group_name}>
                      {group.group_name}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.group_users_number}>
                      {group.group_users.length}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.group_roles}>
                      {(() => {
                        const ids = group.group_roles ?? [];
                        if (!ids.length) return '-';

                        const names = ids
                          .map(id =>
                            rolesMap[id],
                          )
                          .filter(Boolean);

                        const shown = names.slice(0, 10);
                        const hasMore = names.length > 10;

                        return (
                          <span>
                            {shown.join(', ')}
                            {hasMore ? '…' : ''}
                          </span>
                        );
                      })()}
                    </div>
                    <div className={classes.bodyItem} style={inlineStyles.group_grants}>
                      {group.group_grants.length > 0 ? (<CheckCircleOutlined fontSize="small" />) : ('-')}
                    </div>
                  </div>
                )}
              />

            </ListItem>
          ))}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateGroup
            onCreate={result => setGroups([result, ...groups])}
          />
        </Can>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Groups;
