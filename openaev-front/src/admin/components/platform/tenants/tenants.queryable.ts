import { initSorting } from '../../../../components/common/queryable/Page';
import type { Header } from '../../../../components/common/SortHeadersList';
import type { SortField, TenantOutput } from '../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_TENANT = 'tenants';

// Entity
export const ENTITY_TENANT_PREFIX = 'tenant';

// Fields
const FIELD_TENANT_NAME = 'tenant_name';

// Headers
export const getTenantHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_TENANT_NAME,
    label: t('Tenant name'),
    isSortable: true,
    value: (tenant: TenantOutput) => tenant.tenant_name,
  },
];

// Filters
export const TENANT_FILTERS = [FIELD_TENANT_NAME];

// Sorts
export const TENANT_SORTS: SortField[] = initSorting(FIELD_TENANT_NAME);
