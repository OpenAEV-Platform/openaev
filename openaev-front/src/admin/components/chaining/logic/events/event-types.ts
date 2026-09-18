import { z } from 'zod';

import type { ConditionCreateInput } from '../../../../../utils/api-types';
import {
  capabilitiesOf,
  type DescriptorsByPrimitiveType,
  EMPTY_DESCRIPTORS,
  getPrimitiveFormatError,
  UNEXPECTED_FORMAT_ERROR,
  validationOf,
} from '../primitive-types';

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

// Which operators a field offers, whether its value must be numeric and whether the case toggle is
// meaningful all come from the backend descriptor (`GET /api/chaining/primitive-types`). Hardcoding
// them here is what previously let the UI offer `>` on `severity`, an operator the backend
// evaluates with Double.parseDouble and that could therefore never match.

export const isNumericField = (
  field: ConditionKeyType,
  descriptorsByType: DescriptorsByPrimitiveType,
): boolean => capabilitiesOf(field, descriptorsByType).is_numeric_value === true;

/** Whether the case-sensitivity toggle carries any meaning for this field. */
export const supportsCaseSensitivity = (
  field: ConditionKeyType,
  descriptorsByType: DescriptorsByPrimitiveType,
): boolean => capabilitiesOf(field, descriptorsByType).is_case_sensitivity !== false;

/**
 * Case sensitivity to store for a field, given the one currently set.
 *
 * Forced off on a type whose values carry no case: the toggle is hidden there, so leaving it on
 * would apply a strictness the user can no longer see nor undo - `D41D8C...` would stop matching
 * `d41d8c...` on the very type where both denote the same digest.
 */
export const resolveCaseSensitive = (
  field: ConditionKeyType,
  caseSensitive: boolean,
  descriptorsByType: DescriptorsByPrimitiveType,
): boolean => supportsCaseSensitivity(field, descriptorsByType) && caseSensitive;

/** Operators offered for a given field: comparisons are hidden on non-numeric fields. */
export const getAvailableOperators = (
  field: ConditionKeyType,
  descriptorsByType: DescriptorsByPrimitiveType,
): ComparisonOperator[] =>
  COMPARISON_OPERATORS.filter(operator => isNumericField(field, descriptorsByType) || !NUMERIC_OPERATORS.includes(operator));

/** Falls back to the first available operator when the current one is not valid for the field. */
export const resolveOperator = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
  descriptorsByType: DescriptorsByPrimitiveType,
): ComparisonOperator => {
  const available = getAvailableOperators(field, descriptorsByType);
  return available.includes(operator) ? operator : available[0];
};

// Operators whose value is not a single operand: the backend splits them on commas and matches
// each item with `contains` (ConditionUtils#handleInComparison), so a partial value such as `10.0.`
// or a list such as `8,44` is a legitimate input and no format may be applied to it.
export const MULTI_VALUE_OPERATORS: ComparisonOperator[] = ['IN', 'NIN'];

/** A value must be numeric when either the inspected field or the operator is numeric. */
export const requiresNumericValue = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
  descriptorsByType: DescriptorsByPrimitiveType,
): boolean => {
  if (UNARY_OPERATORS.includes(operator)) return false;
  // A numeric operator is numeric whatever the field: the backend parses both operands as doubles.
  if (NUMERIC_OPERATORS.includes(operator)) return true;
  // A numeric field only constrains a single operand, never a comma-separated list.
  return !MULTI_VALUE_OPERATORS.includes(operator) && isNumericField(field, descriptorsByType);
};

// -- Expected value validation --
export const CONDITION_VALUE_ERRORS = {
  required: 'This field is required.',
  number: 'The value should be a number',
  // Fallback only: the backend ships a stable key per rule, which the UI translates as-is.
  format: UNEXPECTED_FORMAT_ERROR,
} as const;

const NUMBER_PATTERN = /^-?\d+(?:\.\d+)?$/;

/**
 * Builds the zod schema validating the "Expected Value" of a single condition.
 * The rules depend on the inspected field, on the selected operator and on the backend descriptor.
 */
