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
const TACTIC_GAP = 260; // Horizontal gap between tactic columns (wide enough to host an event lane on the left of each column)
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
const EVENT_NODE_HEIGHT = 100; // Approx. rendered height of an event node (bolt icon + box + "+" button)
const EVENT_GAP = 40;
const EVENT_WIDTH = 180; // Width of an event node
const EVENT_TO_GROUP_GAP = 50; // Horizontal gap between an event and the tactic column it feeds

/** Placement hint for an event: the tactic column it sits left of, and the Y of its target step. */
export interface EventPlacement {
  groupX: number;
  anchorY: number;
}

/**
 * Resolve, for each event, where it should sit:
 * - `groupX`: X of the tactic group (column) it should be placed to the left of. An event is
 *   linked to a step (via the step's `step_condition_ids`); that step belongs to a tactic column.
 *   When an event feeds several tactics, the leftmost (earliest) one wins.
 * - `anchorY`: the absolute vertical center of the linked step, used to align the event with its
 *   target so the connecting arrow stays horizontal (fewer crossings).
 */
export const buildEventToGroupX = (
  actionMetas: Record<string, ActionMeta>,
  groupNodes: Node[],
  actionNodes: Node[],
): Record<string, EventPlacement> => {
  const groupXById: Record<string, number> = {};
  const groupYById: Record<string, number> = {};
  for (const g of groupNodes) {
    groupXById[g.id] = g.position.x;
    groupYById[g.id] = g.position.y;
  }

  const stepToGroupX: Record<string, number> = {};
  const stepToY: Record<string, number> = {};
  for (const a of actionNodes) {
    if (a.parentId && groupXById[a.parentId] !== undefined) {
      stepToGroupX[a.id] = groupXById[a.parentId];
      // Absolute vertical center of the step (parent group Y + relative Y + half node height),
      // so events can be aligned to it regardless of the group's own Y offset.
      stepToY[a.id] = (groupYById[a.parentId] ?? 0) + a.position.y + ACTION_HEIGHT / 2;
    }
  }

  const placement: Record<string, EventPlacement> = {};
  for (const [stepId, meta] of Object.entries(actionMetas)) {
    const gx = stepToGroupX[stepId];
    if (gx === undefined) continue;
    const y = stepToY[stepId] ?? 0;
    for (const eventId of meta.step_condition_ids) {
      const current = placement[eventId];
      // Prefer the leftmost column; within the same column, keep the topmost step as anchor.
      if (!current || gx < current.groupX || (gx === current.groupX && y < current.anchorY)) {
        placement[eventId] = {
          groupX: gx,
          anchorY: y,
        };
      }
    }
  }

  return placement;
};

/**
 * Position event nodes to the left of the tactic group they feed.
 *
 * <p>Each event is placed at its target step's vertical center so the event→step arrow is
 * horizontal. Events feeding the same column are processed top-to-bottom by anchor; when two would
 * overlap, the lower one is pushed down by the minimum gap ("align, then de-overlap"). Events not
 * linked to any step fall back to the far-left lane (X = 0) and are appended below the anchored ones.
 */
export const positionEventNodes = (
  eventNodes: Node[],
  placement: Record<string, EventPlacement> = {},
): Node[] => {
  // Bucket events per lane (column X). Each event remembers the absolute center of the step it
  // feeds (anchorY); orphan events (no linked step) carry a null anchor and sink to the bottom.
  const lanes: Record<number, {
    node: Node;
    anchorY: number | null;
  }[]> = {};
  for (const en of eventNodes) {
    const p = placement[en.id];
    const x = (p?.groupX ?? 0) - EVENT_WIDTH - EVENT_TO_GROUP_GAP;
    (lanes[x] ??= []).push({
      node: en,
      anchorY: p?.anchorY ?? null,
    });
  }

  const positioned: Node[] = [];
  for (const [x, items] of Object.entries(lanes)) {
    // Order by the target step's center, so arrows keep their vertical order; orphans go last.
    items.sort(
      (a, b) => (a.anchorY ?? Number.POSITIVE_INFINITY) - (b.anchorY ?? Number.POSITIVE_INFINITY),
    );

    // Align each event to its step's center, cascading down only to resolve overlaps. `cursor` is
    // the lowest Y still available; it starts at the top padding, so events never rise above it.
    let cursor = GROUP_PADDING_TOP;
    for (const { node, anchorY } of items) {
      const desiredTop = anchorY === null ? cursor : anchorY - EVENT_NODE_HEIGHT / 2;
      const y = Math.max(desiredTop, cursor);
      positioned.push({
        ...node,
        position: {
          x: Number(x),
          y,
        },
        style: { width: EVENT_WIDTH },
      });
      cursor = y + EVENT_NODE_HEIGHT + EVENT_GAP;
    }
  }
  return positioned;
};

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

