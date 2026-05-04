import { type CSSProperties } from 'react';

import { initSorting } from '../../../../../components/common/queryable/Page';
import { type Header } from '../../../../../components/common/SortHeadersList';
import { type SortField, type UserOutput } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_TENANT_USER = 'tenant_users';

// Entity
export const ENTITY_TENANT_USER_PREFIX = 'user';

// Fields
const FIELD_EMAIL = 'user_email';
const FIELD_FIRSTNAME = 'user_firstname';
const FIELD_LASTNAME = 'user_lastname';

// Inline Styles
export const TENANT_USER_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_EMAIL]: { width: '25%' },
  [FIELD_FIRSTNAME]: { width: '15%' },
  [FIELD_LASTNAME]: { width: '15%' },
};

// Headers
export const getTenantUserHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_EMAIL,
    label: t('Email address'),
    isSortable: true,
    value: (user: UserOutput) => user.user_email,
  },
  {
    field: FIELD_FIRSTNAME,
    label: t('Firstname'),
    isSortable: true,
    value: (user: UserOutput) => user.user_firstname || '-',
  },
  {
    field: FIELD_LASTNAME,
    label: t('Lastname'),
    isSortable: true,
    value: (user: UserOutput) => user.user_lastname || '-',
  },
];

// Filters
export const TENANT_USER_FILTERS = [FIELD_EMAIL, FIELD_FIRSTNAME, FIELD_LASTNAME];

// Sorts
export const TENANT_USER_SORTS: SortField[] = initSorting(FIELD_FIRSTNAME);
