import {PersonOutlined, SecurityOutlined} from '@mui/icons-material';
import {List, ListItem, ListItemIcon, ListItemText} from '@mui/material';
import {makeStyles} from "tss-react/mui";
import {useFormatter} from "../../../../../components/i18n";
import usePlatformUsers from "./hooks/usePlatformRoles";
import {useQueryableWithLocalStorage} from "../../../../../components/common/queryable/useQueryableWithLocalStorage";
import {buildSearchPagination} from "../../../../../components/common/queryable/QueryableUtils";
import {type CSSProperties, useMemo} from "react";
import {
    ENTITY_PLATFORM_USER_PREFIX, FIELD_EMAIL, FIELD_FIRSTNAME, FIELD_LASTNAME, FIELD_ORGANIZATION, FIELD_TAGS,
    getPlatformUserHeaders,
    LOCAL_STORAGE_KEY_PLATFORM_USER,
    PLATFORM_USER_FILTERS,
    PLATFORM_USER_SORTS
} from "./platformUsers.queryable";
import Breadcrumbs from "../../../../../components/Breadcrumbs";
import PaginationComponentV2 from "../../../../../components/common/queryable/pagination/PaginationComponentV2";
import SortHeadersComponentV2 from "../../../../../components/common/queryable/sort/SortHeadersComponentV2";
import PaginatedListLoader from "../../../../../components/PaginatedListLoader";
import PaginatedList from "../../../../../components/common/list/PaginatedList";
import type {UserOutput} from "../../../../../utils/api-types";
import {Can} from "../../../../../utils/permissions/PermissionsProvider";
import {ACTIONS, SUBJECTS} from "../../../../../utils/permissions/types";
import PlatformRoleCreate from "../platform_roles/PlatformRoleCreate";
import UsersCapabilitiesMenu from "../UsersCapabilitiesMenu";
import PlatformUserPopover from "./PlatformUserPopover";
import PlatformUserCreate from "./PlatformUserCreate";

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
        removePlatformUser,
    } = usePlatformUsers();

    const {
        queryableHelpers,
        searchPaginationInput,
    } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_PLATFORM_USER, buildSearchPagination({ sorts: PLATFORM_USER_SORTS }));
    const headers = useMemo(() => getPlatformUserHeaders(t), [t]);

    const itemWidth: Record<string, CSSProperties> = {
        [FIELD_EMAIL]: { width: '20%' },
        [FIELD_FIRSTNAME]: { width: '15%' },
        [FIELD_LASTNAME]: { width: '15%' },
        [FIELD_ORGANIZATION]: { width: '20%' },
        [FIELD_TAGS]: { width: '30%' },
    };

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
                                  <PlatformUserPopover
                                      inList
                                      user={user}
                                      actions={['Delete']}
                                      onDelete={removePlatformUser}
                                  />
                              )}
                              headers={headers}
                              items={platformUsers}
                              rowKey="user_id"
                              itemWidth={itemWidth}
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

