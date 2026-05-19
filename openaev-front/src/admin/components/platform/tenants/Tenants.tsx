import { HomeWorkOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { useMemo } from 'react';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginatedList from '../../../../components/common/list/PaginatedList';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type TenantOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import SecurityMenu from '../../settings/SecurityMenu';
import useTenants from './hooks/useTenants';
import TenantCreate from './tenant/TenantCreate';
import TenantPopover from './TenantPopover';
import {
  ENTITY_TENANT_PREFIX,
  getTenantHeaders,
  LOCAL_STORAGE_KEY_TENANT,
  TENANT_FILTERS,
  TENANT_INLINE_STYLES,
  TENANT_SORTS,
} from './tenants.queryable';

const Tenants = () => {
  // Standard hooks
  const { t } = useFormatter();

  const {
    tenants,
    setTenantList,
    loading,
    fetchTenants,
    addTenant,
    updateTenant,
    softDeleteTenant,
    reactivateTenant,
  } = useTenants();

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT, buildSearchPagination({ sorts: TENANT_SORTS }));
  const headers = useMemo(() => getTenantHeaders(t), [t]);

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Platform') }, {
            label: t('Tenants management'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={fetchTenants}
          searchPaginationInput={searchPaginationInput}
          setContent={setTenantList}
          entityPrefix={ENTITY_TENANT_PREFIX}
          availableFilterNames={TENANT_FILTERS}
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
                  inlineStylesHeaders={TENANT_INLINE_STYLES}
                />
              )}
            />
          </ListItem>
          {loading
            ? (
                <PaginatedListLoader
                  Icon={HomeWorkOutlined}
                  headers={headers}
                  headerStyles={TENANT_INLINE_STYLES}
                />
              )
            : (
                <PaginatedList<TenantOutput>
                  Icon={HomeWorkOutlined}
                  secondaryAction={tenant => (
                    <TenantPopover
                      inList
                      tenant={tenant}
                      actions={tenant.tenant_deleted_at ? ['Reactivate'] : ['Update', 'Delete']}
                      onUpdate={updateTenant}
                      onDelete={softDeleteTenant}
                      onReactivate={reactivateTenant}
                    />
                  )}
                  headers={headers}
                  items={tenants}
                  rowKey="tenant_id"
                  itemWidth={TENANT_INLINE_STYLES}
                />
              )}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANTS}>
          <TenantCreate onCreate={addTenant} />
        </Can>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Tenants;
