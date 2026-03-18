import { useTheme } from '@mui/material/styles';
import {
  ConnectionLineType,
  Controls,
  type Edge,
  MarkerType,
  MiniMap,
  type Node,
  ReactFlow,
} from '@xyflow/react';
import { type FunctionComponent, useMemo, useState } from 'react';

import type { WorkflowStep } from '../../../../../utils/api-types-custom';
import type { InjectExpectation } from '../../../../../utils/api-types';
import { isActionStep, getStepLabel, getChainSequenceOrder, getDownstreamStepIds, getUpstreamStepIds } from '../../../scenarios/scenario/logic/logicUtils';
import NodeAttackStep from './NodeAttackStep';

// ── Layout constants ────────────────────────────────────────────────────
const NODE_W = 200;
const NODE_H = 60;
const H_GAP = 280;
const V_GAP = 100;

// ── Status color helpers ────────────────────────────────────────────────
export type AttackStepStatus = 'prevented' | 'detected' | 'undetected' | 'pending';

export const STATUS_COLORS: Record<AttackStepStatus, { fill: string; stroke: string }> = {
  prevented: { fill: '#4caf50', stroke: '#388e3c' },   // green
  detected: { fill: '#ff9800', stroke: '#f57c00' },     // orange
  undetected: { fill: '#f44336', stroke: '#d32f2f' },   // red
  pending: { fill: '#9e9e9e', stroke: '#757575' },       // grey
};

export function resolveStepStatus(
  expectations: InjectExpectation[],
  injectId: string,
  assetId?: string,
): AttackStepStatus {
  const relevant = expectations.filter((e) => {
    if (e.inject_expectation_inject !== injectId) return false;
    if (assetId && e.inject_expectation_asset !== assetId) return false;
    return true;
  });
  if (relevant.length === 0) return 'pending';

  const prevention = relevant.find(e => e.inject_expectation_type === 'PREVENTION');
  const detection = relevant.find(e => e.inject_expectation_type === 'DETECTION');

  if (prevention?.inject_expectation_status === 'SUCCESS') return 'prevented';
  if (detection?.inject_expectation_status === 'SUCCESS') return 'detected';

  const allPending = relevant.every(
    e => e.inject_expectation_status === 'PENDING' || !e.inject_expectation_status,
  );
  if (allPending) return 'pending';

  return 'undetected';
}

// ── Node data type ──────────────────────────────────────────────────────
export interface NodeAttackStepData {
  label: string;
  status: AttackStepStatus;
  sequenceNumber?: number;
  injectId?: string;
  assetName?: string;
  highlightState: 'source' | 'highlighted' | 'dimmed' | null;
  onClick: (stepId: string) => void;
}

const nodeTypes = { 'attack-step': NodeAttackStep };

// ── Props ───────────────────────────────────────────────────────────────
interface AttackPathFlowProps {
  steps: WorkflowStep[];
  expectations: InjectExpectation[];
  selectedStepId: string | null;
  onSelectStep: (stepId: string | null) => void;
}

