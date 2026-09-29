import { SecurityOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';
import { useNavigate } from 'react-router';

import PaginatedList from '../../../../components/common/list/PaginatedList';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import type { RoleOutput } from '../../../../utils/api-types';
import { type CapabilityScope } from '../../../../utils/permissions/types';
import useRoles from './hooks/useRoles';
import RoleCreate from './RoleCreate';
import RolePopover from './RolePopover';
import { ENTITY_ROLE_PREFIX, getRoleHeaders, ROLE_FILTERS, ROLE_INLINE_STYLES, ROLE_SORTS } from './roles.queryable';
import { useRoleScope } from './RoleScopeContext';

const CREATE_TITLE = {
  TENANT: 'Create a new tenant role',
  PLATFORM: 'Create a new platform role',
} as const satisfies Record<CapabilityScope, string>;

const RolesTab = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { scope, storageKey, detailUrl } = useRoleScope();

  const {
    roles,
    setRoleList,
    loading,
    fetchRoles,
    addRole,
    updateRoleInList,
    removeRole,
  } = useRoles();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(storageKey, buildSearchPagination({ sorts: ROLE_SORTS }));
  const headers = useMemo(() => getRoleHeaders(t), [t]);

  return (
    <>
      <PaginationComponentV2
        fetch={fetchRoles}
        searchPaginationInput={searchPaginationInput}
        setContent={setRoleList}
        entityPrefix={ENTITY_ROLE_PREFIX}
        availableFilterNames={ROLE_FILTERS}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <RoleCreate title={CREATE_TITLE[scope]} onCreate={addRole} />
        )}
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
                inlineStylesHeaders={ROLE_INLINE_STYLES}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={SecurityOutlined} headers={headers} headerStyles={ROLE_INLINE_STYLES} />
          : (
              <PaginatedList<RoleOutput>
                Icon={SecurityOutlined}
                secondaryAction={role => (
                  <RolePopover
                    inList
                    role={role}
                    onUpdate={updateRoleInList}
                    onDelete={removeRole}
                  />
                )}
                headers={headers}
                items={roles}
                rowKey="role_id"
                onRowClick={role => navigate(detailUrl(role.role_id))}
                itemWidth={ROLE_INLINE_STYLES}
              />
            )}
      </List>
    </>
  );
};

export default RolesTab;
