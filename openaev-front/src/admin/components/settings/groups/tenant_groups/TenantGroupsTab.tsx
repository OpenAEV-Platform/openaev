import { GroupsOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';

import PaginatedList from '../../../../../components/common/list/PaginatedList';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import type { Group } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import CreateTenantGroup from './CreateTenantGroup';
import GroupPopover from './GroupPopover';
import useTenantGroups from './hooks/useTenantGroups';
import {
  ENTITY_TENANT_GROUP_PREFIX,
  getTenantGroupHeaders,
  LOCAL_STORAGE_KEY_TENANT_GROUP,
  TENANT_GROUP_FILTERS,
  TENANT_GROUP_INLINE_STYLES,
  TENANT_GROUP_SORTS,
} from './tenantGroups.queryable';

const TenantGroupsTab = () => {
  const { t } = useFormatter();

  const {
    groups,
    setGroupList,
    loading,
    fetchGroups,
    addGroup,
    updateGroupInList,
    removeGroup,
  } = useTenantGroups();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT_GROUP, buildSearchPagination({ sorts: TENANT_GROUP_SORTS }));
  const headers = useMemo(() => getTenantGroupHeaders(t), [t]);

  return (
    <>
      <PaginationComponentV2
        fetch={fetchGroups}
        searchPaginationInput={searchPaginationInput}
        setContent={setGroupList}
        entityPrefix={ENTITY_TENANT_GROUP_PREFIX}
        availableFilterNames={TENANT_GROUP_FILTERS}
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
                inlineStylesHeaders={TENANT_GROUP_INLINE_STYLES}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={GroupsOutlined} headers={headers} headerStyles={TENANT_GROUP_INLINE_STYLES} />
          : (
              <PaginatedList<Group>
                Icon={GroupsOutlined}
                secondaryAction={group => (
                  <GroupPopover
                    group={group}
                    groupUsersIds={group.group_users ?? []}
                    groupRolesIds={group.group_roles ?? []}
                    onUpdate={updateGroupInList}
                    onDelete={removeGroup}
                  />
                )}
                headers={headers}
                items={groups}
                rowKey="group_id"
                itemWidth={TENANT_GROUP_INLINE_STYLES}
              />
            )}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
        <CreateTenantGroup onCreate={addGroup} />
      </Can>
    </>
  );
};

export default TenantGroupsTab;
