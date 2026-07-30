import { type Edge, MarkerType, type Node } from '@xyflow/react';

import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import type { ConditionOutput, EventOutput, KillChainPhase, StepOutput } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import {
  type ComparisonOperator,
  type ConditionGroup,
  type ConditionKeyType,
  createEmptyCondition,
  createEmptyGroup,
  type EventCondition,
  formatConditionKeyLabel,
  generateId,
  type LogicalOperator,
} from './events/event-types';
import type { ActionMeta, EventMeta } from './types';

export interface MapperConditionRow {
  condition_key_types?: string[];
  condition_key_type?: string;
  condition_key_subtype?: string;
  condition_key: string;
  condition_value?: string;
  condition_mapping_type: string;
}

export const resolveConditionKeyTypes = (condition: Record<string, unknown>): string[] => {
  const keyTypes = condition.condition_key_types as string[] | undefined;
  if (keyTypes && keyTypes.length > 0) {
    return keyTypes;
  }
  const legacyKeyType = condition.condition_key_type;
  if (typeof legacyKeyType === 'string' && legacyKeyType.length > 0) {
    return [legacyKeyType];
  }
  return ['text'];
};

// Layout design tokens for tactic groups (px)
const TACTIC_WIDTH = 280; // Width of each tactic column
const TACTIC_GAP = 80; // Horizontal gap between tactic columns
const ACTION_WIDTH = 248; // Width of a single action node
const ACTION_HEIGHT = 70; // Height of a single action node
const ACTION_GAP = 12; // Vertical gap between action nodes
const GROUP_PADDING_TOP = 40; // Top padding inside a tactic group
const GROUP_PADDING_BOTTOM = 20; // Bottom padding inside a tactic group
const GROUP_PADDING_X = (TACTIC_WIDTH - ACTION_WIDTH) / 2; // Horizontal padding to center actions

export { ACTION_WIDTH, GROUP_PADDING_X, TACTIC_WIDTH };

/**
 * Compute tactic group height dynamically based on the number of action nodes inside it.
 */
export const computeGroupHeight = (childCount: number) =>
  GROUP_PADDING_TOP + childCount * (ACTION_HEIGHT + ACTION_GAP) + GROUP_PADDING_BOTTOM;

/**
 * Compute the Y position of an action node within its parent tactic group.
 */
export const computeActionY = (index: number) =>
  GROUP_PADDING_TOP + index * (ACTION_HEIGHT + ACTION_GAP);

// -- Data builders --

/**
 * Parse steps API response into ActionMeta records keyed by step ID.
 */
export const buildActionMetas = (steps: StepOutput[]): Record<string, ActionMeta> => {
  const metas: Record<string, ActionMeta> = {};

  steps
    .filter((s): s is StepOutput & { step_id: string } => !!s.step_id)
    .forEach((s) => {
      const data = s.step_data as Record<string, unknown> | undefined;
      const rawContract = data?.inject_injector_contract;
      const contract = typeof rawContract === 'string'
        ? rawContract
        : (rawContract as Record<string, unknown>)?.injector_contract_id as string ?? '';

      // Extract kill_chain_phase_ids from embedded attack pattern objects
      const rawAttackPatterns = (data?.inject_attack_patterns ?? []) as Array<{ attack_pattern_kill_chain_phases?: string[] }>;
      const killChainPhaseIds = rawAttackPatterns
        .flatMap(ap => ap.attack_pattern_kill_chain_phases ?? []);

      // Icon resolution
      const contractObj = typeof rawContract === 'object' ? rawContract as Record<string, unknown> : undefined;
      const injectorType = contractObj?.injector_contract_injector_type as string | undefined;
      const payload = contractObj?.injector_contract_payload as Record<string, unknown> | undefined;
      const payloadType = payload?.payload_type as string | undefined;
      const payloadCollectorType = payload?.payload_collector_type as string | undefined;

      metas[s.step_id] = {
        inject_title: (data?.inject_title as string) ?? `Step ${s.step_id.slice(0, 6)}`,
        inject_description: (data?.inject_description as string) ?? '',
        inject_injector_contract: contract,
        inject_injector: injectorType,
        inject_payload_type: payloadType,
        inject_payload_collector_type: payloadCollectorType,
        inject_content: (data?.inject_content as Record<string, unknown>) ?? {},
        inject_attack_patterns_ids: (data?.inject_attack_patterns_ids as string[]) ?? [],
        inject_kill_chain_phase_ids: killChainPhaseIds,
        inject_assets: (data?.inject_assets as string[]) ?? [],
        inject_asset_objects: [],
        step_condition_ids: s.step_condition_ids ?? [],
        step_conditions: (s.step_mapper_conditions ?? []).map((mc) => {
          const resolvedKeyTypes = resolveConditionKeyTypes(mc as unknown as Record<string, unknown>);
          return {
            condition_key_types: resolvedKeyTypes,
            condition_key: mc.condition_key ?? '',
            condition_value: mc.condition_value,
            condition_mapping_type: mc.condition_mapping_type ?? 'GLOBAL',
          };
        }),
        step_output_types: s.step_output_types ?? [],
        contract_fields: [],
      };
    });

  return metas;
};

