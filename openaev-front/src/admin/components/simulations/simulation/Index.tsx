import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useParams } from 'react-router';

import { fetchExercise } from '../../../../actions/Exercise';
import { fetchScenarioFromSimulation } from '../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
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
import SimulationShell from './SimulationShell';

const Simulation = lazy(() => import('./overview/SimulationComponent'));
const SimulationStatistics = lazy(() => import('./analysis/SimulationAnalysis'));
const Lessons = lazy(() => import('./lessons/SimulationLessons'));
const SimulationFindings = lazy(() => import('./findings/SimulationFindings'));
const Injects = lazy(() => import('./injects/ExerciseInjects'));
const Tests = lazy(() => import('./tests/ExerciseTests'));
const ExecutionOverview = lazy(() => import('./timeline/ExecutionOverview'));
const Mails = lazy(() => import('./mails/Mails'));
const MailsInject = lazy(() => import('./mails/Inject'));
const Logs = lazy(() => import('./logs/Logs'));
const Chat = lazy(() => import('./chat/Chat'));
const Validations = lazy(() => import('./validation/Validations'));
const SimulationScope = lazy(() => import('./scope/SimulationScope'));
const SimulationLogic = lazy(() => import('./logic/SimulationLogic'));
const SimulationAttackPath = lazy(() => import('./attack_path/SimulationAttackPath'));

// The Animation area was renamed Execution: rewrite any legacy /animation/*
// deep link to its /execution/* equivalent, preserving the sub-path.
const AnimationToExecutionRedirect = () => {
  const location = useLocation();
  return <Navigate to={location.pathname.replace('/animation', '/execution')} replace />;
};

const IndexComponent: FunctionComponent<{ exercise: SimulationDetails }> = ({ exercise }) => {
  const location = useLocation();
  const permissions = useSimulationPermissions(exercise.exercise_id, exercise);
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH');
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

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <div style={{ paddingRight: location.pathname.includes('/execution') ? 200 : 0 }}>
          <SimulationShell exercise={exercise}>
            <Suspense fallback={<Loader />}>
              <Routes>
                <Route path="" element={errorWrapper(Simulation)()} />
                {/* Definition merged into the Injects authoring tab; redirect old links. */}
                <Route path="definition" element={<Navigate to={`/admin/simulations/${exercise.exercise_id}/injects`} replace={true} />} />
                <Route path="injects" element={errorWrapper(Injects)()} />
                <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
                <Route path="execution" element={<Navigate to="timeline" replace={true} />} />
                <Route path="execution/timeline" element={errorWrapper(ExecutionOverview)()} />
                <Route path="execution/mails" element={errorWrapper(Mails)()} />
                <Route path="execution/mails/:injectId" element={errorWrapper(MailsInject)()} />
                <Route path="execution/logs" element={errorWrapper(Logs)()} />
                <Route path="execution/chat" element={errorWrapper(Chat)()} />
                <Route path="execution/validations" element={errorWrapper(Validations)()} />
                {/* The Animation area was renamed Execution; keep old deep links working. */}
                <Route path="animation/*" element={<AnimationToExecutionRedirect />} />
                <Route path="lessons" element={errorWrapper(Lessons)()} />
                <Route path="findings" element={errorWrapper(SimulationFindings)()} />
                {isAttackPathEnabled && <Route path="attack-path" element={errorWrapper(SimulationAttackPath)()} />}
                {/* Simulation-scoped custom dashboard, surfaced as the Statistics tab. */}
                <Route path="statistics" element={errorWrapper(SimulationStatistics)()} />
                {/* Statistics replaced the hero dashboard quick action and the old
                    Analysis tab; keep redirects for old links. */}
                <Route path="dashboard" element={<Navigate to={`/admin/simulations/${exercise.exercise_id}/statistics`} replace />} />
                <Route path="analysis" element={<Navigate to={`/admin/simulations/${exercise.exercise_id}/statistics`} replace />} />
                <Route path="scope" element={errorWrapper(SimulationScope)()} />
                <Route path="logic" element={errorWrapper(SimulationLogic)()} />
                {/* Not found */}
                <Route path="*" element={<NotFound />} />
              </Routes>
            </Suspense>
          </SimulationShell>
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
