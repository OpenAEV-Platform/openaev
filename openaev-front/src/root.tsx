import { CssBaseline } from '@mui/material';
import { StyledEngineProvider } from '@mui/material/styles';
import { lazy, Suspense, useEffect, useMemo } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { fetchMe, fetchPlatformParameters } from './actions/Application';
import { type LoggedHelper } from './actions/helper';
import fetchPublicPlatformParameters from './actions/settings/platform-settings-action';
import { fetchTenantSettings } from './actions/settings/tenant-settings-action';
import EnterpriseEditionAgreementDialog from './admin/components/common/entreprise_edition/EnterpriseEditionAgreementDialog';
import ConnectedIntlProvider from './components/AppIntlProvider';
import ConnectedThemeProvider from './components/AppThemeProvider';
import EnterpriseEditionProvider from './components/EnterpriseEditionProvider';
import { errorWrapper } from './components/Error';
import Loader from './components/Loader';
import Message from './components/Message';
import NoTenantAlert from './components/NoTenantAlert';
import NotFound from './components/NotFound';
import TimeoutLock from './components/TimeoutLock';
import SystemBanners from './public/components/systembanners/SystemBanners';
import LicenseBanner from './public/components/trialbanners/LicenseBanner';
import StartTrialBanner from './public/components/trialbanners/StartTrialBanner';
import { useHelper } from './store';
import ErrorHandler from './utils/error/ErrorHandler';
import { useAppDispatch } from './utils/hooks';
import { UserContext } from './utils/hooks/useAuth';
import useNetworkCheck from './utils/hooks/useCheckNetwork';
import useTenant from './utils/hooks/useTenant';
import PermissionsProvider from './utils/permissions/PermissionsProvider';
import { buildTenantUrl, DEFAULT_TENANT_UUID, extractTenantFromUrl } from './utils/url-helper';

const RootPublic = lazy(() => import('./public/Root'));
const XtmHubRedirect = lazy(() => import('./admin/components/xtm_hub/XtmHubRedirect'));
const IndexPrivate = lazy(() => import('./private/Index'));
const IndexAdmin = lazy(() => import('./admin/Index'));
const Comcheck = lazy(() => import('./public/components/comcheck/Comcheck'));
const Channel = lazy(() => import('./public/components/channels/Channel'));
const Challenges = lazy(() => import('./public/components/challenges/ChallengesPlayer'));
const ExerciseViewLessons = lazy(() => import('./public/components/lessons/ExerciseViewLessons'));
const ScenarioViewLessons = lazy(() => import('./public/components/lessons/ScenarioViewLessons'));
const UrlAccess = lazy(() => import('./public/components/url_access/UrlAccess'));
const PhishingPage = lazy(() => import('./public/components/phishing/PhishingPage'));
const SimulationChallengesPreview = lazy(() => import('./admin/components/simulations/simulation/challenges/SimulationChallengesPreview'));
const ScenarioChallengesPreview = lazy(() => import('./admin/components/scenarios/scenario/challenges/ScenarioChallengesPreview'));
const ReportingRender = lazy(() => import('./admin/components/reporting/render/ReportingRender'));

