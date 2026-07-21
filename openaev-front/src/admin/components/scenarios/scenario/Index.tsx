import { Alert, AlertTitle, Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useMemo, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router';

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
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { isFeatureEnabled } from '../../../../utils/utils';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
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
const ScenarioDashboard = lazy(() => import('./analysis/ScenarioAnalysis'));

const MS_PER_DAY = 1000 * 60 * 60 * 24;

const IndexScenarioComponent: FunctionComponent<{ scenario: ScenarioOutput }> = ({ scenario }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const permissions = useScenarioPermissions(scenario.scenario_id);
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
  const [openScenarioRecurringFormDialog, setOpenScenarioRecurringFormDialog] = useState<boolean>(false);
  const [openInstantiateSimulationAndStart, setOpenInstantiateSimulationAndStart] = useState<boolean>(false);
  const [selectRecurring, setSelectRecurring] = useState('noRepeat');
  const [cronObject, setCronObject] = useState<PeriodExpressionHandler | null>(handle(scenario.scenario_recurrence));
  const noRepeat = !!scenario.scenario_recurrence_end && !!scenario.scenario_recurrence_start
    && new Date(scenario.scenario_recurrence_end).getTime() - new Date(scenario.scenario_recurrence_start).getTime() <= MS_PER_DAY
    && ['noRepeat', 'daily'].includes(selectRecurring);
  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <>
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
            cronObject={cronObject}
            setCronObject={setCronObject}
            setSelectRecurring={setSelectRecurring}
            selectRecurring={selectRecurring}
            setOpenScenarioRecurringFormDialog={setOpenScenarioRecurringFormDialog}
            openScenarioRecurringFormDialog={openScenarioRecurringFormDialog}
            setOpenInstantiateSimulationAndStart={setOpenInstantiateSimulationAndStart}
            openInstantiateSimulationAndStart={openInstantiateSimulationAndStart}
            noRepeat={noRepeat}
          />
          <Box
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              marginBottom: 2,
            }}
          >
            {
              isChainingFeatureEnabled && scenario.scenario_workflow_id ? (
                <Tabs
                  value={tabValue}
                  variant="scrollable"
                  scrollButtons="auto"
                >
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
                </Tabs>
              ) : (
                <Tabs
                  value={tabValue}
                  variant="scrollable"
                  scrollButtons="auto"
                >
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
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/tests`}
                    value={`/admin/scenarios/${scenario.scenario_id}/tests`}
                    label={t('Tests')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/lessons`}
                    value={`/admin/scenarios/${scenario.scenario_id}/lessons`}
                    label={t('Lessons learned')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/findings`}
                    value={`/admin/scenarios/${scenario.scenario_id}/findings`}
                    label={t('Findings')}
                  />
                </Tabs>
              )
            }
          </Box>
          <Suspense fallback={<Loader />}>
            <Routes>
              <Route path="" element={errorWrapper(ScenarioComponent)({ setOpenInstantiateSimulationAndStart })} />
              {/* Definition merged into the Injects authoring tab; redirect old links. */}
              <Route path="definition" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/injects`} replace />} />
              <Route path="injects" element={errorWrapper(Injects)()} />
              <Route path="injects/create" element={errorWrapper(InjectCreation)()} />
              <Route path="injects/create/:contractId" element={errorWrapper(InjectCreation)()} />
              <Route path="assistant" element={errorWrapper(ScenarioAssistant)()} />
              <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
              <Route path="lessons" element={errorWrapper(Lessons)()} />
              <Route path="findings" element={errorWrapper(ScenarioFindings)()} />
              {/* Scenario-scoped custom dashboard, reached from the hero "Analyze" quick action. */}
              <Route path="dashboard" element={errorWrapper(ScenarioDashboard)()} />
              {/* Analysis is no longer a permanent tab; keep a redirect for old links. */}
              <Route path="analysis" element={<Navigate to={`/admin/scenarios/${scenario.scenario_id}/dashboard`} replace />} />
              <Route path="scope" element={errorWrapper(ScenarioScope)()} />
              <Route path="logic" element={errorWrapper(ScenarioLogic)()} />
              {/* Not found */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
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
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchScenario(scenarioId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  const scenarioInjectContext = injectContextForScenario(scenario);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
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
      <IndexScenarioComponent scenario={scenario} />
    </InjectContext.Provider>
  );
};

export default Index;
