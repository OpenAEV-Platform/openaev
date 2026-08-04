import { useCallback, useEffect, useState } from 'react';

import { fetchConditions, fetchSteps } from '../../../../actions/chaining/chaining-actions';
import { fetchValidAssets } from '../../../../actions/chaining/workflow-actions';
import type {
  EventOutput,
  ScopeAssetOutput,
  StepOutput,
} from '../../../../utils/api-types';
import AddComponentButton, { type LogicContext } from './AddComponentButton';
import ChainingFlowConfiguration, { type DrawerView } from './chaining_flow/ChainingFlowConfiguration';
import LogicFlow from './chaining_flow/LogicFlow';
import LogicReadOnlyBanner from './LogicReadOnlyBanner';
import LogicTopBar from './LogicTopBar';
import OutputProvidersProvider from './OutputProvidersContext';
import type { ActionMeta, EventMeta } from './types';

interface LogicProps {
  workflowId: string | undefined;
  context: LogicContext;
  /** When true, the logic map is frozen (launched simulation) and no mutation is allowed. */
  readOnly?: boolean;
  /** Message shown in the read-only banner (varies whether the simulation is linked to a scenario). */
  readOnlyMessage?: string;
}

const Logic = ({ workflowId, context, readOnly = false, readOnlyMessage }: LogicProps) => {
  // Fetch computed valid assets (allowlist minus denylist)
  const [validAssets, setValidAssets] = useState<ScopeAssetOutput[]>([]);
  // Track whether existing steps/events exist
  const [hasExistingData, setHasExistingData] = useState<boolean | null>(null);
  // Count of existing events (used to generate default names)
  const [eventCount, setEventCount] = useState(0);
  // Key to force LogicFlow re-mount after adding a step
  const [refreshKey, setRefreshKey] = useState(0);
  // Drawer navigation state (shared with ChainingFlowConfiguration)
  const [drawerView, setDrawerView] = useState<DrawerView>('closed');
  // Step currently being edited
  const [editingStep, setEditingStep] = useState<{
    stepId: string;
    meta: ActionMeta;
  } | null>(null);
    // Output type required by the "Add Compatible Action" banner (pre-filters the action list)
  const [compatibleActionFilter, setCompatibleActionFilter] = useState<string | undefined>();

  // Event to link a newly created action to (set when adding an action via the event node "+")
  const [linkToEventId, setLinkToEventId] = useState<string | undefined>();

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
  }, [workflowId]);

  // If the map becomes read-only while a drawer/edit is open (e.g. the simulation is launched
  // from another tab), force everything back to a consistent read-only state. See ADR-005.
  useEffect(() => {
    if (readOnly) {
      setDrawerView('closed');
      setEditingStep(null);
      setEditingEvent(null);
      setLinkToEventId(undefined);
      setCompatibleActionFilter(undefined);
    }
  }, [readOnly]);

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
    if (readOnly) return;
    setCompatibleActionFilter(undefined);
    setLinkToEventId(undefined);
    setDrawerView('choose');
  }, [readOnly]);

  // Opens the action list directly, optionally pre-filtered by output type
  const handleOpenActionDrawer = useCallback((field?: string) => {
    if (readOnly) return;
    setCompatibleActionFilter(field);
    setLinkToEventId(undefined);
    setDrawerView('action');
  }, [readOnly]);

  // Opens the action list from an event node "+", linking created actions to that event
  const handleAddActionToEvent = useCallback((eventId: string) => {
    if (readOnly) return;
    setCompatibleActionFilter(undefined);
    setLinkToEventId(eventId);
    setDrawerView('action');
  }, [readOnly]);

  const handleEditStep = useCallback((stepId: string, meta: ActionMeta) => {
    if (readOnly) return;
    setEditingStep({
      stepId,
      meta,
    });
    setDrawerView('actionDetail');
  }, [readOnly]);

  const handleEditEvent = useCallback((eventId: string, meta: EventMeta) => {
    if (readOnly) return;
    setEditingEvent({
      eventId,
      meta,
    });
    setDrawerView('event');
  }, [readOnly]);

  // Loading state
  if (hasExistingData === null) {
    return null;
  }

  const emptyState = readOnly
    ? (
        <div style={{ padding: 16 }}>
          <LogicReadOnlyBanner message={readOnlyMessage} />
        </div>
      )
    : <AddComponentButton nodeCount={0} context={context} onClick={handleOpenDrawer} />;

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
                <LogicFlow
                  reloadTrigger={refreshKey}
                  workflowId={workflowId}
                  onEditStep={handleEditStep}
                  onEditEvent={handleEditEvent}
                  onAddActionToEvent={handleAddActionToEvent}
                  onEventMetasChange={setEventMetas}
                  readOnly={readOnly}
                />
                <LogicTopBar
                  eventMetas={eventMetas}
                  onAddCompatibleAction={handleOpenActionDrawer}
                  onAddComponent={handleOpenDrawer}
                  readOnly={readOnly}
                  readOnlyMessage={readOnlyMessage}
                />
              </>
            )
          : (
              emptyState
            )}
      </div>
      <ChainingFlowConfiguration
        workflowId={workflowId}
        validAssets={validAssets}
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
        linkToEventId={linkToEventId}
      />
    </OutputProvidersProvider>
  );
};

export default Logic;
