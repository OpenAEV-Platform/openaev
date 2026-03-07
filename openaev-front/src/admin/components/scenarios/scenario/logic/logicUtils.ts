import type { InputSource, Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';

/**
 * Extract output types from a step's output_parser JSON.
 * Supports two formats:
 *   - Payload format: [{ contract_output_elements: [{ contract_output_element_type: "port" }] }]
 *   - Contract content format: [{ type: "port" }, { type: "portscan" }]
 */
export const extractOutputTypesFromStepData = (step: WorkflowStep): string[] => {
  if (!step.step_output_parser) return [];
  try {
    const parser = JSON.parse(step.step_output_parser);
    if (!Array.isArray(parser)) return [];

    const types: string[] = [];
    for (const entry of parser) {
      // Payload format: entry has contract_output_elements array
      if (Array.isArray(entry.contract_output_elements)) {
        for (const el of entry.contract_output_elements) {
          if (el.contract_output_element_type) {
            types.push(el.contract_output_element_type);
          }
        }
      }
      // Contract content outputs format: entry has a direct type field
      if (typeof entry.type === 'string') {
        types.push(entry.type);
      }
    }
    return [...new Set(types)];
  } catch {
    return [];
  }
};

/**
 * Get root steps (steps that have no conditions pointing to them, i.e. no dependencies)
 */
export const getRootSteps = (workflow: Workflow): WorkflowStep[] => {
  return workflow.workflow_steps.filter(
    step => step.step_conditions.length === 0,
  );
};

/**
 * Get linked steps (steps that have conditions, i.e. events/dependent steps)
 */
export const getLinkedSteps = (workflow: Workflow): WorkflowStep[] => {
  return workflow.workflow_steps.filter(
    step => step.step_conditions.length > 0,
  );
};

/**
 * Find all actions (steps) that provision a given output field type
 */
export const getActionsProvisioningField = (
  steps: WorkflowStep[],
  fieldType: string,
): WorkflowStep[] => {
  return steps.filter(step => {
    const outputTypes = extractOutputTypesFromStepData(step);
    return outputTypes.includes(fieldType);
  });
};

/**
 * Parse step data to get the inject contract label (if available)
 */
export const getStepLabel = (step: WorkflowStep): string => {
  if (!step.step_data) return `Step ${step.step_id.substring(0, 8)}`;
  try {
    const data = JSON.parse(step.step_data);
    return data.inject_title
      || data.event_name
      || data.injector_contract_label
      || data.label
      || `Step ${step.step_id.substring(0, 8)}`;
  } catch {
    return `Step ${step.step_id.substring(0, 8)}`;
  }
};

/**
 * Check if a step is an action step (has inject_injector_contract in step_data)
 */
export const isActionStep = (step: WorkflowStep): boolean => {
  if (!step.step_data) return false;
  try {
    const data = JSON.parse(step.step_data);
    return !!data.inject_injector_contract;
  } catch {
    return false;
  }
};

/**
 * Check if a step is an event step (not an action step)
 */
export const isEventStep = (step: WorkflowStep): boolean => !isActionStep(step);

/**
 * Check if a step has a DEPEND_ON condition
 */
export const hasDependOnCondition = (step: WorkflowStep): boolean =>
  step.step_conditions.some(c => c.condition_type === 'DEPEND_ON');

/**
 * Get root action steps: action steps with no DEPEND_ON condition
 */
export const getRootActionSteps = (steps: WorkflowStep[]): WorkflowStep[] =>
  steps.filter(s => isActionStep(s) && !hasDependOnCondition(s));

/**
 * Get root event steps: event steps with no DEPEND_ON condition (standalone events)
 */
export const getRootEventSteps = (steps: WorkflowStep[]): WorkflowStep[] =>
  steps.filter(s => isEventStep(s) && !hasDependOnCondition(s));

/**
 * Get child event steps: event steps that have a DEPEND_ON condition pointing to parentStepId
 */
export const getChildEventSteps = (steps: WorkflowStep[], parentStepId: string): WorkflowStep[] =>
  steps.filter(s =>
    isEventStep(s) && s.step_conditions.some(c => c.condition_type === 'DEPEND_ON' && c.step_from_id === parentStepId),
  );

/**
 * Get child action steps: action steps that have a DEPEND_ON condition pointing to parentStepId
 */
export const getChildActionSteps = (steps: WorkflowStep[], parentStepId: string): WorkflowStep[] =>
  steps.filter(s =>
    isActionStep(s) && s.step_conditions.some(c => c.condition_type === 'DEPEND_ON' && c.step_from_id === parentStepId),
  );

/**
 * Get field conditions for an event step (filters out DEPEND_ON and AND/OR root conditions)
 */
export const getEventFieldConditions = (step: WorkflowStep) =>
  step.step_conditions.filter(c => c.condition_type !== 'DEPEND_ON' && c.condition_type !== 'AND' && c.condition_type !== 'OR' && c.condition_key);

/**
 * Get the injector type stored in step_data (enriched at action creation)
 */
export const getStepInjectorType = (step: WorkflowStep): string | null => {
  if (!step.step_data) return null;
  try {
    const data = JSON.parse(step.step_data);
    return data.injector_type ?? null;
  } catch {
    return null;
  }
};

/**
 * Get attack pattern UUIDs stored in step_data (enriched at action creation)
 */
export const getStepAttackPatterns = (step: WorkflowStep): string[] => {
  if (!step.step_data) return [];
  try {
    const data = JSON.parse(step.step_data);
    return Array.isArray(data.injector_contract_attack_patterns)
      ? data.injector_contract_attack_patterns
      : [];
  } catch {
    return [];
  }
};

/**
 * Get all downstream step IDs reachable from a given step.
 * Follows two types of connections:
 *   1. DEPEND_ON edges (explicit structural links)
 *   2. Field type provisioning (action outputs match event condition keys)
 * Returns a Set of step IDs (excluding the source step itself).
 */
export const getDownstreamStepIds = (
  steps: WorkflowStep[],
  sourceStepId: string,
): Set<string> => {
  const downstream = new Set<string>();
  const queue = [sourceStepId];

  while (queue.length > 0) {
    const currentId = queue.shift()!;
    const currentStep = steps.find(s => s.step_id === currentId);
    if (!currentStep) continue;

    for (const step of steps) {
      if (downstream.has(step.step_id) || step.step_id === sourceStepId) continue;

      // 1. Explicit DEPEND_ON edge
      const dependsOnCurrent = step.step_conditions.some(
        c => c.condition_type === 'DEPEND_ON' && c.step_from_id === currentId,
      );

      // 2. Field type provisioning: if current step is an action and produces
      //    output types that match this event's condition keys
      let fieldMatch = false;
      if (isActionStep(currentStep) && isEventStep(step)) {
        const outputTypes = extractOutputTypesFromStepData(currentStep);
        if (outputTypes.length > 0) {
          fieldMatch = step.step_conditions.some(
            c => c.condition_key && outputTypes.includes(c.condition_key),
          );
        }
      }

      if (dependsOnCurrent || fieldMatch) {
        downstream.add(step.step_id);
        queue.push(step.step_id);
      }
    }
  }

  return downstream;
};

/**
 * Get all upstream step IDs that can trigger a given step.
 * Follows: actions whose output types match this event's condition keys,
 * and recursively their upstream providers.
 */
export const getUpstreamStepIds = (
  steps: WorkflowStep[],
  sourceStepId: string,
): Set<string> => {
  const upstream = new Set<string>();
  const queue = [sourceStepId];

  while (queue.length > 0) {
    const currentId = queue.shift()!;
    const currentStep = steps.find(s => s.step_id === currentId);
    if (!currentStep) continue;

    // 1. Follow DEPEND_ON edges backwards: find steps referenced by step_from_id
    for (const c of currentStep.step_conditions) {
      if (c.condition_type === 'DEPEND_ON' && c.step_from_id) {
        if (!upstream.has(c.step_from_id) && c.step_from_id !== sourceStepId) {
          upstream.add(c.step_from_id);
          queue.push(c.step_from_id);
        }
      }
    }

    // 2. Field provisioning backwards: if current is an event,
    //    find actions whose outputs match its condition keys
    if (isEventStep(currentStep)) {
      const conditionKeys = currentStep.step_conditions
        .filter(c => c.condition_key)
        .map(c => c.condition_key!);

      if (conditionKeys.length > 0) {
        for (const step of steps) {
          if (upstream.has(step.step_id) || step.step_id === sourceStepId) continue;
          if (!isActionStep(step)) continue;
          const outputTypes = extractOutputTypesFromStepData(step);
          const matches = conditionKeys.some(k => outputTypes.includes(k));
          if (matches) {
            upstream.add(step.step_id);
            queue.push(step.step_id);
          }
        }
      }
    }
  }

  return upstream;
};

/**
 * Get per-field scope map stored in step_data (field_scopes).
 * Returns e.g. { "port": "LOCAL", "ipv4": "GLOBAL" }.
 * Fields not present default to GLOBAL.
 */
export const getFieldScopes = (step: WorkflowStep): Record<string, string> => {
  if (!step.step_data) return {};
  try {
    const data = JSON.parse(step.step_data);
    return data.field_scopes ?? {};
  } catch {
    return {};
  }
};

// -- Data source binding (input/output mapping) --

export interface InputBinding {
  argumentKey: string;   // e.g., "target_host"
  inputType: string;     // e.g., "portscan"
  inputField: string | null; // e.g., "host" (null for scalar types)
  resolved: boolean;     // true if an upstream action produces this type
  providerStepId: string | null; // step_id of the upstream provider (first match)
}

/**
 * Extract raw (unresolved) data source bindings from a step.
 * Looks at two sources:
 *   1. Payload arguments with input_source (from step_data.payload_arguments)
 *   2. Contract content fields with input_source (from inject_content parsed fields)
 */
const extractRawBindings = (step: WorkflowStep): Omit<InputBinding, 'resolved' | 'providerStepId'>[] => {
  if (!step.step_data) return [];
  try {
    const data = JSON.parse(step.step_data);
    const bindings: Omit<InputBinding, 'resolved' | 'providerStepId'>[] = [];

    // 1. Payload arguments (stored in inject_content or step_data)
    const args: Array<{ key?: string; input_source?: InputSource }> =
      data.payload_arguments ?? data.inject_content?.payload_arguments ?? [];
    for (const arg of args) {
      if (arg.input_source?.input_type) {
        bindings.push({
          argumentKey: arg.key ?? '?',
          inputType: arg.input_source.input_type,
          inputField: arg.input_source.input_field ?? null,
        });
      }
    }

    // 2. Contract content fields (if stored)
    const fields: Array<{ key?: string; input_source?: InputSource }> =
      data.contract_fields ?? [];
    for (const field of fields) {
      if (field.input_source?.input_type) {
        bindings.push({
          argumentKey: field.key ?? '?',
          inputType: field.input_source.input_type,
          inputField: field.input_source.input_field ?? null,
        });
      }
    }

    return bindings;
  } catch {
    return [];
  }
};

/**
 * Extract input bindings from a step, resolved against the full step list.
 * Walks upstream (DEPEND_ON + field provisioning) to find providers.
 */
export const extractInputBindings = (step: WorkflowStep, allSteps: WorkflowStep[]): InputBinding[] => {
  const raw = extractRawBindings(step);
  if (raw.length === 0) return [];

  // Collect upstream step IDs
  const upstreamIds = getUpstreamStepIds(allSteps, step.step_id);

  // For each binding, check if any upstream action produces the needed output type
  return raw.map((binding) => {
    let providerStepId: string | null = null;
    for (const uid of upstreamIds) {
      const upstream = allSteps.find(s => s.step_id === uid);
      if (!upstream || !isActionStep(upstream)) continue;
      const outputTypes = extractOutputTypesFromStepData(upstream);
      if (outputTypes.includes(binding.inputType)) {
        providerStepId = upstream.step_id;
        break;
      }
    }
    return {
      ...binding,
      resolved: providerStepId !== null,
      providerStepId,
    };
  });
};

/**
 * Format an input binding for display: "portscan.host" or "ipv4" (scalar).
 */
export const formatBinding = (binding: InputBinding): string =>
  binding.inputField
    ? `${binding.inputType}.${binding.inputField}`
    : binding.inputType;
