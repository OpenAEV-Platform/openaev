import { SecurityOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';

import PaginatedList from '../../../../../components/common/list/PaginatedList';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import { type RoleOutput } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import CreateRole from './CreateRole';
import useTenantRoles from './hooks/useTenantRoles';
import RolePopover from './RolePopover';
import {
  ENTITY_TENANT_ROLE_PREFIX,
  getTenantRoleHeaders,
  LOCAL_STORAGE_KEY_TENANT_ROLE, TENANT_ROLE_FILTERS, TENANT_ROLE_INLINE_STYLES,
  TENANT_ROLE_SORTS,
} from './tenantRoles.queryable';

const TenantRolesTab = () => {
  const { t } = useFormatter();

  const {
    roles,
    setRoleList,
    loading,
    fetchRoles,
    addRole,
    updateRoleInList,
    removeRole,
  } = useTenantRoles();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT_ROLE, buildSearchPagination({ sorts: TENANT_ROLE_SORTS }));
  const headers = useMemo(() => getTenantRoleHeaders(t), [t]);

  return (
    <>
      <PaginationComponentV2
        fetch={fetchRoles}
        searchPaginationInput={searchPaginationInput}
        setContent={setRoleList}
        entityPrefix={ENTITY_TENANT_ROLE_PREFIX}
        availableFilterNames={TENANT_ROLE_FILTERS}
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
                inlineStylesHeaders={TENANT_ROLE_INLINE_STYLES}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={SecurityOutlined} headers={headers} headerStyles={TENANT_ROLE_INLINE_STYLES} />
          : (
              <PaginatedList<RoleOutput>
                Icon={SecurityOutlined}
                secondaryAction={role => (
                  <RolePopover
                    role={role}
                    onUpdate={updateRoleInList}
                    onDelete={removeRole}
                  />
                )}
                headers={headers}
                items={roles}
                rowKey="role_id"
                itemWidth={TENANT_ROLE_INLINE_STYLES}
              />
            )}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
        <CreateRole onCreate={addRole} />
      </Can>
    </>
  );
};

export default TenantRolesTab;
