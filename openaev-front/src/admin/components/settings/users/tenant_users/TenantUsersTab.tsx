import { PersonOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';

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
import CreateUser from './CreateUser';
import useTenantUsers from './hooks/useTenantUsers';
import {
  ENTITY_TENANT_USER_PREFIX,
  getTenantUserHeaders,
  LOCAL_STORAGE_KEY_TENANT_USER,
  TENANT_USER_FILTERS, TENANT_USER_INLINE_STYLES,
  TENANT_USER_SORTS,
} from './tenantUsers.queryable';
import UserPopover from './UserPopover';

const TenantUsersTab = () => {
  const { t } = useFormatter();

  const {
    users,
    setUserList,
    loading,
    fetchUsers,
    addUser,
    editUser,
    removeUser,
  } = useTenantUsers();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT_USER, buildSearchPagination({ sorts: TENANT_USER_SORTS }));
  const headers = useMemo(() => getTenantUserHeaders(t), [t]);

  return (
    <>
      <PaginationComponentV2
        fetch={fetchUsers}
        searchPaginationInput={searchPaginationInput}
        setContent={setUserList}
        entityPrefix={ENTITY_TENANT_USER_PREFIX}
        availableFilterNames={TENANT_USER_FILTERS}
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
                inlineStylesHeaders={TENANT_USER_INLINE_STYLES}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={PersonOutlined} headers={headers} headerStyles={TENANT_USER_INLINE_STYLES} />
          : (
              <PaginatedList<UserOutput>
                Icon={PersonOutlined}
                secondaryAction={user => (
                  <UserPopover
                    user={user}
                    actions={['Update', 'Delete']}
                    onSubmitUpdate={data => editUser(user.user_id, data)}
                    onSubmitDelete={() => removeUser(user.user_id)}
                    permissions={{
                      manage: [ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS],
                      delete: [ACTIONS.DELETE, SUBJECTS.TENANT_SETTINGS],
                    }}
                    inList
                  />
                )}
                headers={headers}
                items={users}
                rowKey="user_id"
                itemWidth={TENANT_USER_INLINE_STYLES}
              />
            )}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
        <CreateUser onCreate={addUser} />
      </Can>
    </>
  );
};

export default TenantUsersTab;
