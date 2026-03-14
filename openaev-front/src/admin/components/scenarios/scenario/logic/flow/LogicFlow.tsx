import { useTheme } from '@mui/material/styles';
import {
  ConnectionLineType,
  Controls,
  type Edge,
  type EdgeChange,
  MarkerType,
  MiniMap,
  type Node,
  type NodeChange,
  Position,
  ReactFlow,
  applyEdgeChanges,
  applyNodeChanges,
} from '@xyflow/react';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { type AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import { fetchInjectorsContracts } from '../../../../../../actions/InjectorContracts';
import { type InjectorContractHelper } from '../../../../../../actions/injector_contracts/injector-contract-helper';
import { type KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern, InjectorContract, KillChainPhase } from '../../../../../../utils/api-types';
import type { WorkflowStep } from '../../../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import {
  UTILITY_PHASE,
  extractInputBindings,
  extractOutputTypesFromStepData,
  getDownstreamStepIds,
  getEventFieldConditions,
  getEventFlowTypes,
  getFieldScopes,
  getStepAttackPatterns,
  getStepInjectorType,
  getStepKillChainPhase,
  getStepLabel,
  getUpstreamStepIds,
  getUsedPhases,
  hasDependOnCondition,
  isActionStep,
} from '../logicUtils';
import type { HighlightState, NodeActionData } from './NodeAction';
import type { NodeEventData } from './NodeEvent';
import logicNodeTypes from './nodeTypes';

interface LogicFlowProps {
  steps: WorkflowStep[];
  onDeleteStep: (stepId: string) => void;
  onEditAction: (step: WorkflowStep) => void;
  onEditEvent: (step: WorkflowStep) => void;
  onAddActionForEvent: (eventStepId: string) => void;
}

// ── Layout constants ──────────────────────────────────────────────────
const NODE_WIDTH = 280;
const NODE_HEIGHT = 100;
const COLUMN_WIDTH = 320;
const COLUMN_GAP = 160;
const COLUMN_HEADER_H = 44;
const NODE_VGAP = 24;
const NODE_HPAD = 20;

// ── Phase color palette (cycled for many phases) ──────────────────────
const PHASE_COLORS = [
  '#2196f3', // blue
  '#ff9800', // orange
  '#4caf50', // green
  '#e91e63', // pink
  '#9c27b0', // purple
  '#00bcd4', // cyan
  '#ff5722', // deep orange
  '#3f51b5', // indigo
  '#8bc34a', // light green
  '#ffc107', // amber
  '#607d8b', // blue grey
  '#795548', // brown
  '#009688', // teal
  '#673ab7', // deep purple
];

const getPhaseColor = (index: number) => PHASE_COLORS[index % PHASE_COLORS.length];

// ── Column layout types ──────────────────────────────────────────────
interface ColumnDef {
  phase: KillChainPhase;
  color: string;
  x: number;
  width: number;
  actionCount: number;
}

interface ColumnLayout {
  nodes: Node[];
  columns: ColumnDef[];
  graphHeight: number;
}

