import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { lazy, Suspense, useEffect } from 'react';
import { Route, Routes, useNavigate } from 'react-router';
import { type CSSObject } from 'tss-react';
import { makeStyles } from 'tss-react/mui';
import { useLocalStorage } from 'usehooks-ts';

import { fetchAttackPatterns } from '../actions/AttackPattern';
import { type LoggedHelper } from '../actions/helper';
import { fetchKillChainPhases } from '../actions/KillChainPhase';
import { fetchTags } from '../actions/Tag';
import { errorWrapper } from '../components/Error';
import Loader from '../components/Loader';
import NotFound from '../components/NotFound';
import { computeBannerSettings } from '../public/components/systembanners/utils';
import { useHelper } from '../store';
import { useAppDispatch } from '../utils/hooks';
import useDataLoader from '../utils/hooks/useDataLoader';
import ProtectedRoute from '../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../utils/permissions/types';
import { GETTING_STARTED_LOCAL_STORAGE_KEY } from './components/getting_started/GettingStartedPage';
import GettingStartedRoutes, { GETTING_STARTED_URI } from './components/getting_started/GettingStartedRoutes';
import LeftBar from './components/nav/LeftBar';
import TopBar from './components/nav/TopBar';
import DeployScenario from './components/scenarios/DeployScenario';
import InjectIndex from './components/simulations/simulation/injects/InjectIndex';

const Home = lazy(() => import('./components/Home'));
const IndexProfile = lazy(() => import('./components/profile/Index'));
const FullTextSearch = lazy(() => import('./components/search/FullTextSearch'));
const Findings = lazy(() => import('./components/findings/Findings'));
const Exercises = lazy(() => import('./components/simulations/Simulations'));
const IndexExercise = lazy(() => import('./components/simulations/simulation/Index'));
const AtomicTestings = lazy(() => import('./components/atomic_testings/AtomicTestings'));
const IndexAtomicTesting = lazy(() => import('./components/atomic_testings/atomic_testing/Index'));
const Scenarios = lazy(() => import('./components/scenarios/Scenarios'));
const IndexScenario = lazy(() => import('./components/scenarios/scenario/Index'));
const Assets = lazy(() => import('./components/assets/Index'));
const Teams = lazy(() => import('./components/teams/Index'));
const IndexComponents = lazy(() => import('./components/components/Index'));
const IndexIntegrations = lazy(() => import('./components/integrations/Index'));
const IndexAgents = lazy(() => import('./components/agents/Agents'));
const IndexCustomDashboard = lazy(() => import('./components/workspaces/custom_dashboards/Index'));
const Payloads = lazy(() => import('./components/payloads/Payloads'));
const IndexSettings = lazy(() => import('./components/settings/Index'));

const useStyles = makeStyles()(theme => ({ toolbar: theme.mixins.toolbar as CSSObject }));

const Index = () => {
  const theme = useTheme();

  const { classes } = useStyles();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { logged, settings } = useHelper((helper: LoggedHelper) => {
    return {
      logged: helper.logged(),
      settings: helper.getPlatformSettings(),
    };
  });

  useEffect(() => {
    if (logged.isOnlyPlayer) {
      navigate('/');
    }
  }, [logged]);

  const boxSx = {
    flexGrow: 1,
    padding: 3,
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.easeInOut,
      duration: theme.transitions.duration.enteringScreen,
    }),
    overflowX: 'hidden',
    overflowY: 'hidden',
  };
  // load taxonomies one time at login
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
    dispatch(fetchTags());
  });
  const { bannerHeight } = computeBannerSettings(settings);
  const [goToGettingStarted, setGoToGettingStarted] = useLocalStorage<boolean>(GETTING_STARTED_LOCAL_STORAGE_KEY, true);
  useEffect(() => {
    if (goToGettingStarted) {
      navigate('/admin/' + GETTING_STARTED_URI, { replace: true });
      setGoToGettingStarted(false);
    }
  }, [goToGettingStarted, navigate, setGoToGettingStarted]);

  return (
    <Box
      sx={{
        display: 'flex',
        minWidth: 1400,
        marginTop: bannerHeight,
        marginBottom: bannerHeight,
      }}
    >
      <TopBar />
      <LeftBar />
      <Box component="main" sx={boxSx}>
        <div className={classes.toolbar} />
        <Suspense fallback={<Loader />}>
          <Routes>
            <Route path="profile/*" element={errorWrapper(IndexProfile)()} />
            <Route path="" element={errorWrapper(Home)()} />
            <Route path="fulltextsearch" element={errorWrapper(FullTextSearch)()} />
            <Route
              path="findings"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.FINDINGS,
                  }]}
                  Component={errorWrapper(Findings)()}
                />
              )}
            />
            <Route path="simulations" element={errorWrapper(Exercises)()} />
            <Route
              path="simulations/:exerciseId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(IndexExercise)()}
                />
              )}
            />
            <Route
              path="simulations/:exerciseId/injects/:injectId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(InjectIndex)()}
                />
              )}
            />
            <Route path="atomic_testings" element={errorWrapper(AtomicTestings)()} />
            <Route
              path="atomic_testings/:injectId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'injectId',
                  }]}
                  Component={errorWrapper(IndexAtomicTesting)()}
                />
              )}
            />
            <Route path="scenarios" element={errorWrapper(Scenarios)()} />
            <Route path="deploy-scenario/:serviceInstanceId/:fileId" element={errorWrapper(DeployScenario)()} />
            <Route
              path="scenarios/:scenarioId/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'scenarioId',
                  }]}
                  Component={errorWrapper(IndexScenario)()}
                />
              )}
            />
            <Route path="assets/*" element={errorWrapper(Assets)()} />
            <Route path="teams/*" element={errorWrapper(Teams)()} />
            <Route path="components/*" element={errorWrapper(IndexComponents)()} />
            <Route
              path="workspaces/custom_dashboards/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.DASHBOARDS,
                  }]}
                  Component={errorWrapper(IndexCustomDashboard)()}
                />
              )}
            />
            <Route path="payloads" element={errorWrapper(Payloads)()} />
            <Route
              path="integrations/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(IndexIntegrations)()}
                />
              )}
            />
            <Route path="agents/*" element={errorWrapper(IndexAgents)()} />
            {GettingStartedRoutes}
            <Route
              path="settings/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.PLATFORM_SETTINGS,
                  }]}
                  Component={errorWrapper(IndexSettings)()}
                />
              )}
            />
            {/* Not found */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </Box>
    </Box>
  );
};

export default Index;
