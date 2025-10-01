import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchRoles } from '../../../../actions/roles/roles-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import type { RoleOutput, SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import SecurityMenu from '../SecurityMenu';
import CreateRole from './CreateRole';
import RolePopover from './RolePopover';
import RolesList from './RolesList';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
  container: { display: 'flex' },
  bodyItems: { flexGrow: 1 },
}));

const Roles = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  const [roles, setRoles] = useState<RoleOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('role', buildSearchPagination({
    sorts: initSorting('role_name', 'ASC'),
    textSearch: search,
  }));

  const secondaryAction = (role: RoleOutput) => (
    <RolePopover
      onDelete={result => setRoles(roles.filter(r => (r.role_id !== result)))}
      onUpdate={result => setRoles(roles.map(r => (r.role_id !== result.role_id ? r : result)))}
      role={{ ...role }}
    />
  );

  const searchRolesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchRoles(input).finally(() => {
      setLoading(false);
    });
  };

  return (
    <div className={classes.container}>
      <div className={classes.bodyItems}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, {
            label: t('Roles'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchRolesToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setRoles}
          entityPrefix="role"
          queryableHelpers={queryableHelpers}
          disablePagination
          disableFilters
        />
        <RolesList roles={roles} queryableHelpers={queryableHelpers} loading={loading} secondaryAction={secondaryAction} />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateRole onCreate={(result: RoleOutput) => setRoles([...roles, result])} />
        </Can>

      </div>
      <SecurityMenu />

    </div>
  );
};

export default Roles;
