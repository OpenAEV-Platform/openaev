import { useTheme } from '@mui/material/styles';
import {
  ConnectionLineType,
  Controls,
  type Edge,
  type EdgeChange,
  MarkerType,
  type Node,
  type NodeChange,
  Position,
  ReactFlow,
  applyNodeChanges,
  applyEdgeChanges,
} from '@xyflow/react';
import { stratify, tree } from 'd3-hierarchy';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
// Note: getDownstreamStepIds/getUpstreamStepIds kept for potential future use

import { type AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern } from '../../../../../../utils/api-types';
import type { WorkflowStep } from '../../../../../../utils/api-types-custom';
import {
  extractInputBindings,
  extractOutputTypesFromStepData,
  getDownstreamStepIds,
  getEventFieldConditions,
  getEventFlowTypes,
  getFieldScopes,
  getStepAttackPatterns,
  getStepInjectorType,
  getStepLabel,
  getUpstreamStepIds,
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
const GAP_X = 60;
const GAP_Y = 60;

const treeLayout = tree<{ id: string }>()
  .separation(() => 1)
  .nodeSize([NODE_WIDTH + GAP_X, NODE_HEIGHT + GAP_Y]);

// ── Synchronous tree layout ───────────────────────────────────────────
const computeLayout = (nodes: Node[], edges: Edge[]): Node[] => {
  if (nodes.length === 0) return [];

  const fakeRoot = { id: '__root' };

  const getParentId = (d: { id: string }) => {
    if (d.id === '__root') return undefined;
    const incoming = edges.find(e => e.target === d.id);
    return incoming ? incoming.source : '__root';
  };

  try {
    const hierarchy = stratify<{ id: string }>()
      .id(d => d.id)
      .parentId(getParentId)([fakeRoot, ...nodes.map(n => ({ id: n.id }))]);

    const root = treeLayout(hierarchy);

    const posMap = new Map<string, { x: number; y: number }>();
    for (const d of root.descendants()) {
      if (d.data.id !== '__root') {
        posMap.set(d.data.id, { x: d.x, y: d.y });
      }
    }

    return nodes.map(node => {
      const pos = posMap.get(node.id);
      return {
        ...node,
        position: pos
          ? { x: pos.x - NODE_WIDTH / 2, y: pos.y }
          : node.position,
        sourcePosition: Position.Bottom,
        targetPosition: Position.Top,
      };
    });
  } catch {
    return nodes.map((node, i) => ({
      ...node,
      position: {
        x: (i % 3) * (NODE_WIDTH + GAP_X),
        y: Math.floor(i / 3) * (NODE_HEIGHT + GAP_Y),
      },
      sourcePosition: Position.Bottom,
      targetPosition: Position.Top,
    }));
  }
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
      const attackPatternIds = getStepAttackPatterns(step);
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
      // Check if this event has conditions matching any output type of this action
      const matchingFields = event.step_conditions
        .filter(c => c.condition_key && outputTypes.includes(c.condition_key))
        .map(c => c.condition_key!);

      if (matchingFields.length > 0) {
        const edgeId = `field:${action.step_id}->${event.step_id}`;
        // Avoid duplicate if a DEPEND_ON edge already exists between these two
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

  // Add binding flow edges: upstream action output → downstream action input (input_source)
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

// ── Topology key (only structural edges, not highlight-dependent field edges)
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

  const { attackPatternsMap } = useHelper((helper: AttackPatternHelper) => ({
    attackPatternsMap: helper.getAttackPatternsMap(),
  }));

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

  // Build raw nodes & edges (highlight state baked in)
  const { nodes: builtNodes, edges: builtEdges } = useMemo(
    () => buildNodesAndEdges(steps, attackPatternsMap, callbacks, highlightedStepId),
    [steps, attackPatternsMap, callbacks, highlightedStepId],
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

      // Binding edges: always hidden
      if (isBindingEdge) return null;

      // Field provisioning edges: only visible on highlighted path
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

      // DEPEND_ON edges: always visible, highlighted when on path
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

  // Sync nodes & edges
  useEffect(() => {
    const newTopologyKey = getTopologyKey(steps);

    if (newTopologyKey !== topologyKeyRef.current) {
      topologyKeyRef.current = newTopologyKey;
      const laidOut = computeLayout(builtNodes, styledEdges);
      setNodes(laidOut);
      setEdges(styledEdges);
    } else {
      // Data-only change (highlight, scope toggle, etc.) → keep positions
      setNodes(prev => prev.map(node => {
        const updated = builtNodes.find(n => n.id === node.id);
        return updated ? { ...node, data: updated.data } : node;
      }));
      setEdges(styledEdges);
    }
  }, [builtNodes, styledEdges, steps]);

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
        fitViewOptions={{ maxZoom: 1, padding: 0.2 }}
        minZoom={0.3}
      >
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
};

export default LogicFlow;
