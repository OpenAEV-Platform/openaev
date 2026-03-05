import { initSorting } from '../../../../components/common/queryable/Page';
import type { Header } from '../../../../components/common/SortHeadersList';
import type { SortField, UserOutput } from '../../../../utils/api-types';
import ItemTags from '../../../../components/ItemTags';

// Local Storage
export const LOCAL_STORAGE_KEY_TENANT_USER = 'tenant_users';

// Entity
export const ENTITY_TENANT_USER_PREFIX = 'user';

// Fields
export const FIELD_EMAIL = 'user_email';
export const FIELD_FIRSTNAME = 'user_firstname';
export const FIELD_LASTNAME = 'user_lastname';
export const FIELD_ORGANIZATION = 'user_organization';
export const FIELD_TAGS = 'user_tags';

// Headers
export const getTenantUserHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_EMAIL,
    label: t('Email'),
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
    field: FIELD_ORGANIZATION,
    label: t('Organization'),
    isSortable: false,
    value: (user: UserOutput) => user.user_organization_name,
  },
  {
    field: FIELD_TAGS,
    label: t('Tags'),
    isSortable: false,
    value: (user: UserOutput) => <ItemTags variant="list" tags={user.user_tags} />,
  },
];

// Filters
export const TENANT_USER_FILTERS = [FIELD_EMAIL];

// Sorts
export const TENANT_USER_SORTS: SortField[] = initSorting(FIELD_EMAIL);