// ── Column layout engine ─────────────────────────────────────────────
const computeColumnLayout = (
  builtNodes: Node[],
  builtEdges: Edge[],
  steps: WorkflowStep[],
  attackPatternsMap: Record<string, AttackPattern>,
  killChainPhasesMap: Record<string, KillChainPhase>,
  usedPhases: KillChainPhase[],
  injectorContractsMap?: Record<string, InjectorContract>,
): ColumnLayout => {
  if (builtNodes.length === 0) return { nodes: [], columns: [], graphHeight: 0 };

  // 1. Build column definitions
  const columns: ColumnDef[] = usedPhases.map((phase, i) => ({
    phase,
    color: getPhaseColor(i),
    x: i * (COLUMN_WIDTH + COLUMN_GAP),
    width: COLUMN_WIDTH,
    actionCount: 0,
  }));

  const phaseToColumn = new Map<string, number>();
  for (let i = 0; i < columns.length; i++) {
    phaseToColumn.set(columns[i].phase.phase_id, i);
  }

  // 2. Assign actions to columns and stack vertically
  const columnStacks: number[] = new Array(columns.length).fill(0);
  const positionedNodes: Node[] = [];
  const actionPositions = new Map<string, { x: number; y: number; colIdx: number }>();

  // Sort action nodes: root actions first for better visual flow
  const actionNodes = builtNodes.filter(n => {
    const step = steps.find(s => s.step_id === n.id);
    return step && isActionStep(step);
  });
  const eventNodes = builtNodes.filter(n => {
    const step = steps.find(s => s.step_id === n.id);
    return step && !isActionStep(step);
  });

  for (const node of actionNodes) {
    const step = steps.find(s => s.step_id === node.id);
    if (!step) continue;

    const phase = getStepKillChainPhase(step, attackPatternsMap, killChainPhasesMap, injectorContractsMap);
    if (!phase) continue;

    const colIdx = phaseToColumn.get(phase.phase_id);
    if (colIdx === undefined) continue;

    const col = columns[colIdx];
    const stackIdx = columnStacks[colIdx];
    const x = col.x + NODE_HPAD;
    const y = COLUMN_HEADER_H + stackIdx * (NODE_HEIGHT + NODE_VGAP);

    columnStacks[colIdx]++;
    col.actionCount++;

    actionPositions.set(node.id, { x, y, colIdx });
    positionedNodes.push({
      ...node,
      position: { x, y },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    });
  }

  // 3. Position events in gaps between columns
  for (const node of eventNodes) {
    const step = steps.find(s => s.step_id === node.id);
    if (!step) continue;

    // Find provider (upstream) actions
    const providerColIndices: number[] = [];
    const providerYs: number[] = [];
    // Find consumer (downstream) actions
    const consumerColIndices: number[] = [];
    const consumerYs: number[] = [];

    // Check DEPEND_ON conditions (this event depends on...)
    for (const c of step.step_conditions) {
      if (c.condition_type === 'DEPEND_ON' && c.step_from_id) {
        const pos = actionPositions.get(c.step_from_id);
        if (pos) {
          providerColIndices.push(pos.colIdx);
          providerYs.push(pos.y);
        }
      }
    }

    // Check field provisioning: actions whose outputs match event conditions
    const conditionKeys = step.step_conditions
      .filter(c => c.condition_key)
      .map(c => c.condition_key!);
    if (conditionKeys.length > 0) {
      for (const aNode of actionNodes) {
        const aStep = steps.find(s => s.step_id === aNode.id);
        if (!aStep) continue;
        const outputs = extractOutputTypesFromStepData(aStep);
        if (conditionKeys.some(k => outputs.includes(k))) {
          const pos = actionPositions.get(aNode.id);
          if (pos && !providerColIndices.includes(pos.colIdx)) {
            providerColIndices.push(pos.colIdx);
            providerYs.push(pos.y);
          }
        }
      }
    }

    // Find downstream actions (actions that DEPEND_ON this event, or consume its flow)
    for (const otherStep of steps) {
      if (!isActionStep(otherStep)) continue;
      const dependsOnThis = otherStep.step_conditions.some(
        c => c.condition_type === 'DEPEND_ON' && c.step_from_id === step.step_id,
      );
      if (dependsOnThis) {
        const pos = actionPositions.get(otherStep.step_id);
        if (pos) {
          consumerColIndices.push(pos.colIdx);
          consumerYs.push(pos.y);
        }
      }
    }

    // Determine horizontal position (always in a gap)
    let eventX: number;
    const allYs = [...providerYs, ...consumerYs];
    let eventY = allYs.length > 0
      ? allYs.reduce((a, b) => a + b, 0) / allYs.length
      : COLUMN_HEADER_H;

    if (providerColIndices.length > 0 && consumerColIndices.length > 0) {
      const srcCol = Math.min(...providerColIndices);
      const tgtCol = Math.min(...consumerColIndices);
      if (srcCol !== tgtCol) {
        // Between provider and consumer columns
        const leftCol = Math.min(srcCol, tgtCol);
        const rightCol = Math.max(srcCol, tgtCol);
        // Center in the gap between leftCol right edge and rightCol left edge
        const gapLeft = columns[leftCol].x + columns[leftCol].width;
        const gapRight = columns[rightCol].x;
        eventX = gapLeft + (gapRight - gapLeft) / 2 - NODE_WIDTH / 2;
      } else {
        // Same column: place in gap to the right
        const gapLeft = columns[srcCol].x + columns[srcCol].width;
        eventX = gapLeft + (COLUMN_GAP - NODE_WIDTH) / 2;
      }
    } else if (providerColIndices.length > 0) {
      // Provider only: gap to the right of provider column
      const srcCol = Math.max(...providerColIndices);
      const gapLeft = columns[srcCol].x + columns[srcCol].width;
      eventX = gapLeft + (COLUMN_GAP - NODE_WIDTH) / 2;
    } else {
      // Orphan: place after last column
      const lastCol = columns[columns.length - 1];
      const gapLeft = lastCol ? lastCol.x + lastCol.width : 0;
      eventX = gapLeft + (COLUMN_GAP - NODE_WIDTH) / 2;
    }

    positionedNodes.push({
      ...node,
      position: { x: eventX, y: eventY },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    });
  }

  // 4. Compute graph height
  const maxY = positionedNodes.reduce(
    (max, n) => Math.max(max, n.position.y + NODE_HEIGHT),
    0,
  );
  const graphHeight = maxY + NODE_VGAP * 2;

  // 5. Add column background nodes (rendered behind action/event nodes)
  const colHeight = Math.max(graphHeight, COLUMN_HEADER_H + NODE_HEIGHT + NODE_VGAP * 2);
  for (const col of columns) {
    const isUtility = col.phase.phase_id === UTILITY_PHASE.phase_id;
    positionedNodes.unshift({
      id: `col-bg-${col.phase.phase_id}`,
      type: 'column-bg',
      position: { x: col.x, y: 0 },
      data: {
        phaseName: col.phase.phase_name,
        color: col.color,
        isUtility,
        colWidth: col.width,
        colHeight: colHeight,
      },
      selectable: false,
      draggable: false,
      connectable: false,
      style: { zIndex: -1, pointerEvents: 'none' as const },
    });
  }

  return { nodes: positionedNodes, columns, graphHeight };
};