const AttackPathFlow: FunctionComponent<AttackPathFlowProps> = ({
  steps,
  expectations,
  selectedStepId,
  onSelectStep,
}) => {
  const theme = useTheme();

  const { nodes, edges } = useMemo(() => {
    const actionSteps = steps.filter(isActionStep);
    if (actionSteps.length === 0) return { nodes: [] as Node[], edges: [] as Edge[] };

    // Build adjacency from DEPEND_ON conditions (action → action through events)
    const actionAdjacency = new Map<string, Set<string>>();
    const actionInDegree = new Map<string, number>();
    for (const s of actionSteps) {
      actionAdjacency.set(s.step_id, new Set());
      actionInDegree.set(s.step_id, 0);
    }

    // Trace dependencies: action steps depend on other action steps through events
    const actionIds = new Set(actionSteps.map(s => s.step_id));
    const eventSteps = steps.filter(s => !isActionStep(s));

    // Direct action→action DEPEND_ON
    for (const step of actionSteps) {
      for (const cond of step.step_conditions) {
        if (cond.condition_type === 'DEPEND_ON' && cond.step_from_id && actionIds.has(cond.step_from_id)) {
          actionAdjacency.get(cond.step_from_id)?.add(step.step_id);
          actionInDegree.set(step.step_id, (actionInDegree.get(step.step_id) ?? 0) + 1);
        }
      }
    }

    // Action→event→action chains: if action A -> event E -> action B, draw A -> B
    for (const evt of eventSteps) {
      // Find which actions feed into this event (via DEPEND_ON or field matching)
      const sourceActionIds: string[] = [];
      for (const cond of evt.step_conditions) {
        if (cond.condition_type === 'DEPEND_ON' && cond.step_from_id && actionIds.has(cond.step_from_id)) {
          sourceActionIds.push(cond.step_from_id);
        }
      }
      // Find which actions depend on this event
      const targetActionIds: string[] = [];
      for (const action of actionSteps) {
        for (const cond of action.step_conditions) {
          if (cond.condition_type === 'DEPEND_ON' && cond.step_from_id === evt.step_id) {
            targetActionIds.push(action.step_id);
          }
        }
      }
      // Connect source actions to target actions
      for (const src of sourceActionIds) {
        for (const tgt of targetActionIds) {
          if (!actionAdjacency.get(src)?.has(tgt)) {
            actionAdjacency.get(src)?.add(tgt);
            actionInDegree.set(tgt, (actionInDegree.get(tgt) ?? 0) + 1);
          }
        }
      }
    }

    // Topological sort (Kahn's) for X positioning
    const queue: string[] = [];
    const topoOrder: string[] = [];
    for (const [id, deg] of actionInDegree) {
      if (deg === 0) queue.push(id);
    }
    const columnMap = new Map<string, number>();
    while (queue.length > 0) {
      const id = queue.shift()!;
      topoOrder.push(id);
      // Column = max(parent columns) + 1, or 0 if root
      let col = 0;
      for (const [src, targets] of actionAdjacency) {
        if (targets.has(id)) {
          col = Math.max(col, (columnMap.get(src) ?? 0) + 1);
        }
      }
      columnMap.set(id, col);
      for (const tgt of actionAdjacency.get(id) ?? []) {
        actionInDegree.set(tgt, (actionInDegree.get(tgt) ?? 0) - 1);
        if (actionInDegree.get(tgt) === 0) queue.push(tgt);
      }
    }

    // Handle cycles (steps not yet in topoOrder)
    for (const s of actionSteps) {
      if (!columnMap.has(s.step_id)) {
        columnMap.set(s.step_id, 0);
        topoOrder.push(s.step_id);
      }
    }

    // Group by column for Y positioning
    const colGroups = new Map<number, string[]>();
    for (const [id, col] of columnMap) {
      if (!colGroups.has(col)) colGroups.set(col, []);
      colGroups.get(col)!.push(id);
    }

    // Compute highlight state
    const connectedIds = selectedStepId
      ? (() => {
        const downstream = getDownstreamStepIds(steps, selectedStepId);
        const upstream = getUpstreamStepIds(steps, selectedStepId);
        return new Set([...downstream, ...upstream]);
      })()
      : null;

    const sequenceOrder = selectedStepId
      ? getChainSequenceOrder(steps, selectedStepId)
      : null;

    const getHighlightState = (stepId: string): NodeAttackStepData['highlightState'] => {
      if (!selectedStepId) return null;
      if (stepId === selectedStepId) return 'source';
      if (connectedIds?.has(stepId)) return 'highlighted';
      return 'dimmed';
    };

    // Build ReactFlow nodes
    const rfNodes: Node[] = [];
    const stepMap = new Map(actionSteps.map(s => [s.step_id, s]));

    for (const [col, ids] of colGroups) {
      ids.forEach((id, row) => {
        const step = stepMap.get(id)!;
        const data = JSON.parse(step.step_data ?? '{}');
        const injectId = data.inject_id;

        const status = resolveStepStatus(expectations, injectId);
        const nodeData: NodeAttackStepData = {
          label: getStepLabel(step),
          status,
          sequenceNumber: sequenceOrder?.get(id),
          injectId,
          highlightState: getHighlightState(id),
          onClick: onSelectStep,
        };

        rfNodes.push({
          id,
          type: 'attack-step',
          position: { x: col * H_GAP + 50, y: row * V_GAP + 50 },
          data: nodeData,
        });
      });
    }

    // Build edges (action→action)
    const rfEdges: Edge[] = [];
    for (const [src, targets] of actionAdjacency) {
      for (const tgt of targets) {
        const srcStatus = (rfNodes.find(n => n.id === src)?.data as NodeAttackStepData)?.status ?? 'pending';
        rfEdges.push({
          id: `ap:${src}->${tgt}`,
          source: src,
          target: tgt,
          type: ConnectionLineType.SmoothStep,
          style: {
            stroke: STATUS_COLORS[srcStatus].stroke,
            strokeWidth: 2,
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 16,
            height: 16,
            color: STATUS_COLORS[srcStatus].stroke,
          },
        });
      }
    }

    return { nodes: rfNodes, edges: rfEdges };
  }, [steps, expectations, selectedStepId, onSelectStep]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      fitView
      minZoom={0.2}
      maxZoom={2}
      proOptions={{ hideAttribution: true }}
      style={{ background: theme.palette.background.default }}
    >
      <Controls />
      <MiniMap
        nodeStrokeWidth={3}
        nodeColor={(n) => {
          const data = n.data as NodeAttackStepData;
          return STATUS_COLORS[data?.status ?? 'pending'].fill;
        }}
        style={{ background: theme.palette.background.paper }}
      />
    </ReactFlow>
  );
};

export default AttackPathFlow;
