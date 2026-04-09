import { CheckCircleOutlined } from '@mui/icons-material';
import { createElement, type CSSProperties } from 'react';

import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import ItemTags from '../../../../../components/ItemTags';
import type { SortField, UserOutput } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_PLATFORM_USER = 'platform_users';

// Entity
export const ENTITY_PLATFORM_USER_PREFIX = 'user';

// Fields
const FIELD_EMAIL = 'user_email';
const FIELD_FIRSTNAME = 'user_firstname';
const FIELD_LASTNAME = 'user_lastname';
const FIELD_ADMIN = 'user_admin';
const FIELD_ORGANIZATION = 'user_organization_name';
const FIELD_TAGS = 'user_tags';

// Inline Styles
export const PLATFORM_USER_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_EMAIL]: { width: '20%' },
  [FIELD_FIRSTNAME]: { width: '12%' },
  [FIELD_LASTNAME]: { width: '12%' },
  [FIELD_ADMIN]: { width: '10%' },
  [FIELD_ORGANIZATION]: { width: '18%' },
  [FIELD_TAGS]: { width: '20%' },
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
    field: FIELD_ADMIN,
    label: t('Admin'),
    isSortable: false,
    value: (user: UserOutput) => user.user_admin
      ? createElement(CheckCircleOutlined, { fontSize: 'small' })
      : '-',
  },
  {
    field: FIELD_ORGANIZATION,
    label: t('Organization'),
    isSortable: false,
    value: (user: UserOutput) => user.user_organization_name ?? '-',
  },
  {
    field: FIELD_TAGS,
    label: t('Tags'),
    isSortable: false,
    value: (user: UserOutput) => createElement(ItemTags, {
      variant: 'list',
      tags: user.user_tags,
    }),
  },
];

// Filters
export const PLATFORM_USER_FILTERS = [FIELD_EMAIL, FIELD_FIRSTNAME, FIELD_LASTNAME];

// Sorts
export const PLATFORM_USER_SORTS: SortField[] = initSorting(FIELD_EMAIL);
