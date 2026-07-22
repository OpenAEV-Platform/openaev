import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router';
import { type CSSObject } from 'tss-react';
import { makeStyles } from 'tss-react/mui';
import { useLocalStorage } from 'usehooks-ts';

import { fetchAttackPatterns } from '../actions/AttackPattern';
import { fetchDomains } from '../actions/domains/domain-actions';
import { type LoggedHelper } from '../actions/helper';
import { fetchKillChainPhases } from '../actions/KillChainPhase';
import { fetchTags } from '../actions/tags/tag-action';
import { errorWrapper } from '../components/Error';
import Loader from '../components/Loader';
import NotFound from '../components/NotFound';
import { computeBannerSettings } from '../public/components/systembanners/utils';
import { useHelper } from '../store';
import { useAppDispatch } from '../utils/hooks';
import useAuth from '../utils/hooks/useAuth';
import useDataLoader from '../utils/hooks/useDataLoader';
import ProtectedRoute from '../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../utils/permissions/types';
import ChatbotProvider from './components/ariane/ChatbotProvider';
import { useChatbotContentMargin, useChatbotContentTransition } from './components/ariane/useChatbotHooks';
import { GETTING_STARTED_LOCAL_STORAGE_KEY } from './components/getting_started/GettingStartedPage';
import GettingStartedRoutes, { GETTING_STARTED_URI } from './components/getting_started/GettingStartedRoutes';
import { SETTINGS_ACCESS_CHECKS } from './components/nav/config/settings.config';
import LeftBar from './components/nav/LeftBar';
import TopBar from './components/nav/TopBar';
import DeployScenario from './components/scenarios/DeployScenario';

