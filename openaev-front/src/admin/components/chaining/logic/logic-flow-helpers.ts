import { type Edge, MarkerType, type Node } from '@xyflow/react';

import { directFetchInjectorContract } from '../../../../actions/InjectorContracts';
import type { EventOutput, KillChainPhase, StepOutput } from '../../../../utils/api-types';
import type { ContractElement } from '../../../../utils/api-types-custom';
import type { ActionMeta, EventMeta } from './types';

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
      const rawInjector = data?.inject_injector;
      const injector = typeof rawInjector === 'string'
        ? rawInjector
        : (rawInjector as Record<string, unknown>)?.injector_id as string ?? undefined;

      // Extract kill_chain_phase_ids from embedded attack pattern objects
      const rawAttackPatterns = (data?.inject_attack_patterns ?? []) as Array<{
        attack_pattern_id?: string;
        attack_pattern_kill_chain_phases?: string[];
      }>;
      const killChainPhaseIds = rawAttackPatterns
        .flatMap(ap => ap.attack_pattern_kill_chain_phases ?? []);

      metas[s.step_id] = {
        inject_title: (data?.inject_title as string) ?? `Step ${s.step_id.slice(0, 6)}`,
        inject_description: (data?.inject_description as string) ?? '',
        inject_injector_contract: contract,
        inject_injector: injector,
        inject_attack_patterns_ids: (data?.inject_attack_patterns_ids as string[]) ?? [],
        inject_kill_chain_phase_ids: killChainPhaseIds,
        inject_assets: (data?.inject_assets as string[]) ?? [],
        inject_asset_objects: [],
        step_condition_ids: s.step_condition_ids ?? [],
        step_conditions: (((s as unknown as Record<string, unknown>).step_mapper_conditions ?? []) as Array<{
          condition_key_type?: string;
          condition_key?: string;
          condition_mapping_type?: string;
        }>).map(mc => ({
          condition_key_type: mc.condition_key_type ?? 'text',
          condition_key: mc.condition_key ?? '',
          condition_mapping_type: mc.condition_mapping_type ?? 'GLOBAL',
        })),
        contract_fields: [],
      };
    });

  return metas;
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
    const rootCondition = allConditions.find(c => !c.condition_parent_id);
    const leafConditions = allConditions
      .filter(c => !!c.condition_parent_id)
      .map(c => ({
        condition_type: (c.condition_type as string) ?? 'EQ',
        condition_key_type: (c.condition_key_type as string) ?? 'status',
        condition_value: c.condition_value ?? '',
      }));

    eventMetas[e.event_id] = {
      event_name: e.event_name ?? '',
      event_description: e.event_description ?? '',
      root_logical_type: (rootCondition?.condition_type as string) ?? 'AND',
      conditions: leafConditions.length > 0
        ? leafConditions
        : [{
            condition_type: 'EQ',
            condition_key_type: 'status',
            condition_value: 'SUCCESS',
          }],
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
        data: { label: meta.inject_title },
      });
    }
  }

  return {
    groupNodes,
    actionNodes,
  };
};

/**
 * Position event nodes to the left of the first tactic group.
 * Todo : update this when we'll do event creation
 */
export const positionEventNodes = (eventNodes: Node[]): Node[] =>
  eventNodes.map((en, i) => ({
    ...en,
    position: {
      x: -300,
      y: 50 + i * 140,
    },
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
