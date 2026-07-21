import { Alert, AlertTitle, Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router';

import { fetchExercise } from '../../../../actions/Exercise';
import { fetchScenarioFromSimulation } from '../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type Exercise as ExerciseType, type SimulationDetails } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { isFeatureEnabled } from '../../../../utils/utils';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import injectContextForExercise from './ExerciseContext';
import ExerciseHeader from './ExerciseHeader';

const Simulation = lazy(() => import('./overview/SimulationComponent'));
const SimulationDashboard = lazy(() => import('./analysis/SimulationAnalysis'));
const Lessons = lazy(() => import('./lessons/SimulationLessons'));
const SimulationFindings = lazy(() => import('./findings/SimulationFindings'));
const Injects = lazy(() => import('./injects/ExerciseInjects'));
const Tests = lazy(() => import('./tests/ExerciseTests'));
const TimelineOverview = lazy(() => import('./timeline/TimelineOverview'));
const Mails = lazy(() => import('./mails/Mails'));
const MailsInject = lazy(() => import('./mails/Inject'));
const Logs = lazy(() => import('./logs/Logs'));
const Chat = lazy(() => import('./chat/Chat'));
const Validations = lazy(() => import('./validation/Validations'));
const SimulationScope = lazy(() => import('./scope/SimulationScope'));
const SimulationLogic = lazy(() => import('./logic/SimulationLogic'));
const SimulationAttackPath = lazy(() => import('./attack_path/SimulationAttackPath'));

const IndexComponent: FunctionComponent<{ exercise: SimulationDetails }> = ({ exercise }) => {
  const [isLoading, setIsLoading] = useState(false);
  const { t } = useFormatter();
  const location = useLocation();
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH');
  const permissions = useSimulationPermissions(exercise.exercise_id, exercise);
  // Stable context identities: these providers wrap the whole simulation subtree and a
  // new value each render forces every consumer (incl. the injects list) to re-render.
  const permissionsContext: PermissionsContextType = useMemo(() => ({
    permissions,
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  }), [permissions]);
  const documentContext: DocumentContextType = useMemo(() => ({
    onInitDocument: () => ({
      document_tags: [],
      document_scenarios: [],
      document_exercises: exercise
        ? [{
            id: exercise.exercise_id,
            label: exercise.exercise_name,
          }]
        : [],
    }),
  }), [exercise?.exercise_id, exercise?.exercise_name]);

  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/injects`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/injects`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/animation`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/animation`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/results`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/results`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/tests`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/tests`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/attack-path`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/attack-path`;
  }

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>

        <div style={{ paddingRight: ['/results', '/animation'].some(el => location.pathname.includes(el)) ? 200 : 0 }}>
          <Breadcrumbs
            variant="object"
            elements={[
              {
                label: t('Simulations'),
                link: '/admin/simulations',
              },
              {
                label: exercise.exercise_name,
                current: true,
              },
            ]}
          />
          <ExerciseHeader onLoading={setIsLoading} isLoading={isLoading} />
          {isLoading
            ? <Loader />
            : (
                <>
                  <Box
                    sx={{
                      borderBottom: 1,
                      borderColor: 'divider',
                      marginBottom: 2,
                    }}
                  >
                    {isChainingFeatureEnabled && exercise.exercise_workflow_id
                      ? (
                          <Tabs value={tabValue}>
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}`}
                              value={`/admin/simulations/${exercise.exercise_id}`}
                              label={t('Overview')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/scope`}
                              value={`/admin/simulations/${exercise.exercise_id}/scope`}
                              label={t('Scope')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/logic`}
                              value={`/admin/simulations/${exercise.exercise_id}/logic`}
                              label={t('Logic')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/animation`}
                              value={`/admin/simulations/${exercise.exercise_id}/animation`}
                              label={t('Animation')}
                            />
                            {isAttackPathEnabled && (
                              <Tab
                                component={Link}
                                to={`/admin/simulations/${exercise.exercise_id}/attack-path`}
                                value={`/admin/simulations/${exercise.exercise_id}/attack-path`}
                                label={t('Attack path')}
                              />
                            )}
                          </Tabs>
                        )
                      : (
                          <Tabs value={tabValue}>
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}`}
                              value={`/admin/simulations/${exercise.exercise_id}`}
                              label={t('Overview')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/injects`}
                              value={`/admin/simulations/${exercise.exercise_id}/injects`}
                              label={t('Injects')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/tests`}
                              value={`/admin/simulations/${exercise.exercise_id}/tests`}
                              label={t('Tests')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/animation`}
                              value={`/admin/simulations/${exercise.exercise_id}/animation`}
                              label={t('Animation')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/lessons`}
                              value={`/admin/simulations/${exercise.exercise_id}/lessons`}
                              label={t('Lessons learned')}
                            />
                            <Tab
                              component={Link}
                              to={`/admin/simulations/${exercise.exercise_id}/findings`}
                              value={`/admin/simulations/${exercise.exercise_id}/findings`}
                              label={t('Findings')}
                            />
                            {isAttackPathEnabled && (
                              <Tab
                                component={Link}
                                to={`/admin/simulations/${exercise.exercise_id}/attack-path`}
                                value={`/admin/simulations/${exercise.exercise_id}/attack-path`}
                                label={t('Attack path')}
                              />
                            )}
                          </Tabs>
                        )}
                  </Box>
                  <Suspense fallback={<Loader />}>
                    <Routes>
                      <Route path="" element={errorWrapper(Simulation)()} />
                      {/* Definition merged into the Injects authoring tab; redirect old links. */}
                      <Route path="definition" element={<Navigate to={`/admin/simulations/${exercise.exercise_id}/injects`} replace={true} />} />
                      <Route path="injects" element={errorWrapper(Injects)()} />
                      <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
                      <Route path="animation" element={<Navigate to="timeline" replace={true} />} />
                      <Route path="animation/timeline" element={errorWrapper(TimelineOverview)()} />
                      <Route path="animation/mails" element={errorWrapper(Mails)()} />
                      <Route path="animation/mails/:injectId" element={errorWrapper(MailsInject)()} />
                      <Route path="animation/logs" element={errorWrapper(Logs)()} />
                      <Route path="animation/chat" element={errorWrapper(Chat)()} />
                      <Route path="animation/validations" element={errorWrapper(Validations)()} />
                      <Route path="lessons" element={errorWrapper(Lessons)()} />
                      <Route path="findings" element={errorWrapper(SimulationFindings)()} />
                      {/* Simulation-scoped custom dashboard, reached from the hero "Analyze" quick action. */}
                      <Route path="dashboard" element={errorWrapper(SimulationDashboard)()} />
                      {/* Analysis is no longer a permanent tab; keep a redirect for old links. */}
                      <Route path="analysis" element={<Navigate to={`/admin/simulations/${exercise.exercise_id}/dashboard`} replace />} />
                      <Route path="scope" element={errorWrapper(SimulationScope)()} />
                      <Route path="logic" element={errorWrapper(SimulationLogic)()} />
                      {isAttackPathEnabled && <Route path="attack-path" element={errorWrapper(SimulationAttackPath)()} />}
                      {/* Not found */}
                      <Route path="*" element={<NotFound />} />
                    </Routes>
                  </Suspense>
                </>
              )}
        </div>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

const Index = () => {
  // Standard hooks
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchExercise(exerciseId)).finally(() => {
      setLoading(false);
    });
  }, [exerciseId]);

  // Fetch the scenario only once per simulation id: the exercise object gets a
  // new identity on every Redux update (e.g. SSE), which used to re-trigger
  // this effect and re-fetch the scenario redundantly.
  const scenarioRequestedForRef = useRef<ExerciseType['exercise_id'] | undefined>(undefined);
  useEffect(() => {
    if (!exercise) return;
    if (scenarioRequestedForRef.current === exercise.exercise_id) return;
    scenarioRequestedForRef.current = exercise.exercise_id;
    setLoading(true);
    if (!exercise.exercise_scenario) {
      setPristine(false);
      setLoading(false);
    } else {
      dispatch(fetchScenarioFromSimulation(exercise.exercise_id))
        .finally(() => {
          setPristine(false);
          setLoading(false);
        });
    }
  }, [exercise, dispatch]);

  const exerciseInjectContext = injectContextForExercise(exercise);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !exercise) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Simulation is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <InjectContext.Provider value={exerciseInjectContext}>
      <IndexComponent exercise={exercise} />
    </InjectContext.Provider>
  );
};

export default Index;
