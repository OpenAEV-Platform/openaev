import { PersonOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo } from 'react';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import PaginatedList from '../../../../components/common/list/PaginatedList';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import type { UserOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import SecurityMenu from '../SecurityMenu';
import CreateUser from './CreateUser';
import UserPopover from './UserPopover';
import useTenantUsers from './hooks/useTenantUsers';
import {
  ENTITY_TENANT_USER_PREFIX,
  FIELD_EMAIL,
  FIELD_FIRSTNAME,
  FIELD_LASTNAME,
  FIELD_ORGANIZATION,
  FIELD_TAGS,
  getTenantUserHeaders,
  LOCAL_STORAGE_KEY_TENANT_USER,
  TENANT_USER_FILTERS,
  TENANT_USER_SORTS,
} from './tenantUsers.queryable';

const Users = () => {
  const { t } = useFormatter();

  const {
    tenantUsers,
    setTenantUserList,
    loading,
    fetchTenantUsers,
    addTenantUser,
    updateTenantUserInList,
    removeTenantUser,
  } = useTenantUsers();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT_USER, buildSearchPagination({ sorts: TENANT_USER_SORTS }));

  const headers = useMemo(() => getTenantUserHeaders(t), [t]);

  const itemWidth: Record<string, CSSProperties> = {
    [FIELD_EMAIL]: { width: '20%' },
    [FIELD_FIRSTNAME]: { width: '15%' },
    [FIELD_LASTNAME]: { width: '15%' },
    [FIELD_ORGANIZATION]: { width: '20%' },
    [FIELD_TAGS]: { width: '30%' },
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
          fetch={fetchTenantUsers}
          searchPaginationInput={searchPaginationInput}
          setContent={setTenantUserList}
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
                  inlineStylesHeaders={itemWidth}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={PersonOutlined} headers={headers} headerStyles={itemWidth} />
            : (
              <PaginatedList<UserOutput>
                Icon={PersonOutlined}
                secondaryAction={user => (
                  <UserPopover
                    inList
                    user={user}
                    actions={['Update', 'Delete']}
                    onUpdate={updateTenantUserInList}
                    onDelete={removeTenantUser}
                  />
                )}
                headers={headers}
                items={tenantUsers}
                rowKey="user_id"
                itemWidth={itemWidth}
              />
            )}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateUser onCreate={addTenantUser} />
        </Can>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Users;