// ── Build nodes & edges ───────────────────────────────────────────────
const buildNodesAndEdges = (
  steps: WorkflowStep[],
  attackPatternsMap: Record<string, AttackPattern>,
  callbacks: {
    onDeleteStep: (stepId: string) => void;
    onEditAction: (step: WorkflowStep) => void;
    onEditEvent: (step: WorkflowStep) => void;
    onAddActionForEvent: (eventStepId: string) => void;
    onHighlight: (stepId: string) => void;
  },
  highlightedId: string | null,
  injectorContractsMap?: Record<string, InjectorContract>,
) => {
  const nodes: Node[] = [];
  const edges: Edge[] = [];

  // Compute highlight set (both upstream and downstream)
  const connectedIds = highlightedId
    ? (() => {
      const downstream = getDownstreamStepIds(steps, highlightedId);
      const upstream = getUpstreamStepIds(steps, highlightedId);
      return new Set([...downstream, ...upstream]);
    })()
    : null;

  const getHighlightState = (stepId: string): HighlightState => {
    if (!highlightedId) return null;
    if (stepId === highlightedId) return 'source';
    if (connectedIds?.has(stepId)) return 'highlighted';
    return 'dimmed';
  };

  for (const step of steps) {
    if (isActionStep(step)) {
      const attackPatternIds = getStepAttackPatterns(step, injectorContractsMap);
      const attackPatternExternalIds = attackPatternIds
        .map(id => attackPatternsMap[id]?.attack_pattern_external_id)
        .filter((eid): eid is string => !!eid);

      const nodeData: NodeActionData = {
        step,
        label: getStepLabel(step),
        injectorType: getStepInjectorType(step),
        attackPatternExternalIds,
        outputTypes: extractOutputTypesFromStepData(step),
        inputBindings: extractInputBindings(step, steps),
        fieldScopes: getFieldScopes(step),
        hasParentEvent: hasDependOnCondition(step),
        highlightState: getHighlightState(step.step_id),
        onEdit: callbacks.onEditAction,
        onDelete: callbacks.onDeleteStep,
        onHighlight: callbacks.onHighlight,
      };
      nodes.push({
        id: step.step_id,
        type: 'action',
        position: { x: 0, y: 0 },
        data: nodeData,
      });
    } else {
      const nodeData: NodeEventData = {
        step,
        label: getStepLabel(step),
        fieldConditions: getEventFieldConditions(step),
        flowTypes: getEventFlowTypes(step),
        highlightState: getHighlightState(step.step_id),
        onEdit: callbacks.onEditEvent,
        onDelete: callbacks.onDeleteStep,
        onAddAction: callbacks.onAddActionForEvent,
        onHighlight: callbacks.onHighlight,
      };
      nodes.push({
        id: step.step_id,
        type: 'event',
        position: { x: 0, y: 0 },
        data: nodeData,
      });
    }

    for (const condition of step.step_conditions) {
      if (condition.condition_type === 'DEPEND_ON' && condition.step_from_id) {
        edges.push({
          id: `depend:${condition.step_from_id}->${step.step_id}`,
          source: condition.step_from_id,
          target: step.step_id,
          type: ConnectionLineType.SmoothStep,
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 20,
            height: 20,
          },
        });
      }
    }
  }

  // Add implicit "field provisioning" edges: action outputs → event conditions
  const actionSteps = steps.filter(isActionStep);
  const eventSteps = steps.filter(s => !isActionStep(s));

  for (const action of actionSteps) {
    const outputTypes = extractOutputTypesFromStepData(action);
    if (outputTypes.length === 0) continue;

    for (const event of eventSteps) {
      const matchingFields = event.step_conditions
        .filter(c => c.condition_key && outputTypes.includes(c.condition_key))
        .map(c => c.condition_key!);

      if (matchingFields.length > 0) {
        const edgeId = `field:${action.step_id}->${event.step_id}`;
        const alreadyLinked = edges.some(
          e => e.source === action.step_id && e.target === event.step_id,
        );
        if (!alreadyLinked) {
          edges.push({
            id: edgeId,
            source: action.step_id,
            target: event.step_id,
            type: ConnectionLineType.SmoothStep,
            animated: true,
            label: matchingFields.join(', '),
            labelStyle: { fontSize: 9, fill: '#999' },
            style: { strokeDasharray: '6 3' },
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 16,
              height: 16,
            },
          });
        }
      }
    }
  }

  // Add binding flow edges
  for (const node of nodes) {
    if (node.type !== 'action') continue;
    const actionData = node.data as NodeActionData;
    for (const binding of actionData.inputBindings) {
      if (!binding.bound) continue;
      for (const provider of binding.providers) {
        const edgeId = `binding:${provider.providerStepId}->${node.id}:${binding.argumentKey}`;
        const alreadyHasBindingEdge = edges.some(e => e.id === edgeId);
        if (!alreadyHasBindingEdge) {
          edges.push({
            id: edgeId,
            source: provider.providerStepId,
            target: node.id,
            type: ConnectionLineType.SmoothStep,
            animated: true,
            label: `${binding.inputField ?? binding.inputType} → ${binding.argumentKey}`,
            labelStyle: { fontSize: 8, fill: '#4caf50' },
            style: { strokeDasharray: '4 4' },
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 14,
              height: 14,
            },
          });
        }
      }
    }
  }

  return { nodes, edges };
};