export const buildConditionValueSchema = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
  descriptorsByType: DescriptorsByPrimitiveType = EMPTY_DESCRIPTORS,
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

  // The numeric rule is checked first and on its own: the backend compares these operands with
  // Double.parseDouble, so a non-numeric value could never match whatever the type's format says.
  if (requiresNumericValue(field, operator, descriptorsByType) && !NUMBER_PATTERN.test(value)) {
    ctx.addIssue({
      code: 'custom',
      message: CONDITION_VALUE_ERRORS.number,
    });
    return;
  }

  // The type's own format still applies on top, for the operators it declares. Comparison
  // operators are absent from `applies_to`, so they stop at the numeric check above.
  // `applies_to` deliberately excludes IN / NIN: the backend splits those on commas and matches
  // each item with `contains`, so a partial value such as `10.0.` is a legitimate input.
  const operatorIsValidated = (validationOf(field, descriptorsByType)?.applies_to ?? []).includes(operator);
  if (!operatorIsValidated) return;

  const formatError = getPrimitiveFormatError(field, value, descriptorsByType);
  if (!formatError) return;

  ctx.addIssue({
    code: 'custom',
    message: formatError,
  });
});

/** Returns the (untranslated) error message for a condition value, or undefined when valid. */
export const getConditionValueError = (
  field: ConditionKeyType,
  operator: ComparisonOperator,
  value: string,
  descriptorsByType: DescriptorsByPrimitiveType = EMPTY_DESCRIPTORS,
): string | undefined => {
  const result = buildConditionValueSchema(field, operator, descriptorsByType).safeParse(value);
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
export const isConditionValid = (
  condition: EventCondition,
  descriptorsByType: DescriptorsByPrimitiveType = EMPTY_DESCRIPTORS,
): boolean => {
  if (!condition.field) return false;
  if (!condition.operator) return false;
  return getConditionValueError(condition.field, condition.operator, condition.value, descriptorsByType) === undefined;
};

export const isGroupValid = (
  group: ConditionGroup,
  descriptorsByType: DescriptorsByPrimitiveType = EMPTY_DESCRIPTORS,
): boolean => {
  const hasValidConditions = group.conditions.length > 0
    && group.conditions.every(condition => isConditionValid(condition, descriptorsByType));
  const hasValidSubGroups = group.subGroups.length === 0
    || group.subGroups.every(subGroup => isGroupValid(subGroup, descriptorsByType));
  return (hasValidConditions || group.subGroups.length > 0) && hasValidSubGroups;
};

export const isEventFormValid = (
  data: EventFormData,
  descriptorsByType: DescriptorsByPrimitiveType = EMPTY_DESCRIPTORS,
): boolean => {
  if (!data.name.trim()) return false;
  if (data.conditionGroups.length === 0) return false;
  return data.conditionGroups.every(group => isGroupValid(group, descriptorsByType));
};

// -- Conversion helpers --
/**
 * Converts the form's condition tree into the flat array expected by the API.
 *
 * The descriptors are required rather than optional: case sensitivity is re-resolved here, on the
 * last gate before the payload leaves the browser. A condition stored before its type became
 * caseless still loads with the flag on, and the toggle is then hidden, so editing anything else
 * in the event would otherwise write that stale value back untouched.
 */
export const conditionGroupsToApi = (
  groups: ConditionGroup[],
  groupOperators: LogicalOperator[],
  descriptorsByType: DescriptorsByPrimitiveType,
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
        condition_case_sensitive: resolveCaseSensitive(
          cond.field,
          cond.caseSensitive,
          descriptorsByType,
        ),
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

// EQ rather than IN: IN is a substring match on a comma-separated list, so no format rule may be
// applied to it. Starting on EQ makes the format of the selected field validated from the first
// keystroke instead of only once the user happens to change the operator.
export const createEmptyCondition = (): EventCondition => ({
  id: generateId(),
  field: 'text',
  operator: 'EQ',
  value: '',
  caseSensitive: true,
});

export const createEmptyGroup = (operator: LogicalOperator = 'AND'): ConditionGroup => ({
  id: generateId(),
  operator,
  conditions: [createEmptyCondition()],
  subGroups: [],
});