export interface OutputProviderEntry {
  stepId: string;
  actionTitle: string;
  injectorType?: string;
  payloadType?: string;
  isPayload?: boolean;
}

/**
 * Build the inverted map: output type → list of actions that produce it.
 * Used to populate the OutputProvidersContext.
 */
export const buildOutputProvidersMap = (
  actionMetas: Record<string, ActionMeta>,
): Record<string, OutputProviderEntry[]> => {
  const map: Record<string, OutputProviderEntry[]> = {};
  for (const [stepId, meta] of Object.entries(actionMetas)) {
    for (const outputType of meta.step_output_types) {
      if (!outputType) continue;
      if (!map[outputType]) map[outputType] = [];
      if (!map[outputType].some(p => p.stepId === stepId)) {
        map[outputType].push({
          stepId,
          actionTitle: meta.inject_title,
          injectorType: meta.inject_injector,
          payloadType: meta.inject_payload_collector_type ?? meta.inject_payload_type,
          isPayload: !!meta.inject_payload_type,
        });
      }
    }
  }
  return map;
};

/**
 * Reconstruct a ConditionGroup tree from a flat list of ConditionOutput nodes.
 * The tree is built by finding the root (no parent), then recursively building children.
 */
const reconstructConditionGroups = (
  allConditions: ConditionOutput[],
): {
  groups: ConditionGroup[];
  groupOperators: LogicalOperator[];
} => {
  if (allConditions.length === 0) {
    return {
      groups: [createEmptyGroup('AND')],
      groupOperators: [],
    };
  }

  const LOGICAL_TYPES: Set<string> = new Set(['AND', 'OR']);

  // Index by ID for O(1) lookup
  const byId: Record<string, ConditionOutput> = {};
  for (const c of allConditions) {
    if (c.condition_id) byId[c.condition_id] = c;
  }

  // Group children by parent_id
  const childrenOf: Record<string, ConditionOutput[]> = {};
  for (const c of allConditions) {
    const parentId = c.condition_parent_id ?? '__root__';
    childrenOf[parentId] = childrenOf[parentId] ?? [];
    childrenOf[parentId].push(c);
  }

  const buildGroup = (groupNode: ConditionOutput): ConditionGroup => {
    const groupId = groupNode.condition_id ?? generateId();
    const children = childrenOf[groupId] ?? [];
    const conditions: EventCondition[] = [];
    const subGroups: ConditionGroup[] = [];

    for (const child of children) {
      const isLogical = LOGICAL_TYPES.has(child.condition_type ?? '');
      if (isLogical) {
        subGroups.push(buildGroup(child));
      } else {
        const conditionKeyTypes = resolveConditionKeyTypes(child as unknown as Record<string, unknown>);
        conditions.push({
          id: child.condition_id ?? generateId(),
          field: conditionKeyTypes[0] as ConditionKeyType,
          operator: (child.condition_type as ComparisonOperator) ?? 'IN',
          value: child.condition_value ?? '',
          caseSensitive: child.condition_case_sensitive !== false,
        });
      }
    }

    return {
      id: groupId,
      operator: (groupNode.condition_type as LogicalOperator) ?? 'AND',
      conditions: conditions.length > 0 ? conditions : [createEmptyCondition()],
      subGroups,
    };
  };

  // Find roots: conditions with no parent
  const roots = allConditions.filter(c => !c.condition_parent_id);

  if (roots.length === 0) {
    return {
      groups: [createEmptyGroup('AND')],
      groupOperators: [],
    };
  }

  // Single root logical node → its children are top-level groups
  if (roots.length === 1 && LOGICAL_TYPES.has(roots[0].condition_type ?? '')) {
    const rootNode = roots[0];
    const rootId = rootNode.condition_id ?? '';
    const topChildren = childrenOf[rootId] ?? [];

    const topGroups = topChildren.filter(c => LOGICAL_TYPES.has(c.condition_type ?? ''));
    const topConditions = topChildren.filter(c => !LOGICAL_TYPES.has(c.condition_type ?? ''));

    if (topGroups.length > 0) {
      // Multiple groups under root: rootNode operator goes between them
      const groups = topGroups.map(g => buildGroup(g));
      const groupOperators: LogicalOperator[] = groups.slice(1).map(
        () => (rootNode.condition_type as LogicalOperator) ?? 'AND',
      );
      return {
        groups,
        groupOperators,
      };
    }

    // Root has direct leaf conditions → single group
    const group: ConditionGroup = {
      id: rootId,
      operator: (rootNode.condition_type as LogicalOperator) ?? 'AND',
      conditions: topConditions.map((c) => {
        const conditionKeyTypes = resolveConditionKeyTypes(c as unknown as Record<string, unknown>);
        return {
          id: c.condition_id ?? generateId(),
          field: conditionKeyTypes[0] as ConditionKeyType,
          operator: (c.condition_type as ComparisonOperator) ?? 'IN',
          value: c.condition_value ?? '',
          caseSensitive: c.condition_case_sensitive !== false,
        };
      }),
      subGroups: [],
    };
    return {
      groups: [group],
      groupOperators: [],
    };
  }

  // Multiple roots (each a logical group)
  const groups = roots.filter(r => LOGICAL_TYPES.has(r.condition_type ?? '')).map(buildGroup);
  return {
    groups: groups.length > 0 ? groups : [createEmptyGroup('AND')],
    groupOperators: groups.slice(1).map(() => 'AND' as LogicalOperator),
  };
};

