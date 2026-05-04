import { CheckCircleOutlined } from '@mui/icons-material';
import type { CSSProperties } from 'react';
import { createElement } from 'react';

import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import type { PlatformGroupOutput, SortField } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_PLATFORM_GROUP = 'platform_groups';

// Entity
export const ENTITY_PLATFORM_GROUP_PREFIX = 'group';

// Fields
const FIELD_NAME = 'group_name';
const FIELD_DESCRIPTION = 'group_description';
const FIELD_DEFAULT_ASSIGN = 'group_default_user_assign';

// Inline styles
export const PLATFORM_GROUP_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_NAME]: { width: '25%' },
  [FIELD_DESCRIPTION]: { width: '55%' },
  [FIELD_DEFAULT_ASSIGN]: { width: '20%' },
};

// Headers
export const getPlatformGroupHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_NAME,
    label: t('Name'),
    isSortable: true,
    value: (group: PlatformGroupOutput) => group.platform_group_name,
  },
  {
    field: FIELD_DESCRIPTION,
    label: t('Description'),
    isSortable: false,
    value: (group: PlatformGroupOutput) => group.platform_group_description || '-',
  },
  {
    field: FIELD_DEFAULT_ASSIGN,
    label: t('Auto assign'),
    isSortable: false,
    value: (group: PlatformGroupOutput) => group.group_default_user_assign
      ? createElement(CheckCircleOutlined, { fontSize: 'small' })
      : '-',
  },
];

// Filters
export const PLATFORM_GROUP_FILTERS = [FIELD_NAME, FIELD_DESCRIPTION];

// Sorts
export const PLATFORM_GROUP_SORTS: SortField[] = initSorting(FIELD_NAME);
