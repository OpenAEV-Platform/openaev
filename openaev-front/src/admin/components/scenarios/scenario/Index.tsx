import { Alert, AlertTitle, Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useMemo, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router';

import { type AutonomousRun } from '../../../../actions/autonomous/autonomous-types';
import { searchInjectTests } from '../../../../actions/inject_test/scenario-inject-test-actions';
import { fetchScenario } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import {
  type Scenario,
  type ScenarioOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { isFeatureEnabled } from '../../../../utils/utils';
import AutonomousReasoningPanel from '../../autonomous/AutonomousReasoningPanel';
import { isAutonomousRunActive } from '../../autonomous/autonomousStatus';
import useAutonomousPanelWidth from '../../autonomous/useAutonomousPanelWidth';
import { useAutonomousRunForScenario } from '../../autonomous/useAutonomousRunForSimulation';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import useHasInjectTests from '../../injects/useHasInjectTests';
import injectContextForScenario from './ScenarioContext';
import ScenarioHeader from './ScenarioHeader';

const ScenarioComponent = lazy(() => import('./Scenario'));
const Injects = lazy(() => import('./injects/ScenarioInjects'));
const InjectCreation = lazy(() => import('./injects/ScenarioInjectCreation'));
const ScenarioAssistant = lazy(() => import('./scenario_assistant/ScenarioAssistant'));
const Tests = lazy(() => import('./tests/ScenarioTests'));
const Lessons = lazy(() => import('./lessons/ScenarioLessons'));
const ScenarioFindings = lazy(() => import('./findings/ScenarioFindings'));
const ScenarioScope = lazy(() => import('./scope/ScenarioScope'));
const ScenarioLogic = lazy(() => import('./logic/ScenarioLogic'));
const ScenarioStatistics = lazy(() => import('./analysis/ScenarioAnalysis'));
const ScenarioAttackPath = lazy(() => import('./attack_path/ScenarioAttackPath'));
const ScenarioExecution = lazy(() => import('./execution/ScenarioExecution'));

const IndexScenarioComponent: FunctionComponent<{
  scenario: ScenarioOutput;
  /** The autonomous run currently owning this scenario (plan-mode design session or a live
   *  autonomous launch), or null for a plain chained/time-based scenario. Drives the AI cockpit. */
  autonomousRun: AutonomousRun | null;
  /** Push a fresher run up (status transitions, a just-started plan/launch) so the hero, tab set and
   *  reasoning panel stay in lockstep without a second poll loop. */
  onAutonomousRunUpdate: (run: AutonomousRun) => void;
  /** Forget the detected run so the overview reverts to the manual view immediately after a normal
   *  launch supersedes a settled AI outcome server-side (no wait for a full page reload). */
  onAutonomousRunCleared: () => void;
}> = ({ scenario, autonomousRun, onAutonomousRunUpdate, onAutonomousRunCleared }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  // Attack path only exists for chained scenarios (workflow-backed), never
  // for time-based ones: same gating as the simulation side.
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH')
    && isChainingFeatureEnabled
    && !!scenario.scenario_workflow_id;
  const isChained = isChainingFeatureEnabled && !!scenario.scenario_workflow_id;
  // The AI cockpit is live only while a run is ACTIVE (the orchestrator is planning or driving). A
  // settled run (PLANNED / completed) leaves the scenario a normal editable chained scenario again,
  // so scope/logic unlock and the overview reverts to the manual one - the operator then reviews the
  // authored steps and launches (or relaunches) from the hero.
  const hasCockpit = isAutonomousRunActive(autonomousRun);
  // Resizable reasoning-panel width, shared with the content padding so the scenario content never
  // renders underneath the panel (mirrors the simulation cockpit).
  const [panelWidth, setPanelWidth] = useAutonomousPanelWidth();
  const contentPaddingRight = hasCockpit ? `${panelWidth}px` : undefined;
  const permissions = useScenarioPermissions(scenario.scenario_id);
  // The Tests tab only exists for email/SMS injects that have actually been
  // tested; hide it entirely otherwise.
  const hasInjectTests = useHasInjectTests(searchInjectTests, scenario.scenario_id);
  // Stable context identities: these providers wrap the whole scenario subtree and a
  // new value each render forces every consumer (incl. the injects list) to re-render.
  const permissionsContext: PermissionsContextType = useMemo(() => ({
    permissions,
    inherited_context: INHERITED_CONTEXT.SCENARIO,
  }), [permissions]);
  const documentContext: DocumentContextType = useMemo(() => ({
    onInitDocument: () => ({
      document_tags: [],
      document_scenarios: scenario
        ? [{
            id: scenario.scenario_id,
            label: scenario.scenario_name,
          }]
        : [],
      document_exercises: [],
    }),
  }), [scenario?.scenario_id, scenario?.scenario_name]);
  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/scenarios/${scenario.scenario_id}/injects`)) {
    tabValue = `/admin/scenarios/${scenario.scenario_id}/injects`;
  } else if (location.pathname.includes(`/admin/scenarios/${scenario.scenario_id}/tests`)) {
    tabValue = `/admin/scenarios/${scenario.scenario_id}/tests`;
  }
  const [openInstantiateSimulationAndStart, setOpenInstantiateSimulationAndStart] = useState<boolean>(false);

  // Chained scenarios expose Scope / Logic / Attack path (workflow-backed); time-based scenarios
  // keep the classic Injects / Tests / Lessons tab set. Autonomy is a launch-time mode now, so the
  // AI cockpit lives on the resulting simulation's detail page, never on the reusable scenario.
  const renderTabs = () => {
    if (isChained) {
      return (
        <Tabs value={tabValue} variant="scrollable" scrollButtons="auto">
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}`}
            value={`/admin/scenarios/${scenario.scenario_id}`}
            label={t('Overview')}
          />
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/scope`}
            value={`/admin/scenarios/${scenario.scenario_id}/scope`}
            label={t('Scope')}
          />
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/logic`}
            value={`/admin/scenarios/${scenario.scenario_id}/logic`}
            label={t('Logic')}
          />
          {isAttackPathEnabled && (
            <Tab
              component={Link}
              to={`/admin/scenarios/${scenario.scenario_id}/attack-path`}
              value={`/admin/scenarios/${scenario.scenario_id}/attack-path`}
              label={t('Attack Path')}
            />
          )}
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/execution`}
            value={`/admin/scenarios/${scenario.scenario_id}/execution`}
            label={t('Execution')}
          />
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/findings`}
            value={`/admin/scenarios/${scenario.scenario_id}/findings`}
            label={t('Findings')}
          />
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/statistics`}
            value={`/admin/scenarios/${scenario.scenario_id}/statistics`}
            label={t('Statistics')}
          />
        </Tabs>
      );
    }
    return (
      <Tabs value={tabValue} variant="scrollable" scrollButtons="auto">
        <Tab
          component={Link}
          to={`/admin/scenarios/${scenario.scenario_id}`}
          value={`/admin/scenarios/${scenario.scenario_id}`}
          label={t('Overview')}
        />
        <Tab
          component={Link}
          to={`/admin/scenarios/${scenario.scenario_id}/injects`}
          value={`/admin/scenarios/${scenario.scenario_id}/injects`}
          label={t('Injects')}
        />
        {hasInjectTests && (
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/tests`}
            value={`/admin/scenarios/${scenario.scenario_id}/tests`}
            label={t('Tests')}
          />
        )}
        {/* The lessons learned module is opt-in (scenario configuration). */}
        {scenario.scenario_lessons_enabled && (
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}/lessons`}
            value={`/admin/scenarios/${scenario.scenario_id}/lessons`}
            label={t('Lessons learned')}
          />
        )}
        <Tab
          component={Link}
          to={`/admin/scenarios/${scenario.scenario_id}/execution`}
          value={`/admin/scenarios/${scenario.scenario_id}/execution`}
          label={t('Execution')}
        />
        <Tab
          component={Link}
          to={`/admin/scenarios/${scenario.scenario_id}/findings`}
          value={`/admin/scenarios/${scenario.scenario_id}/findings`}
          label={t('Findings')}
        />
        {/* Attack path is a chained-scenario concept (workflow logic):
            time-based scenarios never get the tab. */}
        <Tab
          component={Link}
          to={`/admin/scenarios/${scenario.scenario_id}/statistics`}
          value={`/admin/scenarios/${scenario.scenario_id}/statistics`}
          label={t('Statistics')}
        />
      </Tabs>
    );
  };

  // The AI planning / run layer is ADDITIVE, never a replacement: the scenario keeps its FULL normal
  // overview (posture, information, posture trend, kill chain) and the AI outcome (mission, decision
  // timeline, capability gaps and - for a live run - proofs) renders as a layer ON TOP of it, for an
  // active OR a settled run. Passing the current run here (not just a settled one) is what stops
  // "building with AI" from wiping the normal overview - it is the same scenario with a planning
  // layer, not a different page. The live reasoning stream still lives in the right-hand cockpit
  // panel, and Scope / Logic still go read-only while the orchestrator owns them (see hasCockpit).
  const overviewElement = errorWrapper(ScenarioComponent)({
    autonomousRun,
    setOpenInstantiateSimulationAndStart,
    onAutonomousRunCleared,
  });

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <>
          <Box sx={{
            paddingRight: contentPaddingRight,
            transition: 'padding-right 200ms ease',
          }}
          >
            <Breadcrumbs
              variant="list"
              elements={[
                {
                  label: t('Scenarios'),
                  link: '/admin/scenarios',
                },
                {
                  label: scenario.scenario_name,
                  current: true,
                },
              ]}
            />
            <ScenarioHeader
              setOpenInstantiateSimulationAndStart={setOpenInstantiateSimulationAndStart}
              openInstantiateSimulationAndStart={openInstantiateSimulationAndStart}
              autonomousRun={autonomousRun}
              onAutonomousRunUpdate={onAutonomousRunUpdate}
              onAutonomousRunCleared={onAutonomousRunCleared}
            />
            <Box
              sx={{
                borderBottom: 1,
                borderColor: 'divider',
                marginBottom: 2,
              }}
            >
              {renderTabs()}
            </Box>
            <Suspense fallback={<Loader />}>
              <Routes>
                <Route path="" element={overviewElement} />
                {/* Definition merged into the Injects authoring tab; redirect old links. */}
                <Route path="definition" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/injects`} replace />} />
                <Route path="injects" element={errorWrapper(Injects)()} />
                <Route path="injects/create" element={errorWrapper(InjectCreation)()} />
                <Route path="injects/create/:contractId" element={errorWrapper(InjectCreation)()} />
                <Route path="assistant" element={errorWrapper(ScenarioAssistant)()} />
                <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
                <Route path="lessons" element={errorWrapper(Lessons)()} />
                <Route path="findings" element={errorWrapper(ScenarioFindings)()} />
                {isAttackPathEnabled && <Route path="attack-path" element={errorWrapper(ScenarioAttackPath)()} />}
                {/* Live execution of the scenario's latest simulation - available for every scenario
                    type, mirroring the simulation Execution tab. */}
                <Route path="execution" element={errorWrapper(ScenarioExecution)()} />
                {/* Scenario-scoped custom dashboard, surfaced as the Statistics tab. */}
                <Route path="statistics" element={errorWrapper(ScenarioStatistics)()} />
                {/* Statistics replaced the hero dashboard quick action and the old
                    Analysis tab; keep redirects for old links. */}
                <Route path="dashboard" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/statistics`} replace />} />
                <Route path="analysis" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/statistics`} replace />} />
                <Route
                  path="scope"
                  element={hasCockpit
                    ? errorWrapper(ScenarioScope)({
                        readOnly: true,
                        autonomousTimeoutSeconds: autonomousRun?.autonomous_run_timeout_seconds,
                      })
                    : errorWrapper(ScenarioScope)()}
                />
                <Route
                  path="logic"
                  element={hasCockpit
                    ? errorWrapper(ScenarioLogic)({ readOnly: true })
                    : errorWrapper(ScenarioLogic)()}
                />
                {/* Not found */}
                <Route path="*" element={<NotFound />} />
              </Routes>
            </Suspense>
          </Box>
          {hasCockpit && autonomousRun && (
            <AutonomousReasoningPanel
              run={autonomousRun}
              onRunUpdate={onAutonomousRunUpdate}
              width={panelWidth}
              onWidthChange={setPanelWidth}
            />
          )}
        </>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