/**
 * Parse events API response into EventMeta records and preliminary event nodes.
 */
export const buildEventData = (events: EventOutput[]): {
  eventMetas: Record<string, EventMeta>;
  eventNodes: Node[];
} => {
  const eventMetas: Record<string, EventMeta> = {};

  const eventNodes: Node[] = events.map((e, i) => {
    const allConditions = e.event_conditions ?? [];
    const { groups, groupOperators } = reconstructConditionGroups(allConditions);

    eventMetas[e.event_id] = {
      eventId: e.event_id,
      formData: {
        name: e.event_name ?? '',
        description: e.event_description ?? '',
        groupOperators,
        conditionGroups: groups,
      },
    };

    return {
      id: e.event_id,
      type: 'event' as const,
      position: {
        x: 50,
        y: 100 + i * 140,
      },
      data: { label: e.event_name },
    };
  });

  return {
    eventMetas,
    eventNodes,
  };
};

/**
 * Fetch injector contract fields for each action that has a contract.
 * Mutates the input actionMetas in place for performance.
 */
export const enrichActionMetasWithContracts = async (
  actionMetas: Record<string, ActionMeta>,
): Promise<Record<string, ActionMeta>> => {
  const contractIds = Array.from(new Set(
    Object.values(actionMetas)
      .map(m => m.inject_injector_contract)
      .filter((id): id is string => !!id),
  ));

  const contractFieldsMap: Record<string, ContractElement[]> = {};

  await Promise.all(contractIds.map(async (cid) => {
    try {
      const res = await directFetchInjectorContract(cid) as { data: { injector_contract_content?: string } };
      if (res.data?.injector_contract_content) {
        const parsed = JSON.parse(res.data.injector_contract_content);
        contractFieldsMap[cid] = (parsed.fields ?? []) as ContractElement[];
      }
    } catch {
      // contract not found or content not parseable
    }
  }));

  for (const meta of Object.values(actionMetas)) {
    if (meta.inject_injector_contract && contractFieldsMap[meta.inject_injector_contract]) {
      meta.contract_fields = contractFieldsMap[meta.inject_injector_contract];
    }
  }

  return actionMetas;
};

interface BuildTacticForStepParams {
  actionMetas: Record<string, ActionMeta>;
  killChainPhasesMap: Record<string, KillChainPhase>;
  fallbackTactic: string;
}

/**
 * Resolve the tactic name for each step to determine its column grouping.
 * Use the first Kill Chain Phase (sorted by order) from associated Attack Patterns.
 * Fallback: Use the provided 'fallbackTactic' (e.g., "Other").
 * @returns A map of stepId to Resolved Tactic Name.
 */
