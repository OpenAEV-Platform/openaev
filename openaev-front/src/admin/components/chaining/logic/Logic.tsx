import { useCallback, useEffect, useState } from 'react';

import { fetchConditions, fetchSteps } from '../../../../actions/chaining/chaining-actions';
import { fetchValidAssets, fetchValidTeams } from '../../../../actions/chaining/workflow-actions';
import type {
  EventOutput,
  ScopeAssetOutput,
  ScopeTeamOutput,
  StepOutput,
} from '../../../../utils/api-types';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import ComponentStepperDrawer, { type DrawerView } from './drawer/ComponentStepperDrawer';
import LogicGraph from './logic-graph/LogicGraph';
import LogicTopBar from './LogicTopBar';
import OutputProvidersProvider from './OutputProvidersContext';
import type { ActionMeta, EventMeta } from './types';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
  /** Owning scenario id (scenario context) - feeds the inject form's team/document providers. */
  scenarioId?: string;
  /** Owning exercise id (simulation context) - feeds the inject form's team/document providers. */
  exerciseId?: string;
  /** Read-only inspection mode (autonomous runs): the AI owns the attack path, so the manual
   *  authoring affordances (top bar, add-component, node edit/delete) are hidden while pan/zoom
   *  and the trigger spotlight stay available. */
  readOnly?: boolean;
}

