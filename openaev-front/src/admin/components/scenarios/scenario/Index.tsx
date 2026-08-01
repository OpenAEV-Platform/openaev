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
import AutonomousOverview from '../../autonomous/AutonomousOverview';
import AutonomousReasoningPanel from '../../autonomous/AutonomousReasoningPanel';
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
  autonomousRun: AutonomousRun | null;
  onAutonomousRunUpdate: (run: AutonomousRun) => void;
}> = ({ scenario, autonomousRun, onAutonomousRunUpdate }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const isAutonomous = !!autonomousRun;
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  // Attack path only exists for chained scenarios (workflow-backed), never
  // for time-based ones: same gating as the simulation side.
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH')
    && isChainingFeatureEnabled
    && !!scenario.scenario_workflow_id;
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

  // Resizable reasoning-panel width, shared with the content padding so the two stay in lockstep and
  // the scenario content never renders underneath the panel (mirrors the simulation cockpit).
  const [panelWidth, setPanelWidth] = useAutonomousPanelWidth();
  const contentPaddingRight = isAutonomous ? `${panelWidth}px` : undefined;

  // Autonomous scenarios expose a reduced, read-only tab set: the AI owns the scope and logic, so
  // those manual editors are dropped in favour of Overview / Attack path / Findings / Statistics.
  const renderTabs = () => {
    if (isAutonomous) {
      return (
        <Tabs value={tabValue} variant="scrollable" scrollButtons="auto">
          <Tab
            component={Link}
            to={`/admin/scenarios/${scenario.scenario_id}`}
            value={`/admin/scenarios/${scenario.scenario_id}`}
            label={t('Overview')}
          />
          {isAttackPathEnabled && (
            <Tab
              component={Link}
              to={`/admin/scenarios/${scenario.scenario_id}/attack-path`}
              value={`/admin/scenarios/${scenario.scenario_id}/attack-path`}
              label={t('Attack path')}
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
    if (isChainingFeatureEnabled && scenario.scenario_workflow_id) {
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
              label={t('Attack path')}
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
                <Route
                  path=""
                  element={autonomousRun
                    ? <AutonomousOverview run={autonomousRun} />
                    : errorWrapper(ScenarioComponent)({ setOpenInstantiateSimulationAndStart })}
                />
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
                    type (time-based, chained, autonomous), mirroring the simulation Execution tab. */}
                <Route path="execution" element={errorWrapper(ScenarioExecution)()} />
                {/* Scenario-scoped custom dashboard, surfaced as the Statistics tab. */}
                <Route path="statistics" element={errorWrapper(ScenarioStatistics)()} />
                {/* Statistics replaced the hero dashboard quick action and the old
                    Analysis tab; keep redirects for old links. */}
                <Route path="dashboard" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/statistics`} replace />} />
                <Route path="analysis" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/statistics`} replace />} />
                {/* The AI owns scope and logic on an autonomous run: send the manual editors back
                    to the overview instead of exposing them. */}
                <Route
                  path="scope"
                  element={isAutonomous
                    ? <Navigate to={`/admin/scenarios/${scenario.scenario_id}`} replace />
                    : errorWrapper(ScenarioScope)()}
                />
                <Route
                  path="logic"
                  element={isAutonomous
                    ? <Navigate to={`/admin/scenarios/${scenario.scenario_id}`} replace />
                    : errorWrapper(ScenarioLogic)()}
                />
                {/* Not found */}
                <Route path="*" element={<NotFound />} />
              </Routes>
            </Suspense>
          </Box>
          {autonomousRun && (
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
  // Detect whether this scenario is an autonomous (AI-driven) run, so we render the same AI cockpit
  // (reasoning panel + gated tabs + run controls) as the simulation side.
  const { run: autonomousRun, resolved: autonomousResolved, setRun: setAutonomousRun } = useAutonomousRunForScenario(scenarioId);
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
      />
    </InjectContext.Provider>
  );
};

export default Index;
