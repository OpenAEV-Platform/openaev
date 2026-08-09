import { alpha, useTheme } from '@mui/material/styles';
import { type PointerEvent as ReactPointerEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

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
import {
  buildActionMetas,
  buildEventData,
  buildEventFlow,
  buildOutputProvidersMap,
  buildTacticForStep,
  buildTriggerFanIn,
  enrichActionMetasWithContracts,
  summarizeEventConditions,
} from '../logic-flow-helpers';
import type { ActionMeta, EventMeta } from '../types';
import { useOutputProviders } from '../useOutputProviders';
import Connectors from './Connectors';
import GraphActionCard from './GraphActionCard';
import GraphTriggerCard from './GraphTriggerCard';
import { buildLogicGraphLayout, type PositionedBox, routeOrthogonalEdge } from './layout';
import PanZoom from './PanZoom';

interface NodePosition {
  x: number;
  y: number;
}

/** Decode HTML entities that occasionally survive in orchestrator-authored titles ("&amp;" -> "&"). */
const decodeEntities = (value?: string): string =>
  (value ?? '')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, '\'')
    .replace(/&#x27;/gi, '\'')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&');

interface ConnectDrag {
  fromId: string;
  fromKind: 'action' | 'trigger';
  x0: number;
  y0: number;
  x: number;
  y: number;
}

interface LogicGraphProps {
  workflowId: string;
  reloadTrigger?: number;
  /** Edit an existing action (opens the stepper drawer directly at the configure step). */
  onEditStep?: (stepId: string, meta: ActionMeta) => void;
  onEditEvent?: (eventId: string, meta: EventMeta) => void;
  /** Inline "+": add an action gated by this trigger. */
  onAddActionToEvent?: (eventId: string) => void;
  /** Reports the latest event metas so the parent can drive the warning banner. */
  onEventMetasChange?: (metas: Record<string, EventMeta>) => void;
  /** Read-only inspection mode (autonomous runs): keeps pan/zoom + spotlight, disables mutation. */
  readOnly?: boolean;
}

/**
 * Custom causal-flow visualization of a chained workflow, replacing the React Flow canvas. Lays out
 * actions and triggers as an auto-arranged left-to-right DAG (see {@link buildLogicGraphLayout}),
 * renders them as cards over an SVG connector layer inside a hand-rolled pan/zoom viewport, and owns
 * selection, the data-flow spotlight, deletion, and the inline "+" build affordances.
 */
const LogicGraph = ({
  workflowId,
  reloadTrigger,
  onEditStep,
  onEditEvent,
  onAddActionToEvent,
  onEventMetasChange,
  readOnly = false,
}: LogicGraphProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { setProviders: setContextProviders } = useOutputProviders();

  const { killChainPhasesMap } = useHelper(
    (helper: KillChainPhaseHelper) => ({ killChainPhasesMap: helper.getKillChainPhasesMap() }),
  );

  const [actionMetas, setActionMetas] = useState<Record<string, ActionMeta>>({});
  const [eventMetas, setEventMetas] = useState<Record<string, EventMeta>>({});
  const [loading, setLoading] = useState(true);

  // Derived from the live kill-chain map so tactic chips update as soon as the map loads,
  // without re-running the data fetch (the map is otherwise read via a ref during refresh).
  const tacticForStep = useMemo(
    () => buildTacticForStep({
      actionMetas,
      killChainPhasesMap: killChainPhasesMap as Record<string, KillChainPhase>,
      fallbackTactic: t('Other'),
    }),
    [actionMetas, killChainPhasesMap, t],
  );

  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [pendingDeleteNodeId, setPendingDeleteNodeId] = useState<string | null>(null);
  const [connectDrag, setConnectDrag] = useState<ConnectDrag | null>(null);

  // Manual position overrides (session-only escape hatch when the auto-layout is not ideal). Cleared
  // by "Auto-organize". `refitNonce` bumps the fit signature so clearing them re-centers the view.
  const [positionOverrides, setPositionOverrides] = useState<Record<string, NodePosition>>({});
  const [refitNonce, setRefitNonce] = useState(0);
  // Live zoom, published by PanZoom, used to convert a screen drag delta into logical units.
  const zoomRef = useRef(1);

  /** Build the payload for updateStep, preserving mapper conditions while changing the gating list. */
  const buildStepUpdate = useCallback((action: ActionMeta, newCondIds: string[]) => {
    const stepConditions: ConditionCreateInput[] = action.step_conditions.map((c, i) => ({
      condition_temporary_id: String(i),
      condition_type: 'MAPPER' as const,
      condition_key_types: (
        c.condition_key_types && c.condition_key_types.length > 0 ? c.condition_key_types : ['text']
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
        inject_asset_groups: action.inject_asset_groups,
        inject_all_teams: action.inject_all_teams,
        inject_teams: action.inject_teams,
        inject_documents: action.inject_documents,
        inject_content: action.inject_content,
      },
    };
  }, [workflowId]);

  const refreshGraph = useCallback(async () => {
    const [stepsRes, eventsRes] = await Promise.all([
      fetchSteps(workflowId),
      fetchConditions(workflowId),
    ]);

    const parsedActionMetas = buildActionMetas(stepsRes.data);
    const { eventMetas: parsedEventMetas } = buildEventData(eventsRes.data);
    const enrichedActionMetas = await enrichActionMetasWithContracts(parsedActionMetas, workflowId);

    setActionMetas(enrichedActionMetas);
    setContextProviders(buildOutputProvidersMap(enrichedActionMetas));
    setEventMetas(parsedEventMetas);
    onEventMetasChange?.(parsedEventMetas);
    setLoading(false);
  }, [workflowId, setContextProviders, onEventMetasChange]);

  useEffect(() => {
    refreshGraph();
  }, [refreshGraph, reloadTrigger]);

  const outputProviders = useMemo(
    () => buildOutputProvidersMap(actionMetas),
    [actionMetas],
  );

  // Tactic name -> kill-chain phase order, so the layout orders the tactic columns by MITRE phase
  // (keeping the lowest order when a tactic maps to several phases).
  const tacticOrder = useMemo(() => {
    const order: Record<string, number> = {};
    for (const phase of Object.values(killChainPhasesMap as Record<string, KillChainPhase>)) {
      const name = phase.phase_name;
      if (!name) continue;
      const value = phase.phase_order ?? 99;
      if (order[name] === undefined || value < order[name]) order[name] = value;
    }
    return order;
  }, [killChainPhasesMap]);

  const layout = useMemo(
    () => buildLogicGraphLayout({
      actionMetas,
      eventMetas,
      outputProviders,
      tacticForStep,
      tacticOrder,
    }),
    [actionMetas, eventMetas, outputProviders, tacticForStep, tacticOrder],
  );

  // Apply manual overrides on top of the auto-layout positions.
  const positionedNodes = useMemo(
    () => layout.nodes.map((node) => {
      const override = positionOverrides[node.id];
      return override
        ? {
            ...node,
            x: override.x,
            y: override.y,
          }
        : node;
    }),
    [layout.nodes, positionOverrides],
  );

  const nodeBoxById = useMemo(() => {
    const map: Record<string, PositionedBox> = {};
    for (const node of positionedNodes) {
      map[node.id] = {
        x: node.x,
        y: node.y,
        width: node.width,
        height: node.height,
      };
    }
    return map;
  }, [positionedNodes]);

  // Re-route connectors from the effective positions so edges follow nodes as they are dragged.
  const routedEdges = useMemo(
    () => layout.edges.map((edge) => {
      const source = nodeBoxById[edge.source];
      const target = nodeBoxById[edge.target];
      if (!source || !target) return edge;
      return {
        ...edge,
        ...routeOrthogonalEdge(source, target),
      };
    }),
    [layout.edges, nodeBoxById],
  );

  // Grow the logical canvas so dragged-out nodes stay reachable (never shrinks below the auto bbox).
  const contentSize = useMemo(() => {
    let width = layout.bbox.width;
    let height = layout.bbox.height;
    for (const node of positionedNodes) {
      width = Math.max(width, node.x + node.width + 48);
      height = Math.max(height, node.y + node.height + 48);
    }
    return {
      width,
      height,
    };
  }, [positionedNodes, layout.bbox]);

  // Stable fingerprint of the graph structure so PanZoom only re-fits on real changes (not on
  // selection, dragging, or polling re-renders that keep the same nodes). `refitNonce` forces a
  // re-fit after "Auto-organize" restores the auto positions.
  const fitSignature = useMemo(
    () => `${Math.round(layout.bbox.width)}x${Math.round(layout.bbox.height)}|${layout.nodes.map(n => n.id).join(',')}|${refitNonce}`,
    [layout, refitNonce],
  );

  // Data-flow spotlight for the selected node (action or trigger). `flow` resolves one representative
  // line (numbered badges + the actions a trigger gates); `fanIn` adds EVERY interchangeable producer
  // upstream so selecting a trigger lights up all actions that can satisfy it, not just the first one.
  const flow = useMemo(
    () => buildEventFlow(selectedNodeId, eventMetas, actionMetas, theme.palette.warning.main),
    [selectedNodeId, eventMetas, actionMetas, theme.palette.warning.main],
  );
  const fanIn = useMemo(
    () => buildTriggerFanIn(selectedNodeId, eventMetas, actionMetas),
    [selectedNodeId, eventMetas, actionMetas],
  );

  // Union of the representative line and the full upstream fan-in (plus the trigger's consumers).
  const highlightedSteps = useMemo(
    () => new Set<string>([...flow.highlightedStepIds, ...fanIn.highlightedStepIds]),
    [flow, fanIn],
  );
  const highlightedEvents = useMemo(
    () => new Set<string>([...flow.highlightedEventIds, ...fanIn.highlightedEventIds]),
    [flow, fanIn],
  );

  // An edge belongs to the spotlight when both endpoints are highlighted: this lights up every
  // producer -> trigger and trigger -> action link inside the fan-in, not only the primary line.
  const highlightedEdgeIds = useMemo(() => {
    const ids = new Set<string>();
    if (!selectedNodeId) return ids;
    const inSpotlight = (id: string) => highlightedSteps.has(id) || highlightedEvents.has(id);
    for (const edge of layout.edges) {
      if (inSpotlight(edge.source) && inSpotlight(edge.target)) ids.add(edge.id);
    }
    return ids;
  }, [layout.edges, selectedNodeId, highlightedSteps, highlightedEvents]);

  const isDimmed = useCallback((nodeId: string) => {
    if (!selectedNodeId) return false;
    return !(highlightedSteps.has(nodeId) || highlightedEvents.has(nodeId));
  }, [selectedNodeId, highlightedSteps, highlightedEvents]);

  const handleSelect = useCallback((id: string) => {
    setSelectedNodeId(prev => (prev === id ? null : id));
  }, []);

  // Node drag: a pointerdown on a card body starts a potential move. Below the threshold it stays a
  // plain click and selects the node (preserving the spotlight behaviour); beyond it, the card is
  // repositioned by the screen delta converted to logical units via the live zoom. Read-only mode
  // keeps selection but never moves cards.
  const handleNodePointerDown = useCallback((
    nodeId: string,
    baseX: number,
    baseY: number,
    event: ReactPointerEvent<HTMLDivElement>,
  ) => {
    if (event.button !== 0) return;
    event.stopPropagation();
    const startX = event.clientX;
    const startY = event.clientY;
    let moved = false;

    const move = (ev: PointerEvent) => {
      const dx = ev.clientX - startX;
      const dy = ev.clientY - startY;
      if (!moved && Math.hypot(dx, dy) > 4) moved = true;
      if (moved && !readOnly) {
        const zoom = zoomRef.current || 1;
        setPositionOverrides(prev => ({
          ...prev,
          [nodeId]: {
            x: Math.max(0, baseX + dx / zoom),
            y: Math.max(0, baseY + dy / zoom),
          },
        }));
      }
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      if (!moved) handleSelect(nodeId);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }, [readOnly, handleSelect]);

  const handleAutoLayout = useCallback(() => {
    setPositionOverrides({});
    setRefitNonce(n => n + 1);
  }, []);

  const handleEditAction = useCallback((stepId: string) => {
    const meta = actionMetas[stepId];
    if (meta) onEditStep?.(stepId, meta);
  }, [actionMetas, onEditStep]);

  const handleEditTrigger = useCallback((eventId: string) => {
    const meta = eventMetas[eventId];
    if (meta) onEditEvent?.(eventId, meta);
  }, [eventMetas, onEditEvent]);

  // Remove a real gating link: a real edge is `trigger (source) -> action (target)`, so we drop the
  // trigger from that action's condition list without deleting either node.
  const handleDeleteEdge = useCallback((edge: {
    source: string;
    target: string;
    kind: string;
  }) => {
    if (edge.kind !== 'real') return;
    const action = actionMetas[edge.target];
    if (!action) return;
    updateStep(
      edge.target,
      buildStepUpdate(action, action.step_condition_ids.filter(cid => cid !== edge.source)),
    ).then(refreshGraph);
  }, [actionMetas, buildStepUpdate, refreshGraph]);

  // Gate an action by a trigger (real `trigger -> action` edge) by adding the trigger to the
  // action's condition list. Only the trigger -> action direction is accepted: a user can gate an
  // action with an event, never manually link an action to an event (that relationship exists only
  // as the automatic, informational inferred edge). The drag can only start from a trigger, so
  // `aKind` is always 'trigger'; the guard stays as a defensive no-op for any other combination.
  const linkNodes = useCallback((
    aId: string, aKind: 'action' | 'trigger', bId: string, bKind: 'action' | 'trigger',
  ) => {
    if (aKind !== 'trigger' || bKind !== 'action') {
      return;
    }
    const actionId = bId;
    const triggerId = aId;
    const action = actionMetas[actionId];
    if (!action || action.step_condition_ids.includes(triggerId)) return;
    updateStep(actionId, buildStepUpdate(action, [...action.step_condition_ids, triggerId]))
      .then(refreshGraph);
  }, [actionMetas, buildStepUpdate, refreshGraph]);

  // Screen-space connection drag: preview line follows the cursor in client coordinates (so it is
  // immune to the pan/zoom transform), and the drop target is resolved with elementFromPoint against
  // the data-node-id wrappers. Listeners are attached imperatively for the lifetime of one drag.
  const handleConnectStart = useCallback((
    id: string, kind: 'action' | 'trigger', event: ReactPointerEvent<HTMLElement>,
  ) => {
    event.stopPropagation();
    event.preventDefault();
    const rect = event.currentTarget.getBoundingClientRect();
    const x0 = rect.left + rect.width / 2;
    const y0 = rect.top + rect.height / 2;
    setConnectDrag({
      fromId: id,
      fromKind: kind,
      x0,
      y0,
      x: event.clientX,
      y: event.clientY,
    });

    const move = (ev: PointerEvent) => {
      setConnectDrag(prev => (prev
        ? {
            ...prev,
            x: ev.clientX,
            y: ev.clientY,
          }
        : prev));
    };
    const up = (ev: PointerEvent) => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      const targetWrapper = (document.elementFromPoint(ev.clientX, ev.clientY) as HTMLElement | null)
        ?.closest('[data-node-id]') as HTMLElement | null;
      const targetId = targetWrapper?.getAttribute('data-node-id') ?? null;
      const targetKind = targetWrapper?.getAttribute('data-node-kind') as 'action' | 'trigger' | null;
      if (targetId && targetKind && targetId !== id) {
        linkNodes(id, kind, targetId, targetKind);
      }
      setConnectDrag(null);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }, [linkNodes]);

  const confirmDeleteNode = useCallback(() => {
    const nodeId = pendingDeleteNodeId;
    if (!nodeId) return;
    setPendingDeleteNodeId(null);

    const isAction = !!actionMetas[nodeId];
    const remove = isAction ? deleteStep(nodeId) : deleteCondition(nodeId);
    remove.then(() => {
      if (!isAction) {
        // Unlink the deleted trigger from every step that references it before refreshing.
        const affectedSteps = Object.entries(actionMetas)
          .filter(([, meta]) => meta.step_condition_ids.includes(nodeId));
        Promise.all(affectedSteps.map(([stepId, meta]) =>
          updateStep(stepId, buildStepUpdate(meta, meta.step_condition_ids.filter(cid => cid !== nodeId))),
        )).then(refreshGraph);
      } else {
        refreshGraph();
      }
    });
  }, [pendingDeleteNodeId, actionMetas, buildStepUpdate, refreshGraph]);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  return (
    <>
      <PanZoom
        contentWidth={contentSize.width}
        contentHeight={contentSize.height}
        fitSignature={fitSignature}
        onBackgroundClick={() => setSelectedNodeId(null)}
        onZoomChange={(zoom) => { zoomRef.current = zoom; }}
        onAutoLayout={readOnly ? undefined : handleAutoLayout}
      >
        {/* MITRE-tactic columns: one padded band behind each tactic's action cards, headed by the
            tactic name. Every band uses the SAME theme blue — a real chain carries far too many
            tactics for a colour cycle to stay legible, and near-identical hues would read as a
            meaning they do not carry; the column and its header already identify the tactic. The
            layout gives every tactic its own column, so bands are side by side and can never overlap.
            Rendered first so they sit behind the connectors and cards, and they never intercept
            pointer events. */}
        {layout.columns.map(column => (
          <div
            key={`tactic-col-${column.tactic}`}
            style={{
              position: 'absolute',
              left: column.x,
              top: column.y,
              width: column.width,
              height: column.height,
              pointerEvents: 'none',
              borderRadius: 16,
              backgroundColor: alpha(theme.palette.primary.main, 0.06),
              border: `1px solid ${alpha(theme.palette.primary.main, 0.28)}`,
              boxSizing: 'border-box',
            }}
          >
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                height: column.headerHeight,
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '0 12px',
                boxSizing: 'border-box',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                fontSize: 11,
                fontWeight: 700,
                letterSpacing: '0.09em',
                textTransform: 'uppercase',
                color: theme.palette.primary.main,
              }}
            >
              {column.tactic || t('Other')}
            </div>
          </div>
        ))}
        <Connectors
          edges={routedEdges}
          width={contentSize.width}
          height={contentSize.height}
          highlightedEdgeIds={highlightedEdgeIds}
          selectionActive={!!selectedNodeId}
          onDeleteEdge={handleDeleteEdge}
          readOnly={readOnly}
        />
        {positionedNodes.map((node) => {
          const dimmed = isDimmed(node.id);
          if (node.kind === 'action') {
            const meta = actionMetas[node.id];
            if (!meta) return null;
            return (
              <div
                key={node.id}
                data-node-id={node.id}
                data-node-kind="action"
                onPointerDown={e => handleNodePointerDown(node.id, node.x, node.y, e)}
                style={{
                  position: 'absolute',
                  left: node.x,
                  top: node.y,
                  width: node.width,
                  height: node.height,
                }}
              >
                <GraphActionCard
                  id={node.id}
                  title={decodeEntities(meta.inject_title)}
                  description={decodeEntities(meta.inject_description)}
                  injectorType={meta.inject_injector}
                  payloadType={meta.inject_payload_collector_type ?? meta.inject_payload_type}
                  isPayload={!!meta.inject_payload_type}
                  tacticLabel={tacticForStep[node.id]}
                  outputTypes={meta.step_output_types ?? []}
                  targetCount={meta.inject_assets?.length ?? 0}
                  triggerCount={meta.step_condition_ids?.length ?? 0}
                  highlighted={highlightedSteps.has(node.id)}
                  dimmed={dimmed}
                  pathIndex={flow.pathIndex[node.id]}
                  readOnly={readOnly}
                  onEdit={handleEditAction}
                  onDelete={setPendingDeleteNodeId}
                />
              </div>
            );
          }

          const meta = eventMetas[node.id];
          if (!meta) return null;
          const summary = summarizeEventConditions(meta.formData);
          const isSelected = node.id === selectedNodeId;
          const onFlow = highlightedEvents.has(node.id);
          return (
            <div
              key={node.id}
              data-node-id={node.id}
              data-node-kind="trigger"
              onPointerDown={e => handleNodePointerDown(node.id, node.x, node.y, e)}
              style={{
                position: 'absolute',
                left: node.x,
                top: node.y,
                width: node.width,
                height: node.height,
              }}
            >
              <GraphTriggerCard
                id={node.id}
                name={decodeEntities(meta.formData.name)}
                description={decodeEntities(meta.formData.description)}
                conditionFields={summary.fields}
                conditionLines={summary.lines}
                conditionOperator={summary.operator}
                selected={isSelected}
                highlighted={onFlow}
                dimmed={dimmed}
                pathIndex={flow.pathIndex[node.id]}
                readOnly={readOnly}
                onEdit={handleEditTrigger}
                onDelete={setPendingDeleteNodeId}
                onAddAction={onAddActionToEvent}
                onConnectStart={handleConnectStart}
              />
            </div>
          );
        })}
      </PanZoom>

      {connectDrag && (
        <svg
          style={{
            position: 'fixed',
            inset: 0,
            width: '100vw',
            height: '100vh',
            pointerEvents: 'none',
            zIndex: 1400,
          }}
        >
          <line
            x1={connectDrag.x0}
            y1={connectDrag.y0}
            x2={connectDrag.x}
            y2={connectDrag.y}
            stroke={theme.palette.warning.main}
            strokeWidth={2}
            strokeDasharray="5 4"
          />
        </svg>
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

export default LogicGraph;
