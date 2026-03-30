import { GroupsOutlined } from '@mui/icons-material';
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
import type { PlatformGroupOutput } from '../../../../../utils/api-types';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import UsersCapabilitiesMenu from '../UsersCapabilitiesMenu';
import usePlatformGroups from './hooks/usePlatformGroups';
import PlatformGroupCreate from './PlatformGroupCreate';
import PlatformGroupPopover from './PlatformGroupPopover';
import {
  ENTITY_PLATFORM_GROUP_PREFIX,
  getPlatformGroupHeaders,
  LOCAL_STORAGE_KEY_PLATFORM_GROUP,
  PLATFORM_GROUP_FILTERS,
  PLATFORM_GROUP_SORTS,
} from './platformGroups.queryable';

const useStyles = makeStyles()(() => ({
  container: { display: 'flex' },
  bodyItems: { flexGrow: 1 },
}));

const PlatformGroups = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const {
    platformGroups,
    setPlatformGroupList,
    loading,
    fetchPlatformGroups,
    addPlatformGroup,
    updatePlatformGroupInList,
    removePlatformGroup,
  } = usePlatformGroups();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_PLATFORM_GROUP, buildSearchPagination({ sorts: PLATFORM_GROUP_SORTS }));
  const headers = useMemo(() => getPlatformGroupHeaders(t), [t]);

  return (
    <div className={classes.container}>
      <div className={classes.bodyItems}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Platform') }, { label: t('Users & capabilities') }, {
            label: t('Groups'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={fetchPlatformGroups}
          searchPaginationInput={searchPaginationInput}
          setContent={setPlatformGroupList}
          entityPrefix={ENTITY_PLATFORM_GROUP_PREFIX}
          availableFilterNames={PLATFORM_GROUP_FILTERS}
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
                  inlineStylesHeaders={{}}
                />
              )}
            />
          </ListItem>
          {loading
            ? <PaginatedListLoader Icon={GroupsOutlined} headers={headers} headerStyles={{}} />
            : (
                <PaginatedList<PlatformGroupOutput>
                  Icon={GroupsOutlined}
                  secondaryAction={group => (
                    <PlatformGroupPopover
                      inList
                      platformGroup={group}
                      actions={['Update', 'Manage users', 'Manage roles', 'Delete']}
                      onUpdate={updatePlatformGroupInList}
                      onDelete={removePlatformGroup}
                    />
                  )}
                  headers={headers}
                  items={platformGroups}
                  rowKey="platform_group_id"
                />
              )}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_GROUPS_AND_ROLES}>
          <PlatformGroupCreate onCreate={addPlatformGroup} />
        </Can>
      </div>
      <UsersCapabilitiesMenu />
    </div>
  );
};

export default PlatformGroups;