const Root = () => {
  const { logged, me, settings } = useHelper((helper: LoggedHelper) => {
    return {
      logged: helper.logged(),
      me: helper.getMe(),
      settings: helper.getPlatformSettings(),
    };
  });
  const dispatch = useAppDispatch();

  const { userTenants, currentUserTenant, switchUserTenant, reloadUserTenants } = useTenant(me, logged);

  useEffect(() => {
    dispatch(fetchPublicPlatformParameters());
    dispatch(fetchMe());
  }, []);

  // Fetch full settings once authenticated, re-fetch public settings on logout
  useEffect(() => {
    if (logged && me) {
      dispatch(fetchPlatformParameters());
      dispatch(fetchTenantSettings());
    } else if (logged === null) {
      dispatch(fetchPublicPlatformParameters());
    }
  }, [logged, me]);

  const { isReachable } = useNetworkCheck(settings?.xtm_hub_url && `${settings?.xtm_hub_url}/health`);

  // Stable context identity: without this, every Root render produced a new object and
  // re-rendered every useAuth() consumer in the app.
  const userContextValue = useMemo(() => ({
    me,
    settings,
    isXTMHubAccessible: isReachable,
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants,
  }), [me, settings, isReachable, userTenants, currentUserTenant, switchUserTenant, reloadUserTenants]);

  if (logged && typeof logged === 'object' && Object.keys(logged).length === 0) {
    return <div />;
  }

  if (!logged || !me || !settings || isReachable === undefined) {
    return (
      <Suspense fallback={<Loader />}>
        <RootPublic />
      </Suspense>
    );
  }

  // When the user is authenticated but has no tenant assigned,
  // show a blocking message asking them to contact their administrator.
  const hasNoTenant = userTenants !== undefined && userTenants.length === 0 && !me.user_admin;
  if (hasNoTenant) {
    return (
      <StyledEngineProvider injectFirst>
        <ConnectedIntlProvider>
          <ConnectedThemeProvider>
            <CssBaseline />
            <NoTenantAlert />
          </ConnectedThemeProvider>
        </ConnectedIntlProvider>
      </StyledEngineProvider>
    );
  }

  // When the user is authenticated but the URL has no tenant prefix
  // (e.g. first visit at "/", or right after login), hard-redirect to
  // the tenant-prefixed URL so BrowserRouter picks up the correct basename.
  if (!extractTenantFromUrl()) {
    // Wait for fetchUserTenants (useTenant) before deciding which tenant to use.
    if (userTenants === undefined) {
      return <Loader />;
    }

    const tenantId = currentUserTenant?.tenant_id
      ?? userTenants[0]?.tenant_id
      ?? DEFAULT_TENANT_UUID;
    window.location.href = buildTenantUrl(tenantId);
    return <Loader />;
  }

  return (
    <PermissionsProvider capabilities={me.user_capabilities} grants={me.user_grants} isAdmin={me.user_admin}>
      <UserContext.Provider value={userContextValue}>
        <StyledEngineProvider injectFirst>
          <ConnectedIntlProvider>
            <ConnectedThemeProvider>
              <EnterpriseEditionProvider>
                <CssBaseline />
                <Message />
                <ErrorHandler />
                <EnterpriseEditionAgreementDialog />
                {(settings.platform_session_idle_timeout ?? 0) > 0 && <TimeoutLock />}
                <SystemBanners settings={settings} />
                <LicenseBanner settings={settings} />
                <StartTrialBanner settings={settings} />
                <Suspense fallback={<Loader />}>
                  <Routes>
                    <Route
                      path=""
                      element={logged.isOnlyPlayer ? <Navigate to="private" replace={true} />
                        : <Navigate to="admin" replace={true} />}
                    />
                    <Route path="private/*" element={errorWrapper(IndexPrivate)()} />
                    {/* Add challenge preview routes here to ensure they are rendered without the top & left bar */}
                    <Route path="admin/simulations/:exerciseId/challenges" element={errorWrapper(SimulationChallengesPreview)()} />
                    <Route path="admin/scenarios/:scenarioId/challenges" element={errorWrapper(ScenarioChallengesPreview)()} />
                    {/* Standalone print-ready report render (headless PDF capture + in-app preview) */}
                    <Route path="reporting/:reportingId/render" element={errorWrapper(ReportingRender)()} />
                    <Route path="redirect/*" element={errorWrapper(XtmHubRedirect)()} />
                    <Route path="admin/*" element={errorWrapper(IndexAdmin)()} />
                    {/* Routes from /public/Index that need to be accessible for logged user are duplicated here */}
                    <Route path="comcheck/:statusId" element={errorWrapper(Comcheck)()} />
                    <Route path="channels/:exerciseId/:channelId" element={errorWrapper(Channel)()} />
                    <Route path="challenges/:exerciseId" element={errorWrapper(Challenges)()} />
                    <Route path="lessons/simulation/:exerciseId" element={errorWrapper(ExerciseViewLessons)()} />
                    <Route path="lessons/scenario/:scenarioId" element={errorWrapper(ScenarioViewLessons)()} />
                    <Route path="url/access" element={errorWrapper(UrlAccess)()} />
                    <Route path="phishing/:tenantId/:token" element={errorWrapper(PhishingPage)()} />
                    {/* Not found */}
                    <Route path="*" element={<NotFound />} />
                  </Routes>
                </Suspense>
              </EnterpriseEditionProvider>
            </ConnectedThemeProvider>
          </ConnectedIntlProvider>
        </StyledEngineProvider>
      </UserContext.Provider>
    </PermissionsProvider>

  );
};

export default Root;