const Index = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  // Detect an autonomous run owning this scenario, so the detail page can host the same AI cockpit
  // (reasoning panel + gated tabs + run controls) the simulation side has. Autonomy is a launch-time
  // MODE now, not a scenario type: any CHAINED scenario can carry a plan-mode design session or a
  // live autonomous launch, while a TIME-BASED scenario never can - so we probe only chained
  // scenarios (undefined = probe) and skip the lookup entirely otherwise (false = known manual, no
  // 404). While the scenario is still loading we leave it undefined to probe as before.
  const isChained = isFeatureEnabled('INJECT_CHAINING') && !!scenario?.scenario_workflow_id;
  // undefined = still probing (scenario loading, or a chained scenario that may carry a run);
  // false = known manual (a loaded time-based scenario), which skips the lookup and its 404.
  let knownAutonomous: boolean | undefined;
  if (scenario && !isChained) {
    knownAutonomous = false;
  }
  const { run: autonomousRun, resolved: autonomousResolved, setRun: setAutonomousRun, clearRun: clearAutonomousRun } = useAutonomousRunForScenario(
    scenarioId,
    knownAutonomous,
  );
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchScenario(scenarioId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  const scenarioInjectContext = injectContextForScenario(scenario);

  // avoid to show loader if something trigger useDataLoader
  if ((pristine && loading) || !autonomousResolved) {
    return <Loader />;
  }
  if (!loading && !scenario) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Scenario is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <InjectContext.Provider value={scenarioInjectContext}>
      <IndexScenarioComponent
        scenario={scenario}
        autonomousRun={autonomousRun}
        onAutonomousRunUpdate={setAutonomousRun}
        onAutonomousRunCleared={clearAutonomousRun}
      />
    </InjectContext.Provider>
  );
};

export default Index;
