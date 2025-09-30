import qs from 'qs';
import * as R from 'ramda';

import { type Filter, type FilterGroup, type PropertySchemaDTO, type RelatedEntityOutput } from '../../../../utils/api-types';
import { buildSearchPagination } from '../QueryableUtils';

export const emptyFilterGroup: FilterGroup = {
  mode: 'and',
  filters: [],
};

export const buildEmptyFilter = (key: string, operator: Filter['operator']) => {
  return {
    key,
    mode: 'or' as Filter['mode'],
    values: [],
    operator,
  };
};

export const buildFilter = (key: string, values: string[], operator: Filter['operator']) => {
  return {
    key,
    mode: 'or' as Filter['mode'],
    values,
    operator,
  };
};

export const isExistFilter = (filterGroup: FilterGroup, key: string) => {
  return filterGroup.filters?.some(f => f.key === key);
};

export const isEmptyFilter = (filterGroup: FilterGroup, key: string) => {
  if (R.isEmpty(filterGroup.filters)) {
    return true;
  }
  return !filterGroup.filters?.find(f => f.key === key) || R.isEmpty(filterGroup.filters?.find(f => f.key === key)?.values);
};

export const craftedDocumentFilter = (item: RelatedEntityOutput, keyFilter: string, key: string) => {
  return btoa(qs.stringify({
    ...buildSearchPagination({
      filterGroup: {
        mode: 'and',
        filters: [
          {
            key: keyFilter,
            operator: 'eq',
            values: [item.name ?? ''],
          },
        ],
      },
    }),
    key: key,
  }, { allowEmptyArrays: true }));
};

// -- OPERATOR --

export const convertOperatorToIcon = (t: (text: string) => string, operator: Filter['operator']) => {
  switch (operator) {
    case 'eq':
      return <>&nbsp;=</>;
    case 'not_eq':
      return <>&nbsp;&#8800;</>;
    case 'not_contains':
      return (
        <>
          &nbsp;
          {t('not contains')}
        </>
      );
    case 'contains':
      return (
        <>
          &nbsp;
          {t('contains')}
        </>
      );
    case 'starts_with':
      return (
        <>
          &nbsp;
          {t('starts with')}
        </>
      );
    case 'not_starts_with':
      return (
        <>
          &nbsp;
          {t('not starts with')}
        </>
      );
    case 'gt':
      return <>&nbsp;&#62;</>;
    case 'gte':
      return <>&nbsp;&#8805;</>;
    case 'lt':
      return <>&nbsp;&#60;</>;
    case 'lte':
      return <>&nbsp;&#8804;</>;
    case 'empty':
      return (
        <>
          &nbsp;
          {t('is empty')}
        </>
      );
    case 'not_empty':
      return (
        <>
          &nbsp;
          {t('is not empty')}
        </>
      );
    default:
      return null;
  }
};

export const OperatorKeyValues: { [key: string]: string } = {
  eq: 'Equals',
  not_eq: 'Not equals',
  contains: 'Contains',
  not_contains: 'Not contains',
  starts_with: 'Starts with',
  not_starts_with: 'Not starts with',
  gt: 'Greater than',
  gte: 'Greater than/ Equals',
  lt: 'Lower than',
  lte: 'Lower than/ Equals',
  empty: 'Empty',
  not_empty: 'Not empty',
};

export const availableOperators = (propertySchema: PropertySchemaDTO) => {
  if (propertySchema.schema_property_override_operators && propertySchema.schema_property_override_operators.length > 0) {
    return propertySchema.schema_property_override_operators;
  }
  // Date
  if (propertySchema.schema_property_type.includes('instant')) {
    return ['gt', 'gte', 'lt', 'lte', 'empty', 'not_empty'];
  }
  // Enum & not array
  if (propertySchema.schema_property_values && !propertySchema.schema_property_type_array) {
    return ['eq', 'not_eq', 'empty', 'not_empty'];
  }
  // Dynamic value & not array
  if (propertySchema.schema_property_has_dynamic_value && !propertySchema.schema_property_type_array) {
    return ['eq', 'not_eq', 'empty', 'not_empty'];
  }
  // Array
  if (propertySchema.schema_property_type_array) {
    return ['contains', 'not_contains', 'empty', 'not_empty'];
  }
  return [
    'eq',
    'not_eq',
    'contains',
    'not_contains',
    'starts_with',
    'not_starts_with',
  ];
};

export const convertJsonClassToJavaClass = (input: string) => {
  const segments = input.split('_');

  return segments
    .map((segment) => {
      return segment.charAt(0).toUpperCase() + segment.slice(1);
    })
    .join('');
};
