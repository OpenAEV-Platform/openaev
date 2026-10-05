import { CheckCircleOutlined } from '@mui/icons-material';
import type { CSSProperties } from 'react';
import { createElement } from 'react';

import { initSorting } from '../../../../../components/common/queryable/Page';
import type { Header } from '../../../../../components/common/SortHeadersList';
import ItemMarkings from '../../../../../components/ItemMarkings';
import type { Group, MarkingDefinitionOutput, SortField } from '../../../../../utils/api-types';

// Local Storage
export const LOCAL_STORAGE_KEY_TENANT_GROUP = 'tenant_groups';

// Entity
export const ENTITY_TENANT_GROUP_PREFIX = 'group';

// Fields
const FIELD_NAME = 'group_name';
const FIELD_DESCRIPTION = 'group_description';
const FIELD_DEFAULT_ASSIGN = 'group_default_user_assign';
const FIELD_MARKINGS = 'group_markings';

// Inline styles
export const TENANT_GROUP_INLINE_STYLES: Record<string, CSSProperties> = {
  [FIELD_NAME]: { width: '25%' },
  [FIELD_DESCRIPTION]: { width: '35%' },
  [FIELD_MARKINGS]: { width: '20%' },
  [FIELD_DEFAULT_ASSIGN]: { width: '20%' },
};

// Headers
// The Markings column is only appended when the MARKING feature flag is on - with it off, the
// column (and any marking data) must not leak into the list at all, not merely render empty.
export const getTenantGroupHeaders: (
  t: (text: string) => string,
  options?: {
    markingEnabled?: boolean;
    markingDefinitions?: Record<string, MarkingDefinitionOutput>;
  },
) => Header[] = (t, options) => {
  const headers: Header[] = [
    {
      field: FIELD_NAME,
      label: t('Name'),
      isSortable: true,
      value: (group: Group) => group.group_name,
    },
    {
      field: FIELD_DESCRIPTION,
      label: t('Description'),
      isSortable: false,
      value: (group: Group) => group.group_description || '-',
    },
  ];
  if (options?.markingEnabled) {
    headers.push({
      field: FIELD_MARKINGS,
      label: t('Markings'),
      isSortable: false,
      value: (group: Group) => createElement(ItemMarkings, {
        markingIds: group.group_markings,
        definitions: options.markingDefinitions ?? {},
        variant: 'list',
      }),
    });
  }
  headers.push({
    field: FIELD_DEFAULT_ASSIGN,
    label: t('Auto assign'),
    isSortable: false,
    value: (group: Group) => group.group_default_user_assign
      ? createElement(CheckCircleOutlined, { fontSize: 'small' })
      : '-',
  });
  return headers;
};

// Filters
export const TENANT_GROUP_FILTERS = [FIELD_NAME, FIELD_DESCRIPTION];

// Sorts
export const TENANT_GROUP_SORTS: SortField[] = initSorting(FIELD_NAME);