/** A node in the selected event's flow line (either an action or an event). */
interface FlowNode {
  kind: 'action' | 'event';
  id: string;
}

/** Shared lookups reused across the backward chain traversal. */
interface FlowContext {
  eventMetas: Record<string, EventMeta>;
  actionMetas: Record<string, ActionMeta>;
}

/** Distinct condition fields (output types) referenced by an event's condition tree. */
const eventFields = (
  eventId: string,
  eventMetas: Record<string, EventMeta>,
): string[] => {
  const meta = eventMetas[eventId];
  if (!meta) return [];
  return Array.from(
    new Set(meta.formData.conditionGroups.flatMap(collectEventFields).filter(Boolean)),
  );
};

/** Actions triggered by the given event (its execution conditions reference the event id). */
const consumersOf = (eventId: string, ctx: FlowContext): string[] =>
  Object.entries(ctx.actionMetas)
    .filter(([, meta]) => meta.step_condition_ids.includes(eventId))
    .map(([stepId]) => stepId);

/** Actions whose output types include at least one type the given event checks (type production). */
const typeProducersOf = (eventId: string, ctx: FlowContext): string[] => {
  const fields = new Set(eventFields(eventId, ctx.eventMetas));
  if (fields.size === 0) return [];
  return Object.entries(ctx.actionMetas)
    .filter(([, meta]) => (meta.step_output_types ?? []).some(t => fields.has(t)))
    .map(([stepId]) => stepId);
};

/** The event that triggers the given action, if any (first existing execution condition). */
const triggerEventOf = (actionId: string, ctx: FlowContext): string | null =>
  (ctx.actionMetas[actionId]?.step_condition_ids ?? []).find(e => !!ctx.eventMetas[e]) ?? null;

/** A resolved backward line plus whether it reached an event-less start action. */
interface FeedingChain {
  nodes: FlowNode[];
  reachedStart: boolean;
}

/**
 * Resolve the line of nodes that come before `eventId` (exclusive), walking backward via
 * type production and trigger edges until an event-less start action.
 *
 * Rather than guess an ordering, we take the first producer that completes a
 * link back to an event-less start: candidate producers are explored depth-first in their
 * natural order, and the first branch reaching a start wins.
 *
 * @param eventId the event whose producer line we resolve
 * @param ctx shared lookups
 * @param visitedEvents events already on the current line (cycle guard)
 * @param visitedActions actions already on the current line (cycle guard)
 * @returns the forward-ordered nodes preceding `eventId`, plus whether the line reached a start
 */
const chainBeforeEvent = (
  eventId: string,
  ctx: FlowContext,
  visitedEvents: Set<string>,
  visitedActions: Set<string>,
): FeedingChain => {
  let firstPartial: FeedingChain | null = null;

  for (const producer of typeProducersOf(eventId, ctx)) {
    if (visitedActions.has(producer)) continue;
    const trigger = triggerEventOf(producer, ctx);

    if (!trigger) {
      // Producer has no trigger event → it is the event-less start: this line is complete.
      return {
        nodes: [{
          kind: 'action',
          id: producer,
        }],
        reachedStart: true,
      };
    }
    if (visitedEvents.has(trigger)) continue; // would re-enter the current line

    const sub = chainBeforeEvent(
      trigger,
      ctx,
      new Set(visitedEvents).add(trigger),
      new Set(visitedActions).add(producer),
    );
    const candidate: FeedingChain = {
      nodes: [
        ...sub.nodes,
        {
          kind: 'event',
          id: trigger,
        },
        {
          kind: 'action',
          id: producer,
        },
      ],
      reachedStart: sub.reachedStart,
    };
    if (candidate.reachedStart) return candidate; // first complete link wins
    if (!firstPartial) firstPartial = candidate; // keep the first best-effort line as fallback
  }

  return firstPartial ?? {
    nodes: [],
    reachedStart: false,
  };
};

/** Resolve the backward line feeding the clicked event, starting a fresh cycle-guard. */
const buildBackwardChain = (clickedEventId: string, ctx: FlowContext): FeedingChain =>
  chainBeforeEvent(clickedEventId, ctx, new Set<string>([clickedEventId]), new Set<string>());

/** One dotted "produces" arrow from a provider action to the event it feeds. */
const buildProducesEdge = (
  actionId: string,
  eventId: string,
  arrowColor: string,
  ctx: FlowContext,
): Edge => {
  const producedTypes = new Set(ctx.actionMetas[actionId]?.step_output_types ?? []);
  const matched = eventFields(eventId, ctx.eventMetas).filter(f => producedTypes.has(f));
  return {
    id: `info-${actionId}-${eventId}`,
    source: actionId,
    sourceHandle: 'action-source-right',
    target: eventId,
    targetHandle: 'event-target',
    type: 'informational',
    selectable: false,
    deletable: false,
    markerEnd: {
      type: MarkerType.ArrowClosed,
      color: arrowColor,
    },
    data: { outputLabel: matched.map(formatConditionKeyLabel).join(', ') },
  };
};

