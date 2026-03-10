import type { AttackPattern, KillChainPhase } from '../../../../../utils/api-types';
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

// -- Auto-binding resolution (provider → event → consumer) --

export interface BindingProvider {
  providerStepId: string;
  providerLabel: string;
  eventStepId: string;
  eventLabel: string;
}

export interface InputBinding {
  argumentKey: string;       // e.g., "target"
  inputType: string;         // e.g., "portscan"
  inputField: string | null; // e.g., "host"
  bound: boolean;            // true if auto-bound to upstream provider(s)
  providers: BindingProvider[];
  scope: string;             // "LOCAL" or "GLOBAL"
}

interface RawBinding {
  argumentKey: string;
  candidates: { inputType: string; inputField: string | null }[];
}

/**
 * Extract input_sources declarations from a step's contract_fields and payload_arguments.
 * Each argument may have multiple compatible sources (input_sources array).
 */
const extractRawBindings = (step: WorkflowStep): RawBinding[] => {
  if (!step.step_data) return [];
  try {
    const data = JSON.parse(step.step_data);
    const bindings: RawBinding[] = [];

    const extractFromList = (items: Array<{ key?: string; input_sources?: InputSource[]; input_source?: InputSource }>) => {
      for (const item of items) {
        // Support both input_sources (array) and input_source (legacy single)
        const sources = item.input_sources
          ?? (item.input_source ? [item.input_source] : []);
        if (sources.length > 0) {
          bindings.push({
            argumentKey: item.key ?? '?',
            candidates: sources
              .filter(s => s.input_type)
              .map(s => ({ inputType: s.input_type, inputField: s.input_field ?? null })),
          });
        }
      }
    };

    extractFromList(data.payload_arguments ?? data.inject_content?.payload_arguments ?? []);
    extractFromList(data.contract_fields ?? []);

    return bindings;
  } catch {
    return [];
  }
};

/**
 * Find the parent event step for a given step (the event it DEPEND_ON).
 */
export const getParentEventStep = (step: WorkflowStep, allSteps: WorkflowStep[]): WorkflowStep | null => {
  for (const c of step.step_conditions) {
    if (c.condition_type === 'DEPEND_ON' && c.step_from_id) {
      const parent = allSteps.find(s => s.step_id === c.step_from_id);
      if (parent && isEventStep(parent)) return parent;
    }
  }
  return null;
};

/**
 * Get the output types that flow through an event (from its condition_key values).
 */
export const getEventFlowTypes = (eventStep: WorkflowStep): string[] => {
  const types = new Set<string>();
  for (const c of eventStep.step_conditions) {
    if (c.condition_key) types.add(c.condition_key);
  }
  return [...types];
};

/**
 * Resolve auto-bindings for a step.
 * Walks the full upstream chain transitively. For each argument, tries every
 * candidate input_source against all upstream actions (through events).
 * Data accumulates along the chain — an action deep in the chain can still
 * access outputs from the root action.
 */
export const extractInputBindings = (step: WorkflowStep, allSteps: WorkflowStep[]): InputBinding[] => {
  const rawBindings = extractRawBindings(step);
  if (rawBindings.length === 0) return [];

  const fieldScopes = getFieldScopes(step);
  const upstreamIds = getUpstreamStepIds(allSteps, step.step_id);

  // Collect all upstream events and actions
  const upstreamEvents: WorkflowStep[] = [];
  const upstreamActions: WorkflowStep[] = [];
  for (const uid of upstreamIds) {
    const upstream = allSteps.find(s => s.step_id === uid);
    if (!upstream) continue;
    if (isEventStep(upstream)) upstreamEvents.push(upstream);
    else if (isActionStep(upstream)) upstreamActions.push(upstream);
  }

  return rawBindings.map((raw) => {
    const providers: BindingProvider[] = [];
    const seen = new Set<string>();

    // Try each candidate source for this argument
    for (const candidate of raw.candidates) {
      // Find upstream actions that produce this candidate type
      for (const action of upstreamActions) {
        if (seen.has(action.step_id)) continue;
        const outputTypes = extractOutputTypesFromStepData(action);
        if (!outputTypes.includes(candidate.inputType)) continue;

        // Find an event in the chain that filters on this type (bridge)
        const event = upstreamEvents.find(e =>
          getEventFlowTypes(e).includes(candidate.inputType),
        );

        if (event) {
          seen.add(action.step_id);
          providers.push({
            providerStepId: action.step_id,
            providerLabel: getStepLabel(action),
            eventStepId: event.step_id,
            eventLabel: getStepLabel(event),
          });
        }
      }
    }

    // Pick the first matched candidate as the display type
    const matchedCandidate = providers.length > 0
      ? raw.candidates.find(c =>
        upstreamActions.some(a => extractOutputTypesFromStepData(a).includes(c.inputType)),
      ) ?? raw.candidates[0]
      : raw.candidates[0];

    return {
      argumentKey: raw.argumentKey,
      inputType: matchedCandidate?.inputType ?? '?',
      inputField: matchedCandidate?.inputField ?? null,
      bound: providers.length > 0,
      providers,
      scope: fieldScopes[raw.argumentKey] ?? 'LOCAL',
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

// -- Kill chain phase resolution for swimlane layout --

const UTILITY_PHASE: KillChainPhase = {
  phase_id: '__utility__',
  phase_name: 'Utility',
  phase_shortname: 'utility',
  phase_kill_chain_name: 'openaev',
  phase_external_id: 'utility',
  phase_order: -1,
  phase_created_at: '',
  phase_updated_at: '',
};

/**
 * Resolve the primary kill chain phase for an action step.
 * Picks the most advanced phase (max phase_order) across all attack patterns.
 * Returns UTILITY_PHASE for actions without attack patterns, null for events.
 */
export const getStepKillChainPhase = (
  step: WorkflowStep,
  attackPatternsMap: Record<string, AttackPattern>,
  killChainPhasesMap: Record<string, KillChainPhase>,
): KillChainPhase | null => {
  if (!isActionStep(step)) return null;

  const attackPatternIds = getStepAttackPatterns(step);
  let bestPhase: KillChainPhase | null = null;

  for (const apId of attackPatternIds) {
    const ap = attackPatternsMap[apId];
    if (!ap?.attack_pattern_kill_chain_phases) continue;
    for (const phaseId of ap.attack_pattern_kill_chain_phases) {
      const phase = killChainPhasesMap[phaseId];
      if (!phase) continue;
      if (!bestPhase || (phase.phase_order ?? 0) > (bestPhase.phase_order ?? 0)) {
        bestPhase = phase;
      }
    }
  }

  return bestPhase ?? UTILITY_PHASE;
};

/**
 * Get the sorted list of kill chain phases actually used by action steps,
 * with UTILITY_PHASE prepended if any action has no attack patterns.
 */
export const getUsedPhases = (
  steps: WorkflowStep[],
  attackPatternsMap: Record<string, AttackPattern>,
  killChainPhasesMap: Record<string, KillChainPhase>,
): KillChainPhase[] => {
  const phaseMap = new Map<string, KillChainPhase>();

  for (const step of steps) {
    const phase = getStepKillChainPhase(step, attackPatternsMap, killChainPhasesMap);
    if (phase) {
      phaseMap.set(phase.phase_id, phase);
    }
  }

  return [...phaseMap.values()].sort(
    (a, b) => (a.phase_order ?? 0) - (b.phase_order ?? 0),
  );
};

export { UTILITY_PHASE };
