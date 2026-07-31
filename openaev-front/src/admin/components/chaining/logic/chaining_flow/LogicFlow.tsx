import { styled, useTheme } from '@mui/material/styles';
import {
  addEdge,
  type Connection,
  Controls,
  type Edge,
  MarkerType,
  MiniMap,
  type Node,
  ReactFlow,
  useEdgesState,
  useKeyPress,
  useNodesState,
} from '@xyflow/react';
import { type MouseEvent as ReactMouseEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  deleteCondition,
  deleteStep,
  fetchConditions,
  fetchSteps,
  updateStep,
} from '../../../../../actions/chaining/chaining-actions';
import type { KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import type { ConditionCreateInput, KillChainPhase } from '../../../../../utils/api-types';
import { emitChainingUpdated } from '../chaining-refresh-events';
import {
  buildActionMetas,
  buildEdges,
  buildEventData,
  buildEventPath,
  buildEventToGroupX,
  buildInformationalEdges,
  buildOutputProvidersMap,
  buildTacticForStep,
  buildTacticNodes,
  enrichActionMetasWithContracts,
  positionEventNodes,
} from '../logic-flow-helpers';
import type { ActionMeta, EventMeta } from '../types';
import { useOutputProviders } from '../useOutputProviders';
import edgeTypes from './edges';
import nodeTypes from './nodes';

interface LogicFlowProps {
  workflowId: string;
  reloadTrigger?: number;
  onEditStep?: (stepId: string, meta: ActionMeta) => void;
  onEditEvent?: (eventId: string, meta: EventMeta) => void;
  /** Open the action drawer to add an action linked to the given event. */
  onAddActionToEvent?: (eventId: string) => void;
  /** Called after each graph refresh so the parent can drive the warning banner. */
  onEventMetasChange?: (metas: Record<string, EventMeta>) => void;
}

const proOptions = {
  account: 'paid-pro',
  hideAttribution: true,
};

/** Zoom / fit-view controls themed to match the app (primary-colored buttons). */
const StyledControls = styled(Controls)(({ theme }) => ({
  'background': theme.palette.background.paper,
  'border': `1px solid ${theme.palette.divider}`,
  'borderRadius': theme.spacing(1),
  'boxShadow': theme.shadows[3],
  '& .react-flow__controls-button': {
    'background': theme.palette.background.paper,
    'borderBottom': `1px solid ${theme.palette.divider}`,
    '&:hover': { background: theme.palette.action.hover },
  },
  '& .react-flow__controls-button svg': { fill: theme.palette.primary.main },
}));

/** Opacity applied to nodes/edges outside the selected event's flow (spotlight backdrop). */
const DIMMED_OPACITY = 0.24;

/**
 * Main logic flow part that displays actions and events as a ReactFlow graph,
 * grouped into MITRE tactic columns. Supports connecting events to actions, editing,
 * deleting nodes, and adding new components.
 */
const LogicFlow = ({
  workflowId,
  reloadTrigger,
  onEditStep,
  onEditEvent,
  onAddActionToEvent,
  onEventMetasChange,
}: LogicFlowProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { setProviders: setContextProviders } = useOutputProviders();

  // Access kill chain phases from store for tactic column ordering
  const { killChainPhasesMap } = useHelper(
    (helper: KillChainPhaseHelper) => ({ killChainPhasesMap: helper.getKillChainPhasesMap() }),
  );

  // Store in a ref so refreshGraph can always read the latest value
  // without having it as a dependency (which causes infinite reload loops,
  // since useHelper returns a new object reference on every render)
  const killChainPhasesMapRef = useRef(killChainPhasesMap);

  // Keep ref in sync so refreshGraph always reads the latest killChainPhasesMap
  // without having it as a useCallback dependency (new object reference every render)
  useEffect(() => {
    killChainPhasesMapRef.current = killChainPhasesMap;
  }, [killChainPhasesMap]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  // Extra metadata per node (keyed by node id)
  const [actionMetas, setActionMetas] = useState<Record<string, ActionMeta>>({});
  const [eventMetas, setEventMetas] = useState<Record<string, EventMeta>>({});

  // Graph loading state — true until first data load completes
  const [loading, setLoading] = useState(true);

  // Delete confirmation dialog state
  const [pendingDeleteNodeId, setPendingDeleteNodeId] = useState<string | null>(null);

  // Event currently selected to reveal its informational (data-flow) arrows.
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

  /**
     * Build the step for updateStep API calls.
     */
  const buildStepUpdate = useCallback((action: ActionMeta, newCondIds: string[]) => {
    const stepConditions: ConditionCreateInput[] = action.step_conditions.map((c, i) => ({
      condition_temporary_id: String(i),
      condition_type: 'MAPPER' as const,
      condition_key_types: (
        c.condition_key_types && c.condition_key_types.length > 0
          ? c.condition_key_types
          : ['text']
      ) as ConditionCreateInput['condition_key_types'],
      condition_key: c.condition_key,
      condition_value: c.condition_value,
      condition_mapping_type: c.condition_mapping_type as ConditionCreateInput['condition_mapping_type'],
    }));

    return {
      step_workflow_id: workflowId,
      step_action: 'INJECT_EXECUTION' as const,
      step_condition_ids: newCondIds,
      step_conditions: stepConditions.length > 0 ? stepConditions : undefined,
      step_data_step: {
        inject_title: action.inject_title,
        inject_description: action.inject_description,
        inject_injector_contract: action.inject_injector_contract,
        inject_assets: action.inject_assets,
        inject_content: action.inject_content,
      },
    };
  }, [workflowId]);

  /**
     * Load all steps and events from the API, resolve contracts and tactic grouping,
     * then build the complete ReactFlow graph.
     */
  const refreshGraph = useCallback(async () => {
    const [stepsRes, eventsRes] = await Promise.all([
      fetchSteps(workflowId),
      fetchConditions(workflowId),
    ]);

    const actionMetasData = buildActionMetas(stepsRes.data);
    const { eventMetas, eventNodes } = buildEventData(eventsRes.data);

    // Read from ref to avoid stale closure without adding killChainPhasesMap to deps
    const currentKillChainPhasesMap = killChainPhasesMapRef.current as Record<string, KillChainPhase>;

    // Fetch contract fields for form pre-population
    const enrichedActionMetas = await enrichActionMetasWithContracts(actionMetasData);

    // Group steps into tactic columns based on associated Attack Patterns
    const tacticForStep = buildTacticForStep({
      actionMetas: enrichedActionMetas,
      killChainPhasesMap: currentKillChainPhasesMap,
      fallbackTactic: t('Other'),
    });

    const { groupNodes, actionNodes } = buildTacticNodes(
      tacticForStep,
      enrichedActionMetas,
      currentKillChainPhasesMap,
    );
    const eventToGroupX = buildEventToGroupX(enrichedActionMetas, groupNodes, actionNodes);
    const positionedEventNodes = positionEventNodes(eventNodes, eventToGroupX);
    const edgesData = buildEdges(enrichedActionMetas, eventMetas);

    setActionMetas(enrichedActionMetas);
    setContextProviders(buildOutputProvidersMap(enrichedActionMetas));
    setEventMetas(eventMetas);
    onEventMetasChange?.(eventMetas);
    setNodes([...groupNodes, ...positionedEventNodes, ...actionNodes]);
    setEdges(edgesData);
    setLoading(false);
    emitChainingUpdated(workflowId);
  }, [workflowId, t, setNodes, setEdges, setContextProviders]);

  useEffect(() => {
    refreshGraph();
  }, [workflowId, killChainPhasesMap, reloadTrigger]);

  /**
     * Handle new connection between an event node and an action node.
     * Only allows event → action edges. Persists the link to the backend via updateStep.
     * @param params the connection parameters from ReactFlow (source, target)
     */
  const onConnect = useCallback(
    (params: Connection) => {
      const sourceNode = nodes.find(n => n.id === params.source);
      if (sourceNode?.type !== 'event') return;
      const targetNode = nodes.find(n => n.id === params.target);
      if (targetNode?.type !== 'action') return;

      const eventId = params.source!;
      const stepId = params.target!;
      const meta = actionMetas[stepId];
      if (!meta) return;

      const currentCondIds = meta.step_condition_ids;
      if (currentCondIds.includes(eventId)) return;
      const newCondIds = [...currentCondIds, eventId];

      updateStep(stepId, buildStepUpdate(meta, newCondIds)).then(() => {
        setActionMetas(prev => ({
          ...prev,
          [stepId]: {
            ...prev[stepId],
            step_condition_ids: newCondIds,
          },
        }));
      });

      setEdges(eds => addEdge({
        ...params,
        type: 'deletable',
        markerEnd: { type: MarkerType.ArrowClosed },
      }, eds));
    },
    [nodes, setEdges, workflowId, actionMetas, buildStepUpdate],
  );

  /**
     * Remove an edge between an event and an action node.
     * Unlinks the event from the step's condition list and persists to the backend.
     * @param source the event node ID
     * @param target the action (step) node ID
     */
  const removeEdge = useCallback(
    (source: string, target: string) => {
      const stepId = target;
      const eventId = source;
      const meta = actionMetas[stepId];
      if (!meta) return;

      const newCondIds = meta.step_condition_ids.filter(id => id !== eventId);

      updateStep(stepId, buildStepUpdate(meta, newCondIds)).then(() => {
        setActionMetas(prev => ({
          ...prev,
          [stepId]: {
            ...prev[stepId],
            step_condition_ids: newCondIds,
          },
        }));
        setEdges(eds => eds.filter(e => !(e.source === eventId && e.target === stepId)));
      });
    },
    [workflowId, actionMetas, setEdges, buildStepUpdate],
  );

  /**
     * Handle batch edge deletion triggered by ReactFlow.
     * @param deletedEdges the list of edges removed by the user
     */
  const onEdgesDelete = useCallback(
    (deletedEdges: Edge[]) => {
      for (const edge of deletedEdges) {
        // Informational edges are read-only visualizations — never unlink steps for them.
        if (edge.type !== 'deletable') continue;
        removeEdge(edge.source, edge.target);
      }
    },
    [removeEdge],
  );

  /**
     * Handle edge deletion from the custom "delete" button on edge overlay.
     * @param _edgeId the edge identifier (unused, kept for signature compatibility)
     * @param source the source node ID (event)
     * @param target the target node ID (action)
     */
  const onDeleteEdgeClick = useCallback(
    (_edgeId: string, source: string, target: string) => {
      removeEdge(source, target);
    },
    [removeEdge],
  );

  /**
     * Request deletion of a node — opens confirmation dialog.
     * @param nodeId the ID of the node to delete
     */
  const requestDeleteNode = useCallback((nodeId: string) => {
    setPendingDeleteNodeId(nodeId);
  }, []);

  /**
     * Actually delete a node after confirmation.
     * Deletes from backend then triggers a full graph refresh to recalculate layout.
     * For event nodes, also unlinks affected steps before refreshing.
     */
  const confirmDeleteNode = useCallback(() => {
    const nodeId = pendingDeleteNodeId;
    if (!nodeId) return;
    setPendingDeleteNodeId(null);

    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;

    const remove = node.type === 'action' ? deleteStep(nodeId) : deleteCondition(nodeId);
    remove.then(() => {
      if (node.type === 'event') {
        // Unlink the deleted event from all steps that reference it before refreshing
        const affectedSteps = Object.entries(actionMetas)
          .filter(([, meta]) => meta.step_condition_ids.includes(nodeId));
        Promise.all(affectedSteps.map(([stepId, meta]) => {
          const newCondIds = meta.step_condition_ids.filter(cid => cid !== nodeId);
          return updateStep(stepId, buildStepUpdate(meta, newCondIds));
        })).then(refreshGraph);
      } else {
        refreshGraph();
      }
    });
  }, [pendingDeleteNodeId, nodes, actionMetas, buildStepUpdate, refreshGraph]);

  /**
     * Open the edit drawer in the parent component with pre-populated data from the step.
     * Called when the user clicks the "Edit" button on an action node.
     * @param nodeId the step ID to edit
     * @param _type the node type (unused)
     */
  const editNode = useCallback((nodeId: string, type: string) => {
    if (type === 'action') {
      const meta = actionMetas[nodeId];
      if (meta && onEditStep) {
        onEditStep(nodeId, meta);
      }
    } else if (type === 'event') {
      const meta = eventMetas[nodeId];
      if (meta && onEditEvent) {
        onEditEvent(nodeId, meta);
      }
    }
  }, [actionMetas, eventMetas, onEditStep, onEditEvent]);

  /**
     * Invert action metas into an "output type → provider actions" map so we can
     * resolve which actions feed a given event condition field.
     */
  const outputProviders = useMemo(() => buildOutputProvidersMap(actionMetas), [actionMetas]);

  // Read-only dotted arrows from provider actions into the selected event (see helper).
  const informationalEdges = useMemo<Edge[]>(
    () => buildInformationalEdges(selectedEventId, eventMetas, outputProviders, theme.palette.warning.main),
    [selectedEventId, eventMetas, outputProviders, theme.palette.warning.main],
  );

  // Numbered path (provider = 1, event = 2, consumer = 3) + highlighted steps
  const { highlightedStepIds, stepPathIndex, eventPathIndex } = useMemo(
    () => buildEventPath(selectedEventId, informationalEdges, actionMetas),
    [selectedEventId, informationalEdges, actionMetas],
  );

  /**
     * Enrich all nodes with edit/delete callbacks so custom node components can trigger actions.
     * Recomputed whenever nodes or callbacks change.
     */
  const nodesWithCallbacks = useMemo(
    () => nodes.map((node) => {
      // When an event is selected, everything outside its data-flow is dimmed
      // to create a spotlight ("backdrop") effect on the highlighted flow.
      let inFlow = false;
      if (node.type === 'event') {
        inFlow = node.id === selectedEventId;
      } else if (node.type === 'action') {
        inFlow = highlightedStepIds.has(node.id);
      }
      const dimmed = !!selectedEventId && !inFlow;
      return {
        ...node,
        style: {
          ...node.style,
          opacity: dimmed ? DIMMED_OPACITY : 1,
          transition: 'opacity 0.2s ease',
        },
        data: {
          ...node.data,
          onEdit: editNode,
          onDelete: requestDeleteNode,
          ...(node.type === 'event'
            ? {
                isSelected: node.id === selectedEventId,
                pathIndex: node.id === selectedEventId ? eventPathIndex : undefined,
                onAddAction: onAddActionToEvent,
              }
            : {}),
          ...(node.type === 'action'
            ? {
                isHighlighted: highlightedStepIds.has(node.id),
                pathIndex: stepPathIndex[node.id],
              }
            : {}),
        },
      };
    }),
    [nodes, editNode, requestDeleteNode, onAddActionToEvent, selectedEventId, highlightedStepIds, stepPathIndex, eventPathIndex],
  );

  /**
     * Enrich all edges with the delete callback so the custom edge component can show a delete button.
     * Recomputed whenever edges or the delete handler changes.
     */
  const edgesWithCallbacks = useMemo(
    () => edges.map((edge) => {
      // Real event → step link belonging to the selected event's flow.
      const inFlow = !!selectedEventId && edge.source === selectedEventId;
      const dimmed = !!selectedEventId && !inFlow;
      return {
        ...edge,
        data: {
          ...edge.data,
          onDelete: onDeleteEdgeClick,
          // Real event → step links are emphasized in blue while their event is selected.
          isHighlighted: inFlow,
          // Faded out when outside the selected event's flow (spotlight backdrop).
          dimmed,
        },
      };
    }),
    [edges, onDeleteEdgeClick, selectedEventId],
  );

  const allEdges = useMemo(
    () => [...edgesWithCallbacks, ...informationalEdges],
    [edgesWithCallbacks, informationalEdges],
  );

  /**
     * Select an event when clicked, or clear the
     * selection when any other node is clicked.
     */
  const onNodeClick = useCallback(
    (_: ReactMouseEvent, node: Node) => {
      setSelectedEventId(node.type === 'event' ? node.id : null);
    },
    [],
  );

  /** Dismiss the informational visualization when clicking on the empty canvas. */
  const onPaneClick = useCallback(() => setSelectedEventId(null), []);

  // Dismiss the informational visualization when pressing Escape (ReactFlow's key hook).
  const escapePressed = useKeyPress('Escape');
  useEffect(() => {
    if (escapePressed) setSelectedEventId(null);
  }, [escapePressed]);

  return (
    <>
      {loading && <Loader variant="inElement" />}
      {!loading && (
        <ReactFlow
          nodes={nodesWithCallbacks}
          edges={allEdges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onEdgesDelete={onEdgesDelete}
          onConnect={onConnect}
          onNodeClick={onNodeClick}
          onPaneClick={onPaneClick}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          proOptions={proOptions}
          fitView
          style={{ background: 'transparent' }}
          defaultEdgeOptions={{
            type: 'deletable',
            markerEnd: { type: MarkerType.ArrowClosed },
            data: { onDelete: onDeleteEdgeClick },
          }}
        >
          <StyledControls
            position="bottom-left"
            showInteractive={false}
            style={{
              background: theme.palette.background.paper,
              border: 'none',
            }}
          />
          <MiniMap
            position="bottom-right"
            pannable
            zoomable
            style={{
              background: theme.palette.background.paper,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 4,
              boxShadow: theme.shadows[3],
              marginRight: theme.spacing(1),
            }}
            maskColor={`${theme.palette.background.default}80`}
            nodeColor={theme.palette.primary.main}
          />
        </ReactFlow>
      )}
      <DialogDelete
        open={pendingDeleteNodeId !== null}
        handleClose={() => setPendingDeleteNodeId(null)}
        handleSubmit={confirmDeleteNode}
        text={t('Are you sure you want to delete this element?')}
      />
    </>
  );
};

export default LogicFlow;
