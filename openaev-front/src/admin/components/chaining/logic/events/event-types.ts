import { z } from 'zod';

import type { ConditionCreateInput } from '../../../../../utils/api-types';

export type ConditionKeyType = string;

/** 'admin_username' → 'Admin username' */
export const formatConditionKeyLabel = (value: string): string => value
  .replace(/[_-]/g, ' ')
  .replace(/^./, c => c.toUpperCase());

// -- Operators available for conditions --
export const COMPARISON_OPERATORS = [
  'EQ', 'NEQ', 'IS_NULL', 'IS_NOT_NULL',
  'GT', 'GTE', 'LT', 'LTE', 'IN', 'NIN',
] as const;

export type ComparisonOperator = typeof COMPARISON_OPERATORS[number];

// Operators that don't require a value
export const UNARY_OPERATORS: ComparisonOperator[] = ['IS_NULL', 'IS_NOT_NULL'];

// Operators where case sensitivity is relevant
export const CASE_SENSITIVE_OPERATORS: ComparisonOperator[] = ['EQ', 'NEQ', 'IN', 'NIN'];

// Operators the backend evaluates numerically (see ConditionUtils#handleNumericComparison):
// a non-numeric expected value can never match, so it must be rejected at input time.
export const NUMERIC_OPERATORS: ComparisonOperator[] = ['GT', 'GTE', 'LT', 'LTE'];

// PrimitiveType labels (backend enum) that only ever hold numeric values
export const NUMERIC_FIELD_TYPES: ConditionKeyType[] = ['number', 'port'];

export const ORDERED_FIELD_TYPES: ConditionKeyType[] = [...NUMERIC_FIELD_TYPES, 'severity'];

export const isNumericField = (field: ConditionKeyType): boolean => NUMERIC_FIELD_TYPES.includes(field);

export const supportsOrdering = (field: ConditionKeyType): boolean => ORDERED_FIELD_TYPES.includes(field);

/** Operators offered for a given field: ordering comparisons are hidden on non-numeric fields. */
export const getAvailableOperators = (field: ConditionKeyType): ComparisonOperator[] =>
  COMPARISON_OPERATORS.filter(operator => supportsOrdering(field) || !NUMERIC_OPERATORS.includes(operator));

/** Falls back to the first available operator when the current one is not valid for the field. */
export const resolveOperator = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
): ComparisonOperator => {
  const available = getAvailableOperators(field);
  return available.includes(operator) ? operator : available[0];
};

/** A value must be numeric when either the inspected field or the operator is numeric. */
export const requiresNumericValue = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
): boolean => !UNARY_OPERATORS.includes(operator)
  && (isNumericField(field) || NUMERIC_OPERATORS.includes(operator));

// -- Expected value validation --
export const CONDITION_VALUE_ERRORS = {
  required: 'This field is required.',
  number: 'The value should be a number',
} as const;

const NUMBER_PATTERN = /^-?\d+(?:\.\d+)?$/;

/**
 * Builds the zod schema validating the "Expected Value" of a single condition.
 * The rules depend on the inspected field and on the selected operator.
 */
export const buildConditionValueSchema = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
) => z.string().superRefine((rawValue, ctx) => {
  // Unary operators (IS_NULL / IS_NOT_NULL) take no value
  if (UNARY_OPERATORS.includes(operator)) return;

  const value = rawValue.trim();
  if (!value) {
    ctx.addIssue({
      code: 'custom',
      message: CONDITION_VALUE_ERRORS.required,
    });
    return;
  }

  if (!requiresNumericValue(field, operator)) return;

  if (!NUMBER_PATTERN.test(value)) {
    ctx.addIssue({
      code: 'custom',
      message: CONDITION_VALUE_ERRORS.number,
    });
  }
});

/** Returns the (untranslated) error message for a condition value, or undefined when valid. */
export const getConditionValueError = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
  value: string,
): string | undefined => {
  const result = buildConditionValueSchema(field, operator).safeParse(value);
  return result.success ? undefined : result.error.issues[0]?.message;
};

// -- Operator labels (function form so the extractor sees static t() calls) --
export const OPERATOR_LABELS: Record<ComparisonOperator, string> = {
  EQ: 'Equals',
  NEQ: 'Not equals',
  IS_NULL: 'Is null',
  IS_NOT_NULL: 'Is not null',
  GT: 'Greater than',
  GTE: 'Greater than or equals',
  LT: 'Less than',
  LTE: 'Less than or equals',
  IN: 'Contains',
  NIN: 'Not contains',
};