export const buildTacticForStep = ({
  actionMetas,
  killChainPhasesMap,
  fallbackTactic,
}: BuildTacticForStepParams): Record<string, string> => {
  const tacticForStep: Record<string, string> = {};

  for (const [stepId, meta] of Object.entries(actionMetas)) {
    if (meta.inject_kill_chain_phase_ids.length > 0) {
      // Resolve tactic from Kill Chain Phases of associated Attack Patterns
      const phases = meta.inject_kill_chain_phase_ids
        .map(pid => killChainPhasesMap[pid])
        .filter((p): p is KillChainPhase => !!p);

      if (phases.length > 0) {
        // Sort by order to get the most relevant phase
        phases.sort((a, b) => (a.phase_order ?? 0) - (b.phase_order ?? 0));
        tacticForStep[stepId] = phases[0].phase_name;
      } else {
        tacticForStep[stepId] = fallbackTactic;
      }
    } else {
      tacticForStep[stepId] = fallbackTactic;
    }
  }

  return tacticForStep;
};

/**
 * Build tactic group nodes and position action nodes inside their respective tactic column.
 */
export const buildTacticNodes = (
  tacticForStep: Record<string, string>,
  actionMetas: Record<string, ActionMeta>,
  killChainPhasesMap: Record<string, KillChainPhase>,
): {
  groupNodes: Node[];
  actionNodes: Node[];
} => {
  // 1. Determine the global order of tactics (columns) based on official MITRE order
  const tacticOrder: Record<string, number> = {};
  for (const phase of Object.values(killChainPhasesMap)) {
    // Keep the lowest order (leftmost) if a tactic appears multiple times
    if (tacticOrder[phase.phase_name] === undefined || (phase.phase_order ?? 99) < tacticOrder[phase.phase_name]) {
      tacticOrder[phase.phase_name] = phase.phase_order ?? 99;
    }
  }

  // 2. Extract and sort unique tactics present in this specific workflow
  const uniqueTactics = Array.from(new Set(Object.values(tacticForStep)));
  uniqueTactics.sort((a, b) => (tacticOrder[a] ?? 99) - (tacticOrder[b] ?? 99));

  const groupNodes: Node[] = [];
  const actionNodes: Node[] = [];

  // 3. Loop through each tactic to create its column (group) and position its actions
  for (let tacticIdx = 0; tacticIdx < uniqueTactics.length; tacticIdx++) {
    const tactic = uniqueTactics[tacticIdx];
    const groupId = `tactic-${tactic.replace(/\s+/g, '-').toLowerCase()}`;

    // Get all step IDs (actions) belonging to this tactic
    const stepsInTactic = Object.entries(tacticForStep)
      .filter(([, t2]) => t2 === tactic)
      .map(([sid]) => sid);

    // 4. Create the "Group" node
    groupNodes.push({
      id: groupId,
      type: 'tacticGroup',
      position: {
        x: tacticIdx * (TACTIC_WIDTH + TACTIC_GAP), // Horizontal offset based on tactic index
        y: 0,
      },
      data: { label: tactic },
      style: {
        width: TACTIC_WIDTH,
        height: computeGroupHeight(stepsInTactic.length), // Dynamic height based on number of actions
      },
    });

    // 5. Position each action inside its tactic column
    for (let actionIdx = 0; actionIdx < stepsInTactic.length; actionIdx++) {
      const stepId = stepsInTactic[actionIdx];
      const meta = actionMetas[stepId];
      actionNodes.push({
        id: stepId,
        type: 'action',
        position: {
          x: GROUP_PADDING_X,
          y: computeActionY(actionIdx),
        },
        parentId: groupId, // Link action to group for collective movement
        extent: 'parent' as const, // Prevents dragging action outside its column
        data: {
          label: meta.inject_title,
          injectorType: meta.inject_injector,
          payloadType: meta.inject_payload_collector_type ?? meta.inject_payload_type,
          isPayload: !!meta.inject_payload_type,
        },
      });
    }
  }

  return {
    groupNodes,
    actionNodes,
  };
};

// Layout design tokens for event column (px)
const EVENT_NODE_HEIGHT = 70;
const EVENT_GAP = 20;

/**
 * Position event nodes to the left of the first tactic group.
 */
export const positionEventNodes = (eventNodes: Node[]): Node[] =>
  eventNodes.map((en, i) => ({
    ...en,
    position: {
      x: -300,
      y: 50 + i * (EVENT_NODE_HEIGHT + EVENT_GAP),
    },
    style: { width: 180 },
  }));

/**
 * Reconstruct edges from step_condition_ids linking events to actions.
 */
export const buildEdges = (
  actionMetas: Record<string, ActionMeta>,
  eventMetas: Record<string, EventMeta>,
): Edge[] => {
  const edges: Edge[] = [];
  for (const [stepId, meta] of Object.entries(actionMetas)) {
    for (const condId of meta.step_condition_ids) {
      if (eventMetas[condId]) {
        edges.push({
          id: `${condId}-${stepId}`,
          source: condId,
          target: stepId,
          type: 'deletable',
          markerEnd: { type: MarkerType.ArrowClosed },
        });
      }
    }
  }
  return edges;
};

