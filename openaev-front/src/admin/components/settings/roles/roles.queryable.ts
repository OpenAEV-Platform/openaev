import type { CSSProperties } from 'react';

import { initSorting } from '../../../../components/common/queryable/Page';
import type { Header } from '../../../../components/common/SortHeadersList';
import type { RoleOutput, SortField } from '../../../../utils/api-types';

// Entity
export const ENTITY_ROLE_PREFIX = 'role';

// Fields
const FIELD_NAME = 'role_name';
const FIELD_DESCRIPTION = 'role_description';

// Inline styles
export const ROLE_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_NAME]: { width: '25%' },
  [FIELD_DESCRIPTION]: { width: '75%' },
};

// Headers
export const getRoleHeaders: (t: (text: string) => string) => Header[] = (t: (text: string) => string) => [
  {
    field: FIELD_NAME,
    label: t('Name'),
    isSortable: true,
    value: (role: RoleOutput) => role.role_name,
  },
  {
    field: FIELD_DESCRIPTION,
    label: t('Description'),
    isSortable: false,
    value: (role: RoleOutput) => role.role_description || '-',
  },
];

// Filters
export const ROLE_FILTERS = [FIELD_NAME, FIELD_DESCRIPTION];

// Sorts
export const ROLE_SORTS: SortField[] = initSorting(FIELD_NAME);
