import { PermIdentityOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import Breadcrumbs from '../../../../../components/Breadcrumbs';
import PaginatedList from '../../../../../components/common/list/PaginatedList';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import { type UserOutput } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import UsersCapabilitiesMenu from '../UsersCapabilitiesMenu';
import usePlatformUsers from './hooks/usePlatformUsers';
import PlatformUserCreate from './PlatformUserCreate';
import PlatformUserPopover from './PlatformUserPopover';
import {
  ENTITY_PLATFORM_USER_PREFIX,
  getPlatformUserHeaders,
  LOCAL_STORAGE_KEY_PLATFORM_USER,
  PLATFORM_USER_FILTERS,
  PLATFORM_USER_INLINE_STYLES,
  PLATFORM_USER_SORTS,
} from './platformUsers.queryable';

const useStyles = makeStyles()(() => ({
  container: { display: 'flex' },
  bodyItems: { flexGrow: 1 },
}));

const PlatformUsers = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const {
    platformUsers,
    setPlatformUserList,
    loading,
    fetchPlatformUsers,
    addPlatformUser,
    updatePlatformUserInList,
    removePlatformUser,
  } = usePlatformUsers();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_PLATFORM_USER, buildSearchPagination({ sorts: PLATFORM_USER_SORTS }));
  const headers = useMemo(() => getPlatformUserHeaders(t), [t]);

  return (
    <div className={classes.container}>
      <div className={classes.bodyItems}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Platform') }, { label: t('Users & capabilities') }, {
            label: t('Users'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={fetchPlatformUsers}
          searchPaginationInput={searchPaginationInput}
          setContent={setPlatformUserList}
          entityPrefix={ENTITY_PLATFORM_USER_PREFIX}
          availableFilterNames={PLATFORM_USER_FILTERS}
          queryableHelpers={queryableHelpers}
        />
        <List>
          <ListItem
            divider={false}
            secondaryAction={<>&nbsp;</>}
            style={{ paddingTop: 0 }}
          >
            <ListItemIcon />
            <ListItemText
              style={{ textTransform: 'uppercase' }}
              primary={(
                <SortHeadersComponentV2
                  headers={headers}
                  sortHelpers={queryableHelpers.sortHelpers}
                  inlineStylesHeaders={PLATFORM_USER_INLINE_STYLES}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={PermIdentityOutlined} headers={headers} headerStyles={PLATFORM_USER_INLINE_STYLES} />
            : (
                <PaginatedList<UserOutput>
                  Icon={PermIdentityOutlined}
                  secondaryAction={user => (
                    <PlatformUserPopover
                      inList
                      platformUser={user}
                      actions={['Update', 'Delete']}
                      onUpdate={updatePlatformUserInList}
                      onDelete={removePlatformUser}
                    />
                  )}
                  headers={headers}
                  items={platformUsers}
                  rowKey="user_id"
                  itemWidth={PLATFORM_USER_INLINE_STYLES}
                />
              )}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_GROUPS_AND_ROLES}>
          <PlatformUserCreate onCreate={addPlatformUser} />
        </Can>
      </div>
      <UsersCapabilitiesMenu />
    </div>
  );
};

export default PlatformUsers;