// ── Topology key (only structural edges, not highlight-dependent)
const getTopologyKey = (steps: WorkflowStep[]) => {
  const ids = steps.map(s => s.step_id).sort().join(',');
  const deps = steps.flatMap(s =>
    s.step_conditions
      .filter(c => c.condition_type === 'DEPEND_ON' && c.step_from_id)
      .map(c => `${c.step_from_id}->${s.step_id}`),
  ).sort().join(',');
  return `${ids}|${deps}`;
};

// ── Component ─────────────────────────────────────────────────────────
const LogicFlow: FunctionComponent<LogicFlowProps> = ({
  steps,
  onDeleteStep,
  onEditAction,
  onEditEvent,
  onAddActionForEvent,
}) => {
  const theme = useTheme();
  const [highlightedStepId, setHighlightedStepId] = useState<string | null>(null);

  const { attackPatternsMap, killChainPhasesMap, injectorContractsMap } = useHelper(
    (helper: AttackPatternHelper & KillChainPhaseHelper & InjectorContractHelper) => ({
      attackPatternsMap: helper.getAttackPatternsMap(),
      killChainPhasesMap: helper.getKillChainPhasesMap(),
      injectorContractsMap: helper.getInjectorContractsMap(),
    }),
  );

  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchInjectorsContracts());
  });

  const handleHighlight = useCallback((stepId: string) => {
    setHighlightedStepId(prev => prev === stepId ? null : stepId);
  }, []);

  const handlePaneClick = useCallback(() => {
    setHighlightedStepId(null);
  }, []);

  const callbacks = useMemo(() => ({
    onDeleteStep,
    onEditAction,
    onEditEvent,
    onAddActionForEvent,
    onHighlight: handleHighlight,
  }), [onDeleteStep, onEditAction, onEditEvent, onAddActionForEvent, handleHighlight]);

  // Used kill chain phases (sorted by phase_order)
  const usedPhases = useMemo(
    () => getUsedPhases(steps, attackPatternsMap, killChainPhasesMap, injectorContractsMap),
    [steps, attackPatternsMap, killChainPhasesMap, injectorContractsMap],
  );

  // Build raw nodes & edges (highlight state baked in)
  const { nodes: builtNodes, edges: builtEdges } = useMemo(
    () => buildNodesAndEdges(steps, attackPatternsMap, callbacks, highlightedStepId, injectorContractsMap),
    [steps, attackPatternsMap, callbacks, highlightedStepId, injectorContractsMap],
  );

  // Compute highlighted edge IDs
  const highlightedEdgeIds = useMemo(() => {
    if (!highlightedStepId) return null;
    const downstream = getDownstreamStepIds(steps, highlightedStepId);
    const upstream = getUpstreamStepIds(steps, highlightedStepId);
    const allConnected = new Set([highlightedStepId, ...downstream, ...upstream]);
    const edgeIds = new Set<string>();
    for (const edge of builtEdges) {
      if (allConnected.has(edge.source) && allConnected.has(edge.target)) {
        edgeIds.add(edge.id);
      }
    }
    return edgeIds;
  }, [steps, highlightedStepId, builtEdges]);

  // Style edges
  const styledEdges = useMemo(() => {
    return builtEdges.map((edge): Edge | null => {
      const isFieldEdge = edge.id.startsWith('field:');
      const isBindingEdge = edge.id.startsWith('binding:');
      const isOnPath = highlightedEdgeIds?.has(edge.id) ?? false;
      const hasHighlight = highlightedEdgeIds !== null;

      if (isBindingEdge) return null;

      if (isFieldEdge) {
        if (!hasHighlight || !isOnPath) return null;
        return {
          ...edge,
          style: {
            ...edge.style,
            stroke: theme.palette.warning.main,
            strokeWidth: 2,
            strokeDasharray: '6 3',
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 16,
            height: 16,
            color: theme.palette.warning.main,
          },
        };
      }

      const activeColor = theme.palette.primary.main;
      return {
        ...edge,
        style: {
          ...edge.style,
          stroke: hasHighlight
            ? (isOnPath ? activeColor : theme.palette.divider)
            : theme.palette.divider,
          strokeWidth: isOnPath ? 3 : 2,
          opacity: hasHighlight ? (isOnPath ? 1 : 0.3) : 1,
          transition: 'all 0.2s',
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 20,
          height: 20,
          color: hasHighlight
            ? (isOnPath ? activeColor : theme.palette.text.disabled)
            : theme.palette.text.secondary,
        },
      };
    }).filter((e): e is Edge => e !== null);
  }, [builtEdges, highlightedEdgeIds, theme]);

  // State
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const topologyKeyRef = useRef('');

  // Sync nodes & edges (column layout is synchronous)
  useEffect(() => {
    // Include usedPhases in topology key so layout recalculates when contracts load
    const phasesKey = usedPhases.map(p => p.phase_id).join(',');
    const newTopologyKey = `${getTopologyKey(steps)}|phases:${phasesKey}`;

    if (newTopologyKey !== topologyKeyRef.current) {
      topologyKeyRef.current = newTopologyKey;
      const layout = computeColumnLayout(
        builtNodes, styledEdges, steps,
        attackPatternsMap, killChainPhasesMap, usedPhases, injectorContractsMap,
      );
      setNodes(layout.nodes);
      setEdges(styledEdges);
      return;
    }

    // Data-only change (highlight, scope toggle, etc.) → keep positions
    setNodes(prev => prev.map(node => {
      const updated = builtNodes.find(n => n.id === node.id);
      return updated ? { ...node, data: updated.data } : node;
    }));
    setEdges(styledEdges);
  }, [builtNodes, styledEdges, steps, attackPatternsMap, killChainPhasesMap, usedPhases, injectorContractsMap]);

  const onNodesChange = useCallback((changes: NodeChange[]) => {
    setNodes(nds => applyNodeChanges(changes, nds));
  }, []);

  const onEdgesChange = useCallback((changes: EdgeChange[]) => {
    setEdges(eds => applyEdgeChanges(changes, eds));
  }, []);

  const defaultEdgeOptions = useMemo(() => ({
    type: ConnectionLineType.SmoothStep,
    style: { stroke: theme.palette.divider, strokeWidth: 2 },
    markerEnd: {
      type: MarkerType.ArrowClosed,
      width: 20,
      height: 20,
      color: theme.palette.text.secondary,
    },
  }), [theme]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') setHighlightedStepId(null);
  }, []);

  return (
    <div onKeyDown={handleKeyDown} tabIndex={0} style={{ width: '100%', height: '100%', outline: 'none' }}>
      <ReactFlow
        colorMode={theme.palette.mode}
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onPaneClick={handlePaneClick}
        nodeTypes={logicNodeTypes}
        nodesConnectable={false}
        defaultEdgeOptions={defaultEdgeOptions}
        connectionLineType={ConnectionLineType.SmoothStep}
        proOptions={{ account: 'paid-pro', hideAttribution: true }}
        fitView
        fitViewOptions={{ maxZoom: 1, padding: 0.15 }}
        minZoom={0.15}
      >
        <Controls showInteractive={false} />
        <MiniMap
          pannable
          zoomable
          nodeStrokeWidth={3}
          style={{ border: `1px solid ${theme.palette.divider}` }}
        />
      </ReactFlow>
    </div>
  );
};

export default LogicFlow;