export interface EventFlow {
  /** Action ids highlighted along the flow (chain producers + consumers). */
  highlightedStepIds: Set<string>;
  /** Event ids highlighted along the flow (chain events + the clicked one). */
  highlightedEventIds: Set<string>;
  /** 1-based badge index per node id, covering both events and actions. */
  pathIndex: Record<string, number>;
  /** `${source}->${target}` keys of the real trigger edges that belong to the flow. */
  triggerEdgeKeys: Set<string>;
  /** Read-only dotted "produces" arrows from each provider action to the event it feeds. */
  informationalEdges: Edge[];
}

/**
 * Compute the full data-flow line for the selected node by expanding everything that happens
 * before it. Starting from the clicked node, we walk backward — producer action → its trigger
 * event → that event's producer → … — until we reach an event-less action (the start), forming a
 * single line.
 *
 * @param selectedId the currently selected node id — an event or an action (or null → empty flow)
 * @param eventMetas all events keyed by id
 * @param actionMetas all steps keyed by id
 * @param arrowColor the informational arrow stroke/marker color (resolved from the theme)
 */
export const buildEventFlow = (
  selectedId: string | null,
  eventMetas: Record<string, EventMeta>,
  actionMetas: Record<string, ActionMeta>,
  arrowColor: string,
): EventFlow => {
  const empty: EventFlow = {
    highlightedStepIds: new Set<string>(),
    highlightedEventIds: new Set<string>(),
    pathIndex: {},
    triggerEdgeKeys: new Set<string>(),
    informationalEdges: [],
  };
  if (!selectedId) return empty;

  const ctx: FlowContext = {
    eventMetas,
    actionMetas,
  };

  const isEvent = !!eventMetas[selectedId];
  const isAction = !isEvent && !!actionMetas[selectedId];
  if (!isEvent && !isAction) return empty;

  // 1-3. Build the spine and, for an event click, the consumer actions it triggers.
  let spine: FlowNode[];
  let consumers: string[];

  if (isEvent) {
    const backward: FlowNode[] = buildBackwardChain(selectedId, ctx).nodes;
    spine = [
      ...backward,
      {
        kind: 'event',
        id: selectedId,
      },
    ];
    consumers = consumersOf(selectedId, ctx);
  } else {
    // Action click: expand everything before it, ending at the action itself. Reuse the backward
    // walk from the event that triggers the action; an event-less start action highlights alone.
    const trigger = triggerEventOf(selectedId, ctx);
    if (trigger) {
      const backward: FlowNode[] = chainBeforeEvent(
        trigger,
        ctx,
        new Set<string>([trigger]),
        new Set<string>([selectedId]),
      ).nodes;
      spine = [
        ...backward,
        {
          kind: 'event',
          id: trigger,
        },
        {
          kind: 'action',
          id: selectedId,
        },
      ];
    } else {
      spine = [{
        kind: 'action',
        id: selectedId,
      }];
    }
    consumers = [];
  }

  // 4. Number the spine forward (1 = event-less start); all consumers share the final index.
  const pathIndex: Record<string, number> = {};
  let idx = 0;
  for (const node of spine) {
    idx += 1;
    pathIndex[node.id] = idx;
  }
  const consumerIndex = idx + 1;
  for (const consumerId of consumers) {
    if (!(consumerId in pathIndex)) pathIndex[consumerId] = consumerIndex;
  }

  // 5. Highlighted node sets.
  const highlightedStepIds = new Set<string>();
  const highlightedEventIds = new Set<string>();
  for (const node of spine) {
    if (node.kind === 'action') highlightedStepIds.add(node.id);
    else highlightedEventIds.add(node.id);
  }
  for (const consumerId of consumers) highlightedStepIds.add(consumerId);

  // 6. Flow edges: walk consecutive spine pairs — action→event is a dotted "produces" arrow,
  //    event→action is a real "triggers" link — then add the clicked event → consumer links.
  const triggerEdgeKeys = new Set<string>();
  const informationalEdges: Edge[] = [];
  for (let i = 0; i < spine.length - 1; i += 1) {
    const prev = spine[i];
    const next = spine[i + 1];
    if (prev.kind === 'action' && next.kind === 'event') {
      informationalEdges.push(buildProducesEdge(prev.id, next.id, arrowColor, ctx));
    } else if (prev.kind === 'event' && next.kind === 'action') {
      triggerEdgeKeys.add(`${prev.id}->${next.id}`);
    }
  }
  for (const consumerId of consumers) {
    triggerEdgeKeys.add(`${selectedId}->${consumerId}`);
  }

  return {
    highlightedStepIds,
    highlightedEventIds,
    pathIndex,
    triggerEdgeKeys,
    informationalEdges,
  };
};
