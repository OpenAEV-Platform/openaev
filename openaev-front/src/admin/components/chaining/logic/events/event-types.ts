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
  return UNARY_OPERATORS.includes(condition.operator) || !!condition.value.trim();
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
        condition_key_type: cond.field as ConditionCreateInput['condition_key_type'],
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
