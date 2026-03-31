import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import type { PlatformGroupOutput, SortField } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_PLATFORM_GROUP = 'platform_groups';

// Entity
export const ENTITY_PLATFORM_GROUP_PREFIX = 'platform_group';

// Fields
const FIELD_NAME = 'platform_group_name';

// Headers
export const getPlatformGroupHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_NAME,
    label: t('Name'),
    isSortable: true,
    value: (group: PlatformGroupOutput) => group.platform_group_name,
  },
];

// Filters
export const PLATFORM_GROUP_FILTERS = [FIELD_NAME];

// Sorts
export const PLATFORM_GROUP_SORTS: SortField[] = initSorting(FIELD_NAME);
