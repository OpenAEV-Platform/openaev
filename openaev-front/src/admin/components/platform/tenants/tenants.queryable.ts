import { createElement } from 'react';
import { type CSSProperties } from 'react';

import { initSorting } from '../../../../components/common/queryable/Page';
import type { Header } from '../../../../components/common/SortHeadersList';
import type { SortField, TenantOutput } from '../../../../utils/api-types';
import TenantStatus from './TenantStatus';

// Local Storage
export const LOCAL_STORAGE_KEY_TENANT = 'tenants';

// Entity
export const ENTITY_TENANT_PREFIX = 'tenant';

// Fields
const FIELD_TENANT_NAME = 'tenant_name';
const FIELD_TENANT_DELETED_AT = 'tenant_deleted_at';

// Inline Styles
export const TENANT_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_TENANT_NAME]: { width: '70%' },
  [FIELD_TENANT_DELETED_AT]: { width: '30%' },
};

// Headers
export const getTenantHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_TENANT_NAME,
    label: t('Tenant name'),
    isSortable: true,
    value: (tenant: TenantOutput) => tenant.tenant_name,
  },
  {
    field: FIELD_TENANT_DELETED_AT,
    label: t('Status'),
    isSortable: false,
    value: (tenant: TenantOutput) => createElement(TenantStatus, { deletedAt: tenant.tenant_deleted_at }),
  },
];

// Filters
export const TENANT_FILTERS = [FIELD_TENANT_NAME];

// Sorts
export const TENANT_SORTS: SortField[] = initSorting(FIELD_TENANT_NAME);
