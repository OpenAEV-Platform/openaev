import { createElement, type CSSProperties } from 'react';

import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import ItemTenants from '../../../../../components/items/ItemTenants';
import type { SortField, UserOutput } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_PLATFORM_USER = 'platform_users';

// Entity
export const ENTITY_PLATFORM_USER_PREFIX = 'user';

// Fields
const FIELD_EMAIL = 'user_email';
const FIELD_FIRSTNAME = 'user_firstname';
const FIELD_LASTNAME = 'user_lastname';
const FIELD_TENANTS = 'user_tenants';

// Inline Styles
export const PLATFORM_USER_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_EMAIL]: { width: '25%' },
  [FIELD_FIRSTNAME]: { width: '15%' },
  [FIELD_LASTNAME]: { width: '15%' },
  [FIELD_TENANTS]: { width: '35%' },
};

// Headers
export const getPlatformUserHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
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
    value: (user: UserOutput) => user.user_firstname,
  },
  {
    field: FIELD_LASTNAME,
    label: t('Lastname'),
    isSortable: true,
    value: (user: UserOutput) => user.user_lastname,
  },
  {
    field: FIELD_TENANTS,
    label: 'Tenants',
    isSortable: false,
    value: (user: UserOutput) => createElement(ItemTenants, { tenants: user.user_tenants }),
  },
];

// Filters
export const PLATFORM_USER_FILTERS = [FIELD_EMAIL, FIELD_FIRSTNAME, FIELD_LASTNAME];

// Sorts
export const PLATFORM_USER_SORTS: SortField[] = initSorting(FIELD_EMAIL);
