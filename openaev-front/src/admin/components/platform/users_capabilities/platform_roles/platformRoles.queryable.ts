import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import type { PlatformRoleOutput, SortField } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_PLATFORM_ROLE = 'platform_roles';

// Entity
export const ENTITY_PLATFORM_ROLE_PREFIX = 'platform_role';

// Fields
const FIELD_NAME = 'platform_role_name';

// Headers
export const getPlatformRoleHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_NAME,
    label: t('Name'),
    isSortable: true,
    value: (role: PlatformRoleOutput) => role.platform_role_name,
  },
];

// Filters
export const PLATFORM_ROLE_FILTERS = [FIELD_NAME];

// Sorts
export const PLATFORM_ROLE_SORTS: SortField[] = initSorting(FIELD_NAME);