// -- Condition (leaf node) --
export interface EventCondition {
  id: string;
  field: ConditionKeyType;
  operator: ComparisonOperator;
  value: string;
  caseSensitive: boolean;
}

// -- Condition group (AND/OR container, can be nested) --
export type LogicalOperator = 'AND' | 'OR';

export interface ConditionGroup {
  id: string;
  operator: LogicalOperator;
  conditions: EventCondition[];
  subGroups: ConditionGroup[];
}

// -- Event form data --
export interface EventFormData {
  name: string;
  description: string;
  groupOperators: LogicalOperator[];
  conditionGroups: ConditionGroup[];
}

// -- Validation helpers --
export const isConditionValid = (condition: EventCondition): boolean => {
  if (!condition.field) return false;
  if (!condition.operator) return false;
  return getConditionValueError(condition.field, condition.operator, condition.value) === undefined;
};

export const isGroupValid = (group: ConditionGroup): boolean => {
  const hasValidConditions = group.conditions.length > 0
    && group.conditions.every(isConditionValid);
  const hasValidSubGroups = group.subGroups.length === 0
    || group.subGroups.every(isGroupValid);
  return (hasValidConditions || group.subGroups.length > 0) && hasValidSubGroups;
};

export const isEventFormValid = (data: EventFormData): boolean => {
  if (!data.name.trim()) return false;
  if (data.conditionGroups.length === 0) return false;
  return data.conditionGroups.every(isGroupValid);
};

// -- Conversion helpers --
/**
 * Converts the form's condition tree into the flat array expected by the API.
 */
export const conditionGroupsToApi = (
  groups: ConditionGroup[],
  groupOperators: LogicalOperator[] = [],
): ConditionCreateInput[] => {
  // Local counter: resets each call so IDs stay predictable
  let tempIdCounter = 0;
  const nextTempId = () => `temp_${++tempIdCounter}`;

  const result: ConditionCreateInput[] = [];

  const processGroup = (group: ConditionGroup, parentTempId?: string) => {
    // 1. Emit the logical node for this group
    const groupTempId = nextTempId();
    result.push({
      condition_temporary_id: groupTempId,
      condition_temporary_id_condition_parent: parentTempId,
      condition_type: group.operator,
    });

    // 2. Emit each leaf condition, parented to the group node
    group.conditions.forEach(cond =>
      result.push({
        condition_temporary_id: nextTempId(),
        condition_temporary_id_condition_parent: groupTempId,
        condition_type: cond.operator as ConditionCreateInput['condition_type'],
        condition_key_types: [cond.field] as ConditionCreateInput['condition_key_types'],
        // Unary operators (IS_NULL / IS_NOT_NULL) need no value
        condition_value: UNARY_OPERATORS.includes(cond.operator) ? undefined : cond.value,
        condition_case_sensitive: cond.caseSensitive,
      }),
    );

    // 3. Recurse into nested sub-groups
    group.subGroups.forEach(subGroup => processGroup(subGroup, groupTempId));
  };

  if (groups.length === 1) {
    // Single group → it is the root, no wrapper needed
    processGroup(groups[0]);
    return result;
  }

  // Multiple groups → emit a single root logical node wrapping them all.
  // The backend stores one operator at the root, so all gap operators must be identical.
  // EventCreationForm.handleUpdateGroupOperator keeps them in sync: groupOperators[0] is authoritative.
  const rootTempId = nextTempId();
  result.push({
    condition_temporary_id: rootTempId,
    condition_type: groupOperators[0] ?? 'AND',
  });

  groups.forEach(group => processGroup(group, rootTempId));

  return result;
};

export const generateId = (): string => `tmp_${Date.now()}_${Math.random().toString(16).slice(2)}`;

export const createEmptyCondition = (): EventCondition => ({
  id: generateId(),
  field: 'text',
  operator: 'IN',
  value: '',
  caseSensitive: true,
});

export const createEmptyGroup = (operator: LogicalOperator = 'AND'): ConditionGroup => ({
  id: generateId(),
  operator,
  conditions: [createEmptyCondition()],
  subGroups: [],
});