const Logic = ({ workflowId, context, scenarioId, exerciseId, readOnly = false }: LogicProps) => {
  // Fetch computed valid assets (allowlist minus denylist)
  const [validAssets, setValidAssets] = useState<ScopeAssetOutput[]>([]);
  // Fetch computed valid teams (allowlist minus denylist)
  const [validTeams, setValidTeams] = useState<ScopeTeamOutput[]>([]);
  // Track whether existing steps/events exist
  const [hasExistingData, setHasExistingData] = useState<boolean | null>(null);
  // Count of existing events (used to generate default names)
  const [eventCount, setEventCount] = useState(0);
  // Key to force the graph to re-fetch after a mutation
  const [refreshKey, setRefreshKey] = useState(0);
  // Drawer navigation state (shared with ComponentStepperDrawer)
  const [drawerView, setDrawerView] = useState<DrawerView>('closed');
  // Step currently being edited
  const [editingStep, setEditingStep] = useState<{
    stepId: string;
    meta: ActionMeta;
  } | null>(null);
  // Output type required by the "Add compatible action" banner (pre-filters the action list)
  const [compatibleActionFilter, setCompatibleActionFilter] = useState<string | undefined>();
  // Event to link a newly created action to (set when adding an action via a trigger's "+")
  const [linkToEventId, setLinkToEventId] = useState<string | undefined>();
  // Output types to seed a new trigger with (set when adding a trigger via an action's "+")
  const [prefillEventFields, setPrefillEventFields] = useState<string[] | undefined>();
  // Event currently being edited
  const [editingEvent, setEditingEvent] = useState<{
    eventId: string;
    meta: EventMeta;
  } | null>(null);
  // Latest event metas
  const [eventMetas, setEventMetas] = useState<Record<string, EventMeta>>({});

  useEffect(() => {
    if (workflowId) {
      fetchValidAssets(workflowId).then((assets: ScopeAssetOutput[]) => {
        setValidAssets(assets);
      });
      fetchValidTeams(workflowId).then((teams: ScopeTeamOutput[]) => {
        setValidTeams(teams);
      });
    }
  }, [workflowId]);

  // Check if there are existing steps or events
  useEffect(() => {
    if (!workflowId) return;
    Promise.all([
      fetchSteps(workflowId),
      fetchConditions(workflowId),
    ]).then(([stepsRes, conditionsRes]) => {
      const steps: StepOutput[] = stepsRes.data ?? [];
      const events: EventOutput[] = conditionsRes.data ?? [];
      setHasExistingData(steps.length > 0 || events.length > 0);
      setEventCount(events.length);
    });
  }, [workflowId, refreshKey]);

  const handleStepCreated = useCallback(() => {
    setHasExistingData(true);
    setRefreshKey(k => k + 1);
  }, []);

  const handleEventCreated = useCallback(() => {
    setHasExistingData(true);
    setEventCount(c => c + 1);
    setRefreshKey(k => k + 1);
  }, []);

  const handleOpenDrawer = useCallback(() => {
    setCompatibleActionFilter(undefined);
    setLinkToEventId(undefined);
    setPrefillEventFields(undefined);
    setDrawerView('choose');
  }, []);

  // Opens the action list directly, optionally pre-filtered by output type (warning banner)
  const handleOpenActionDrawer = useCallback((field?: string) => {
    setCompatibleActionFilter(field);
    setLinkToEventId(undefined);
    setPrefillEventFields(undefined);
    setDrawerView('action');
  }, []);

  // Inline "+" on a trigger: add an action gated by that trigger
  const handleAddActionToEvent = useCallback((eventId: string) => {
    setCompatibleActionFilter(undefined);
    setPrefillEventFields(undefined);
    setLinkToEventId(eventId);
    setDrawerView('action');
  }, []);

  // Inline "+" on an action: add a trigger fed by that action's output types
  const handleAddTriggerAfterAction = useCallback((_stepId: string, outputTypes: string[]) => {
    setEditingEvent(null);
    setLinkToEventId(undefined);
    setPrefillEventFields(outputTypes);
    setDrawerView('event');
  }, []);

  const handleEditStep = useCallback((stepId: string, meta: ActionMeta) => {
    setEditingStep({
      stepId,
      meta,
    });
    setDrawerView('actionDetail');
  }, []);

  const handleEditEvent = useCallback((eventId: string, meta: EventMeta) => {
    setEditingEvent({
      eventId,
      meta,
    });
    setPrefillEventFields(undefined);
    setDrawerView('event');
  }, []);

  // Loading state
  if (hasExistingData === null) {
    return null;
  }

  return (
    <OutputProvidersProvider>
      <div
        style={{
          position: 'relative',
          width: '100%',
          height: 'calc(100vh - 340px)',
          overflow: 'hidden',
        }}
      >
        {hasExistingData && workflowId
          ? (
              <>
                <LogicGraph
                  reloadTrigger={refreshKey}
                  workflowId={workflowId}
                  onEditStep={handleEditStep}
                  onEditEvent={handleEditEvent}
                  onAddActionToEvent={handleAddActionToEvent}
                  onAddTriggerAfterAction={handleAddTriggerAfterAction}
                  onEventMetasChange={setEventMetas}
                  readOnly={readOnly}
                />
                {!readOnly && (
                  <LogicTopBar
                    eventMetas={eventMetas}
                    onAddCompatibleAction={handleOpenActionDrawer}
                    onAddComponent={handleOpenDrawer}
                  />
                )}
              </>
            )
          : (
              !readOnly && (
                <AddComponentButton nodeCount={0} context={context} onClick={handleOpenDrawer} />
              )
            )}
      </div>
      <ComponentStepperDrawer
        workflowId={workflowId}
        context={context}
        scenarioId={scenarioId}
        exerciseId={exerciseId}
        validAssets={validAssets}
        validTeams={validTeams}
        drawerView={drawerView}
        onDrawerViewChange={setDrawerView}
        editingStep={editingStep}
        onEditingStepChange={setEditingStep}
        editingEvent={editingEvent}
        onEditingEventChange={setEditingEvent}
        onStepCreated={handleStepCreated}
        onEventCreated={handleEventCreated}
        eventCount={eventCount}
        compatibleActionFilter={compatibleActionFilter}
        onCompatibleActionFilterChange={setCompatibleActionFilter}
        linkToEventId={linkToEventId}
        prefillEventFields={prefillEventFields}
      />
    </OutputProvidersProvider>
  );
};

export default Logic;
