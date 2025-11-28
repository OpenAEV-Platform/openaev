import { Alert, AlertTitle, Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, lazy, Suspense, useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchExercise } from '../../../../actions/Exercise';
import { fetchScenarioFromSimulation } from '../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type Exercise as ExerciseType } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import injectContextForExercise from './ExerciseContext';
import ExerciseDatePopover from './ExerciseDatePopover';
import ExerciseHeader from './ExerciseHeader';

const Simulation = lazy(() => import('./overview/SimulationComponent'));
const Lessons = lazy(() => import('./lessons/SimulationLessons'));
const SimulationFindings = lazy(() => import('./findings/SimulationFindings'));
const SimulationAnalysis = lazy(() => import('./analysis/SimulationAnalysis'));
const SimulationDefinition = lazy(() => import('./SimulationDefinition'));
const Injects = lazy(() => import('./injects/ExerciseInjects'));
const Tests = lazy(() => import('./tests/ExerciseTests'));
const TimelineOverview = lazy(() => import('./timeline/TimelineOverview'));
const Mails = lazy(() => import('./mails/Mails'));
const MailsInject = lazy(() => import('./mails/Inject'));
const Logs = lazy(() => import('./logs/Logs'));
const Chat = lazy(() => import('./chat/Chat'));
const Validations = lazy(() => import('./validation/Validations'));

const useStyles = makeStyles()(() => ({
  scheduling: {
    display: 'flex',
    margin: '-35px 8px 0 0',
    float: 'right',
    alignItems: 'center',
  },
}));

const IndexComponent: FunctionComponent<{ exercise: ExerciseType }> = ({ exercise }) => {
  const { t, fldt } = useFormatter();
  const location = useLocation();
  const { classes } = useStyles();
  const permissionsContext: PermissionsContextType = {
    permissions: useSimulationPermissions(exercise.exercise_id, exercise),
    inherited_context: INHERITED_CONTEXT.SIMULATION,
  };
  const documentContext: DocumentContextType = {
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
  };

  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/definition`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/definition`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/animation`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/animation`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/results`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/results`;
  } else if (location.pathname.includes(`/admin/simulations/${exercise.exercise_id}/tests`)) {
    tabValue = `/admin/simulations/${exercise.exercise_id}/tests`;
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
          <ExerciseHeader />
          <Box
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              marginBottom: 2,
            }}
          >
            <Tabs value={tabValue}>
              <Tab
                component={Link}
                to={`/admin/simulations/${exercise.exercise_id}`}
                value={`/admin/simulations/${exercise.exercise_id}`}
                label={t('Overview')}
              />
              <Tab
                component={Link}
                to={`/admin/simulations/${exercise.exercise_id}/definition`}
                value={`/admin/simulations/${exercise.exercise_id}/definition`}
                label={t('Definition')}
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
              <Tab
                component={Link}
                to={`/admin/simulations/${exercise.exercise_id}/analysis`}
                value={`/admin/simulations/${exercise.exercise_id}/analysis`}
                label={t('Analysis')}
              />
            </Tabs>
            {permissionsContext.permissions.canManage && (
              <div className={classes.scheduling}>
                <ExerciseDatePopover exercise={exercise} />
                {exercise.exercise_start_date ? fldt(exercise.exercise_start_date) : t('Manual')}
              </div>
            )}
          </Box>
          <Suspense fallback={<Loader />}>
            <Routes>
              <Route path="" element={errorWrapper(Simulation)()} />
              <Route path="definition" element={errorWrapper(SimulationDefinition)()} />
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
              <Route path="analysis" element={errorWrapper(SimulationAnalysis)()} />
              {/* Not found */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
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

  useEffect(() => {
    if (!exercise) return;
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
  }, [exercise]);

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
