import { Typography } from '@mui/material';
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
  Panel,
  Position,
  ReactFlow,
  applyEdgeChanges,
  applyNodeChanges,
} from '@xyflow/react';
import ELK, { type ElkExtendedEdge, type ElkNode } from 'elkjs/lib/elk.bundled.js';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { type AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import { type KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern, KillChainPhase } from '../../../../../../utils/api-types';
import type { WorkflowStep } from '../../../../../../utils/api-types-custom';
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

const elk = new ELK();

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

// ── Swimlane band computed from laid-out node positions ───────────────
interface SwimlaneBand {
  phase: KillChainPhase;
  color: string;
  x: number;
  width: number;
}

const BAND_PADDING = 30;
const BAND_HEADER_HEIGHT = 36;

const computeSwimlaneBands = (
  nodes: Node[],
  steps: WorkflowStep[],
  attackPatternsMap: Record<string, AttackPattern>,
  killChainPhasesMap: Record<string, KillChainPhase>,
  usedPhases: KillChainPhase[],
): SwimlaneBand[] => {
  // Map action nodes to their phase
  const phaseNodeBounds = new Map<string, { minX: number; maxX: number }>();

  for (const node of nodes) {
    const step = steps.find(s => s.step_id === node.id);
    if (!step || !isActionStep(step)) continue;

    const phase = getStepKillChainPhase(step, attackPatternsMap, killChainPhasesMap);
    if (!phase) continue;

    const left = node.position.x;
    const right = node.position.x + NODE_WIDTH;
    const existing = phaseNodeBounds.get(phase.phase_id);
    if (existing) {
      existing.minX = Math.min(existing.minX, left);
      existing.maxX = Math.max(existing.maxX, right);
    } else {
      phaseNodeBounds.set(phase.phase_id, { minX: left, maxX: right });
    }
  }

  return usedPhases.map((phase, i) => {
    const bounds = phaseNodeBounds.get(phase.phase_id);
    if (!bounds) return null;
    return {
      phase,
      color: getPhaseColor(i),
      x: bounds.minX - BAND_PADDING,
      width: bounds.maxX - bounds.minX + BAND_PADDING * 2,
    };
  }).filter((b): b is SwimlaneBand => b !== null);
};

// ── ELK layout (async) ───────────────────────────────────────────────
const computeElkLayout = async (
  nodes: Node[],
  edges: Edge[],
): Promise<Node[]> => {
  if (nodes.length === 0) return [];

  const elkNodes: ElkNode[] = nodes.map(n => ({
    id: n.id,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
  }));

  // Only use structural edges (DEPEND_ON + field provisioning) for layout
  const elkEdges: ElkExtendedEdge[] = edges
    .filter(e => !e.id.startsWith('binding:'))
    .map(e => ({
      id: e.id,
      sources: [e.source],
      targets: [e.target],
    }));

  const graph: ElkNode = {
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'RIGHT',
      'elk.spacing.nodeNode': '60',
      'elk.layered.spacing.nodeNodeBetweenLayers': '80',
      'elk.layered.spacing.edgeNodeBetweenLayers': '40',
      'elk.edgeRouting': 'SPLINES',
      'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
      'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
    },
    children: elkNodes,
    edges: elkEdges,
  };

  try {
    const laid = await elk.layout(graph);
    const posMap = new Map<string, { x: number; y: number }>();
    for (const child of laid.children ?? []) {
      posMap.set(child.id, { x: child.x ?? 0, y: child.y ?? 0 });
    }

    return nodes.map(node => {
      const pos = posMap.get(node.id);
      return {
        ...node,
        position: pos ?? node.position,
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      };
    });
  } catch {
    // Fallback: simple grid
    return nodes.map((node, i) => ({
      ...node,
      position: {
        x: (i % 4) * (NODE_WIDTH + 80),
        y: Math.floor(i / 4) * (NODE_HEIGHT + 60),
      },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
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

  const { attackPatternsMap, killChainPhasesMap } = useHelper(
    (helper: AttackPatternHelper & KillChainPhaseHelper) => ({
      attackPatternsMap: helper.getAttackPatternsMap(),
      killChainPhasesMap: helper.getKillChainPhasesMap(),
    }),
  );

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
    () => getUsedPhases(steps, attackPatternsMap, killChainPhasesMap),
    [steps, attackPatternsMap, killChainPhasesMap],
  );

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
  const [swimlanes, setSwimlanes] = useState<SwimlaneBand[]>([]);
  const topologyKeyRef = useRef('');

  // Sync nodes & edges (ELK layout is async)
  useEffect(() => {
    const newTopologyKey = getTopologyKey(steps);

    if (newTopologyKey !== topologyKeyRef.current) {
      topologyKeyRef.current = newTopologyKey;
      let cancelled = false;

      computeElkLayout(builtNodes, styledEdges).then((laidOut) => {
        if (cancelled) return;
        setNodes(laidOut);
        setEdges(styledEdges);
        setSwimlanes(
          computeSwimlaneBands(laidOut, steps, attackPatternsMap, killChainPhasesMap, usedPhases),
        );
      });

      return () => { cancelled = true; };
    }

    // Data-only change (highlight, scope toggle, etc.) → keep positions
    setNodes(prev => prev.map(node => {
      const updated = builtNodes.find(n => n.id === node.id);
      return updated ? { ...node, data: updated.data } : node;
    }));
    setEdges(styledEdges);
    return undefined;
  }, [builtNodes, styledEdges, steps, attackPatternsMap, killChainPhasesMap, usedPhases]);

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

  // Compute total vertical extent of the graph for swimlane band heights
  const graphBounds = useMemo(() => {
    if (nodes.length === 0) return { minY: 0, maxY: 400 };
    let minY = Infinity;
    let maxY = -Infinity;
    for (const n of nodes) {
      minY = Math.min(minY, n.position.y);
      maxY = Math.max(maxY, n.position.y + NODE_HEIGHT);
    }
    return { minY: minY - BAND_PADDING, maxY: maxY + BAND_PADDING };
  }, [nodes]);

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
        {/* Swimlane phase backgrounds rendered as SVG behind nodes */}
        {swimlanes.length > 0 && (
          <Panel position="top-left" style={{ margin: 0, padding: 0, pointerEvents: 'none', position: 'absolute', top: 0, left: 0 }}>
            <svg
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: 0,
                height: 0,
                overflow: 'visible',
                pointerEvents: 'none',
              }}
            >
              {swimlanes.map((band) => {
                const bandHeight = graphBounds.maxY - graphBounds.minY + BAND_HEADER_HEIGHT;
                const isUtility = band.phase.phase_id === UTILITY_PHASE.phase_id;
                return (
                  <g key={band.phase.phase_id}>
                    {/* Background band */}
                    <rect
                      x={band.x}
                      y={graphBounds.minY - BAND_HEADER_HEIGHT}
                      width={band.width}
                      height={bandHeight}
                      rx={8}
                      fill={band.color}
                      fillOpacity={theme.palette.mode === 'dark' ? 0.06 : 0.04}
                      stroke={band.color}
                      strokeOpacity={0.2}
                      strokeWidth={1}
                      strokeDasharray={isUtility ? '6 3' : undefined}
                    />
                    {/* Phase header */}
                    <text
                      x={band.x + band.width / 2}
                      y={graphBounds.minY - BAND_HEADER_HEIGHT + 22}
                      textAnchor="middle"
                      fill={band.color}
                      fontSize={12}
                      fontWeight={600}
                      fontFamily={theme.typography.fontFamily}
                      opacity={0.8}
                    >
                      {band.phase.phase_name}
                    </text>
                  </g>
                );
              })}
            </svg>
          </Panel>
        )}
        <Controls showInteractive={false} />
        <MiniMap
          pannable
          zoomable
          nodeStrokeWidth={3}
          style={{ border: `1px solid ${theme.palette.divider}` }}
        />
      </ReactFlow>
      {/* Phase legend (bottom) */}
      {swimlanes.length > 1 && (
        <div
          style={{
            position: 'absolute',
            bottom: 8,
            left: '50%',
            transform: 'translateX(-50%)',
            display: 'flex',
            gap: 12,
            padding: '4px 12px',
            borderRadius: 6,
            background: theme.palette.mode === 'dark'
              ? 'rgba(0,0,0,0.6)'
              : 'rgba(255,255,255,0.85)',
            backdropFilter: 'blur(4px)',
            border: `1px solid ${theme.palette.divider}`,
            zIndex: 10,
          }}
        >
          {swimlanes.map((band) => (
            <div key={band.phase.phase_id} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <div style={{
                width: 10,
                height: 10,
                borderRadius: 2,
                background: band.color,
                opacity: 0.7,
              }}
              />
              <Typography variant="caption" sx={{ fontSize: 10, color: 'text.secondary' }}>
                {band.phase.phase_name}
              </Typography>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default LogicFlow;
