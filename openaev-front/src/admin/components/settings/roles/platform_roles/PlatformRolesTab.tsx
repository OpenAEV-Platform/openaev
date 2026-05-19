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
import type { PlatformRoleOutput } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import usePlatformRoles from './hooks/usePlatformRoles';
import PlatformRoleCreate from './PlatformRoleCreate';
import PlatformRolePopover from './PlatformRolePopover';
import {
  ENTITY_PLATFORM_ROLE_PREFIX,
  getPlatformRoleHeaders,
  LOCAL_STORAGE_KEY_PLATFORM_ROLE, PLATFORM_ROLE_FILTERS, PLATFORM_ROLE_INLINE_STYLES,
  PLATFORM_ROLE_SORTS,
} from './platformRoles.queryable';

const PlatformRolesTab = () => {
  const { t } = useFormatter();

  const {
    platformRoles,
    setPlatformRoleList,
    loading,
    fetchPlatformRoles,
    addPlatformRole,
    updatePlatformRoleInList,
    removePlatformRole,
  } = usePlatformRoles();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_PLATFORM_ROLE, buildSearchPagination({ sorts: PLATFORM_ROLE_SORTS }));
  const headers = useMemo(() => getPlatformRoleHeaders(t), [t]);

  return (
    <>
      <PaginationComponentV2
        fetch={fetchPlatformRoles}
        searchPaginationInput={searchPaginationInput}
        setContent={setPlatformRoleList}
        entityPrefix={ENTITY_PLATFORM_ROLE_PREFIX}
        availableFilterNames={PLATFORM_ROLE_FILTERS}
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
                inlineStylesHeaders={PLATFORM_ROLE_INLINE_STYLES}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={SecurityOutlined} headers={headers} headerStyles={PLATFORM_ROLE_INLINE_STYLES} />
          : (
              <PaginatedList<PlatformRoleOutput>
                Icon={SecurityOutlined}
                secondaryAction={role => (
                  <PlatformRolePopover
                    inList
                    platformRole={role}
                    actions={['Update', 'Delete']}
                    onUpdate={updatePlatformRoleInList}
                    onDelete={removePlatformRole}
                  />
                )}
                headers={headers}
                items={platformRoles}
                rowKey="platform_role_id"
                itemWidth={PLATFORM_ROLE_INLINE_STYLES}
              />
            )}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES}>
        <PlatformRoleCreate onCreate={addPlatformRole} />
      </Can>
    </>
  );
};

export default PlatformRolesTab;
