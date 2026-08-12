import { AccountTreeOutlined, Add } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchConditions, fetchSteps } from '../../../../actions/chaining/chaining-actions';
import { fetchValidAssets, fetchValidTeams } from '../../../../actions/chaining/workflow-actions';
import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import { useFormatter } from '../../../../components/i18n';
import type {
  EventOutput,
  ScopeAssetOutput,
  ScopeTeamOutput,
  StepOutput,
} from '../../../../utils/api-types';
import useLivePolling from '../../../../utils/hooks/useLivePolling';
import useRemainingViewportHeight from '../../../../utils/hooks/useRemainingViewportHeight';
import { type LogicContext } from './AddComponentButton';
import ComponentStepperDrawer, { type DrawerView } from './drawer/ComponentStepperDrawer';
import LogicGraph from './logic-graph/LogicGraph';
import LogicReadOnlyBanner from './LogicReadOnlyBanner';
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
  /** Read-only inspection mode (autonomous runs OR a launched simulation, see ADR-005): the manual
   *  authoring affordances (top bar, add-component, node edit/delete) are hidden while pan/zoom
   *  and the trigger spotlight stay available. */
  readOnly?: boolean;
  /** Message shown in the read-only banner explaining WHY the map is frozen. When omitted, no
   *  banner is rendered (the read-only affordances are still hidden). */
  readOnlyMessage?: string;
}

const Logic = ({ workflowId, context, scenarioId, exerciseId, readOnly = false, readOnlyMessage }: LogicProps) => {
  const { t } = useFormatter();
  // The canvas sizes itself to the exact space left under the page chrome (no page scrollbar).
  const [graphContainerRef, graphHeight] = useRemainingViewportHeight();
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

  // Fingerprint of the workflow's shape (which steps/triggers exist and when each last changed) so a
  // live poll can tell a real edit from a no-op tick: it re-draws the graph on an add, a delete or an
  // in-place edit (the *_updated_at moves), and does nothing at all when the run is quiet.
  const shapeSignatureRef = useRef<string | null>(null);
  const shapeSignature = (steps: StepOutput[], events: EventOutput[]) => [
    steps.map(s => `${s.step_id}:${s.step_updated_at ?? ''}`).sort().join(','),
    events.map(e => `${e.event_id}:${e.event_updated_at ?? ''}`).sort().join(','),
  ].join('|');

  // Single loader for the workflow shape, shared by the initial read, the live poll and the mutation
  // callbacks. It only bumps `refreshKey` (which re-fetches the graph) when the shape actually moved,
  // so a steady poll never resets the user's pan/zoom or selection — the graph is left strictly alone
  // until the AI (or the user) changes something. `force` re-draws right after a local mutation.
  const syncShape = useCallback(async ({ force = false }: { force?: boolean } = {}) => {
    if (!workflowId) {
      return;
    }
    const [stepsRes, conditionsRes] = await Promise.all([
      fetchSteps(workflowId),
      fetchConditions(workflowId),
    ]);
    const steps: StepOutput[] = stepsRes.data ?? [];
    const events: EventOutput[] = conditionsRes.data ?? [];
    setHasExistingData(steps.length > 0 || events.length > 0);
    setEventCount(events.length);
    const signature = shapeSignature(steps, events);
    // First read of this workflow just seeds the fingerprint: the graph fetches itself on mount, so
    // bumping here would be a redundant second fetch.
    const isFirstRead = shapeSignatureRef.current === null;
    if (force || (!isFirstRead && signature !== shapeSignatureRef.current)) {
      setRefreshKey(k => k + 1);
    }
    shapeSignatureRef.current = signature;
  }, [workflowId]);

  // Re-seed and re-read whenever the workflow changes (simulation/scenario switch).
  useEffect(() => {
    shapeSignatureRef.current = null;
    setHasExistingData(null);
    void syncShape();
  }, [workflowId, syncShape]);

  // Keep the Logic tab live: while it is open and visible, poll for shape changes the AI (or another
  // user) commits, so authored steps appear without a manual tab reload. Refreshes on change only.
  useLivePolling(() => {
    void syncShape();
  }, { enabled: !!workflowId });

  const handleStepCreated = useCallback(() => {
    setHasExistingData(true);
    void syncShape({ force: true });
  }, [syncShape]);

  const handleEventCreated = useCallback(() => {
    setHasExistingData(true);
    setEventCount(c => c + 1);
    void syncShape({ force: true });
  }, [syncShape]);

  const handleOpenDrawer = useCallback(() => {
    setCompatibleActionFilter(undefined);
    setLinkToEventId(undefined);
    setDrawerView('choose');
  }, []);

  // Opens the action list directly, optionally pre-filtered by output type (warning banner)
  const handleOpenActionDrawer = useCallback((field?: string) => {
    setCompatibleActionFilter(field);
    setLinkToEventId(undefined);
    setDrawerView('action');
  }, []);

  // Inline "+" on a trigger: add an action gated by that trigger
  const handleAddActionToEvent = useCallback((eventId: string) => {
    setCompatibleActionFilter(undefined);
    setLinkToEventId(eventId);
    setDrawerView('action');
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
    setDrawerView('event');
  }, []);

  // Loading state
  if (hasExistingData === null) {
    return null;
  }

  // Zero-state shown when the workflow has no steps/triggers. Read-only inspection (autonomous run,
  // or a launched simulation) used to render a blank tab — it now gets a proper placeholder that the
  // live poll fills in place. Editable contexts keep the "Add component" call-to-action.
  const emptyState = readOnly
    ? (
        <EmptyPlaceholder
          icon={<AccountTreeOutlined />}
          title={t('No logic to display')}
          message={t('This workflow does not contain any steps or triggers yet. They appear here as they are authored.')}
        />
      )
    : (
        <EmptyPlaceholder
          icon={<AccountTreeOutlined />}
          title={t('No components yet')}
          message={context === 'scenario'
            ? t('Start adding components to complete the configuration of your scenario.')
            : t('Start adding components to complete the configuration of your simulation.')}
          action={(
            <Button
              variant="contained"
              color="primary"
              size="large"
              startIcon={<Add />}
              onClick={handleOpenDrawer}
            >
              {t('Add component')}
            </Button>
          )}
        />
      );

  return (
    <OutputProvidersProvider>
      {readOnly && readOnlyMessage && <LogicReadOnlyBanner message={readOnlyMessage} />}
      <div
        ref={graphContainerRef}
        style={{
          position: 'relative',
          width: '100%',
          height: graphHeight ?? 'calc(100vh - 340px)',
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
          : emptyState}
      </div>
      <ComponentStepperDrawer
        workflowId={workflowId}
        context={context}
        readOnly={readOnly}
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
      />
    </OutputProvidersProvider>
  );
};

export default Logic;