// -- Informational data-flow visualization (selected event) --

/** Recursively collect every leaf-condition field referenced by an event's condition tree. */
export const collectEventFields = (group: ConditionGroup): string[] => [
  ...group.conditions.map(c => c.field),
  ...group.subGroups.flatMap(collectEventFields),
];

/**
 * Build the read-only dotted arrows from every provider action into the currently selected
 * event. One arrow per provider action, carrying the matching output type(s) as label data.
 * These are informational only — they are never persisted and do not represent a real link.
 *
 * @param selectedEventId the currently selected event id (or null → no arrows)
 * @param eventMetas all events keyed by id
 * @param outputProviders inverted map "output type → provider actions"
 * @param arrowColor the stroke/marker color (resolved from the theme by the caller)
 */
export const buildInformationalEdges = (
  selectedEventId: string | null,
  eventMetas: Record<string, EventMeta>,
  outputProviders: Record<string, OutputProviderEntry[]>,
  arrowColor: string,
): Edge[] => {
  if (!selectedEventId) return [];
  const meta = eventMetas[selectedEventId];
  if (!meta) return [];

  // All distinct condition fields referenced by the event.
  const fields = new Set(meta.formData.conditionGroups.flatMap(collectEventFields).filter(Boolean));

  // Aggregate, per provider action, the set of matching output types it feeds.
  const typesByAction = new Map<string, Set<string>>();
  for (const field of fields) {
    for (const provider of outputProviders[field] ?? []) {
      const current = typesByAction.get(provider.stepId) ?? new Set<string>();
      current.add(field);
      typesByAction.set(provider.stepId, current);
    }
  }

  return Array.from(typesByAction.entries()).map(([stepId, types]) => ({
    id: `info-${stepId}-${selectedEventId}`,
    source: stepId,
    sourceHandle: 'action-source-right',
    target: selectedEventId,
    targetHandle: 'event-target',
    type: 'informational',
    selectable: false,
    deletable: false,
    markerEnd: {
      type: MarkerType.ArrowClosed,
      color: arrowColor,
    },
    data: { outputLabel: Array.from(types).map(formatConditionKeyLabel).join(', ') },
  }));
};

export interface EventPath {
  /** Ids of every step (provider or consumer) involved in the selected event's data flow. */
  highlightedStepIds: Set<string>;
  /** 1-based badge index per step id. */
  stepPathIndex: Record<string, number>;
  /** 1-based badge index of the event itself (sits between providers and consumers). */
  eventPathIndex: number | undefined;
}

/**
 * Compute the data-flow path for the selected event: provider steps (which produce outputs
 * feeding the event) first, then the event itself, then consumer steps (which use the event
 * as an execution condition). Each element gets a 1-based index rendered as a numbered badge
 * (e.g. provider = 1, event = 2, consumer step = 3).
 *
 * @param selectedEventId the currently selected event id (or null → empty path)
 * @param informationalEdges the informational edges produced by {@link buildInformationalEdges}
 * @param actionMetas all steps keyed by id
 */
export const buildEventPath = (
  selectedEventId: string | null,
  informationalEdges: Edge[],
  actionMetas: Record<string, ActionMeta>,
): EventPath => {
  if (!selectedEventId) {
    return {
      highlightedStepIds: new Set<string>(),
      stepPathIndex: {},
      eventPathIndex: undefined,
    };
  }

  // Provider steps: actions producing an output matching one of the event's condition fields.
  const providerIds = Array.from(new Set(informationalEdges.map(e => e.source)));
  // Consumer steps: actions using this event as an execution condition (real link).
  const consumerIds = Object.entries(actionMetas)
    .filter(([, meta]) => meta.step_condition_ids.includes(selectedEventId))
    .map(([stepId]) => stepId);

  const index: Record<string, number> = {};
  let counter = 0;

  // 1..n — provider steps come first.
  for (const id of providerIds) {
    if (!(id in index)) index[id] = ++counter;
  }
  // n+1 — the event sits between producers and consumers.
  const eventPathIndex = ++counter;
  // n+2.. — consumer steps (skip any already counted as a provider).
  for (const id of consumerIds) {
    if (!(id in index)) index[id] = ++counter;
  }

  return {
    highlightedStepIds: new Set(Object.keys(index)),
    stepPathIndex: index,
    eventPathIndex,
  };
};