const Home = lazy(() => import('./components/Home'));
const DefaultHomeResults = lazy(() => import('./components/default_dashboard/DefaultHomeResults'));
// Lazy like every other route: keeps the inject detail tree (incl. charts) out of the main admin chunk
const InjectIndex = lazy(() => import('./components/simulations/simulation/injects/InjectIndex'));
const IndexProfile = lazy(() => import('./components/profile/Index'));
const FullTextSearch = lazy(() => import('./components/search/FullTextSearch'));
const Findings = lazy(() => import('./components/findings/Findings'));
const FindingOverview = lazy(() => import('./components/findings/FindingOverview'));
const Exercises = lazy(() => import('./components/simulations/Simulations'));
const IndexExercise = lazy(() => import('./components/simulations/simulation/Index'));
const SimulationInjectCreation = lazy(() => import('./components/simulations/simulation/injects/SimulationInjectCreationRoute'));
const AtomicTestings = lazy(() => import('./components/atomic_testings/AtomicTestings'));
const AtomicTestingCreation = lazy(() => import('./components/atomic_testings/AtomicTestingCreation'));
const IndexAtomicTesting = lazy(() => import('./components/atomic_testings/atomic_testing/Index'));
const Scenarios = lazy(() => import('./components/scenarios/Scenarios'));
const IndexScenario = lazy(() => import('./components/scenarios/scenario/Index'));
const Assets = lazy(() => import('./components/assets/Index'));
const Persons = lazy(() => import('./components/teams/Players'));
const PersonDetail = lazy(() => import('./components/teams/persons/PersonDetail'));
const TeamsList = lazy(() => import('./components/teams/Teams'));
const TeamDetail = lazy(() => import('./components/teams/teams/TeamDetail'));
const OrganizationsList = lazy(() => import('./components/teams/OrganizationsList'));
const OrganizationDetail = lazy(() => import('./components/teams/organizations/OrganizationDetail'));
const IndexComponents = lazy(() => import('./components/components/Index'));
const IndexIntegrations = lazy(() => import('./components/integrations/Index'));
const IndexAgents = lazy(() => import('./components/agents/Agents'));
const IndexCustomDashboard = lazy(() => import('./components/workspaces/custom_dashboards/Index'));
const IndexSettings = lazy(() => import('./components/settings/Index'));
const ThreatArsenal = lazy(() => import('./components/threat_arsenal/ThreatArsenal'));

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

  const chatbotMargin = useChatbotContentMargin();
  const chatbotTransition = useChatbotContentTransition(theme);

  const { currentUserTenant } = useAuth();

  const boxSx = {
    flexGrow: 1,
    paddingTop: 2,
    paddingLeft: 2.5,
    paddingRight: 2.5,
    // Global bottom breathing room: without it every page's last row sits flush
    // against the viewport edge and feels "cut off". Set once here for the whole app.
    paddingBottom: 3,
    marginRight: chatbotMargin > 0 ? `${chatbotMargin}px` : 0,
    transition: chatbotTransition,
    overflowX: 'hidden',
    overflowY: 'hidden',
  };
  // load taxonomies at login and reload tenant-scoped data on tenant switch
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
    dispatch(fetchTags());
    dispatch(fetchDomains());
  }, [currentUserTenant?.tenant_id]);
  const { bannerHeight, bannerHeightNumber } = computeBannerSettings(settings);
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
        // Lock the shell to the viewport (minus any system banners) so <main> matches the
        // viewport height instead of the sidebar's content height. Without this the app is only
        // as tall as the left menu, which leaves full-height pages (e.g. the dashboard results
        // page) either short with a gap or overflowing into a body scrollbar depending on the
        // viewport. minHeight (not height) still lets genuinely long pages grow and body-scroll.
        minHeight: `calc(100dvh - ${2 * bannerHeightNumber}px)`,
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
            <Route path="results" element={errorWrapper(DefaultHomeResults)()} />
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
            <Route
              path="findings/:findingId"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.FINDINGS,
                  }]}
                  Component={errorWrapper(FindingOverview)()}
                />
              )}
            />
            <Route path="simulations" element={errorWrapper(Exercises)()} />
            {/* Inject creation is a full-page flow and MUST be declared before the
                inject-detail route below: `injects/:injectId/*` would otherwise
                capture `injects/create` (injectId="create") and mount the detail
                view, which loads forever. The static `create` segment ranks these
                two routes above both `injects/:injectId/*` and `:exerciseId/*`. */}
            <Route
              path="simulations/:exerciseId/injects/create"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.MANAGE,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(SimulationInjectCreation)()}
                />
              )}
            />
            <Route
              path="simulations/:exerciseId/injects/create/:contractId"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.MANAGE,
                    subject: SUBJECTS.ASSESSMENT,
                  }, {
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.RESOURCE,
                    resourceURIParamName: 'exerciseId',
                  }]}
                  Component={errorWrapper(SimulationInjectCreation)()}
                />
              )}
            />
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
            {/* Creation requires the same Manage Assessment capability as the create button. */}
            <Route
              path="atomic_testings/create"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.MANAGE,
                    subject: SUBJECTS.ASSESSMENT,
                  }]}
                  Component={errorWrapper(AtomicTestingCreation)()}
                />
              )}
            />
            <Route
              path="atomic_testings/create/:contractId"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.MANAGE,
                    subject: SUBJECTS.ASSESSMENT,
                  }]}
                  Component={errorWrapper(AtomicTestingCreation)()}
                />
              )}
            />
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
            {/* Persons / Teams / Organizations are top-level sections (no shared
                "teams" parent segment). Static back-compat aliases below rank
                above the dynamic ":teamId" route in React Router. */}
            <Route path="persons" element={errorWrapper(Persons)()} />
            <Route path="persons/:userId" element={errorWrapper(PersonDetail)()} />
            <Route path="teams" element={errorWrapper(TeamsList)()} />
            <Route path="teams/persons" element={<Navigate to="/admin/persons" replace={true} />} />
            <Route path="teams/players" element={<Navigate to="/admin/persons" replace={true} />} />
            <Route path="teams/teams" element={<Navigate to="/admin/teams" replace={true} />} />
            <Route path="teams/organizations" element={<Navigate to="/admin/organizations" replace={true} />} />
            <Route path="teams/:teamId" element={errorWrapper(TeamDetail)()} />
            <Route
              path="organizations"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.TENANT_SETTINGS,
                  }]}
                  Component={errorWrapper(OrganizationsList)()}
                />
              )}
            />
            <Route
              path="organizations/:organizationId"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.TENANT_SETTINGS,
                  }]}
                  Component={errorWrapper(OrganizationDetail)()}
                />
              )}
            />
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
            <Route
              path="threat-arsenal"
              element={errorWrapper(ThreatArsenal)()}
            />
            <Route
              path="integrations/*"
              element={(
                <ProtectedRoute
                  checks={[{
                    action: ACTIONS.ACCESS,
                    subject: SUBJECTS.TENANT_SETTINGS,
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
                  checks={SETTINGS_ACCESS_CHECKS}
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

const IndexWithChatbot = () => (
  <ChatbotProvider>
    <Index />
  </ChatbotProvider>
);

export default IndexWithChatbot;
