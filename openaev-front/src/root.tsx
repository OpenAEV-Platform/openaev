import { CssBaseline } from '@mui/material';
import { StyledEngineProvider } from '@mui/material/styles';
import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { fetchMe, fetchPlatformParameters } from './actions/Application';
import {fetchUserTenants, switchUserTenant, type UserTenantOutput} from './actions/user/user-tenant-actions';
import { type LoggedHelper } from './actions/helper';
import EnterpriseEditionAgreementDialog from './admin/components/common/entreprise_edition/EnterpriseEditionAgreementDialog';
import ConnectedIntlProvider from './components/AppIntlProvider';
import ConnectedThemeProvider from './components/AppThemeProvider';
import EnterpriseEditionProvider from './components/EnterpriseEditionProvider';
import { errorWrapper } from './components/Error';
import Loader from './components/Loader';
import Message from './components/Message';
import NotFound from './components/NotFound';
import SystemBanners from './public/components/systembanners/SystemBanners';
import LicenseBanner from './public/components/trialbanners/LicenseBanner';
import StartTrialBanner from './public/components/trialbanners/StartTrialBanner';
import { useHelper } from './store';
import ErrorHandler from './utils/error/ErrorHandler';
import { useAppDispatch } from './utils/hooks';
import { UserContext } from './utils/hooks/useAuth';
import useNetworkCheck from './utils/hooks/useCheckNetwork';
import { PermissionsProvider } from './utils/permissions/PermissionsProvider';

const RootPublic = lazy(() => import('./public/Root'));
const IndexPrivate = lazy(() => import('./private/Index'));
const IndexAdmin = lazy(() => import('./admin/Index'));
const Comcheck = lazy(() => import('./public/components/comcheck/Comcheck'));
const Channel = lazy(() => import('./public/components/channels/Channel'));
const SimulationReport = lazy(() => import('./admin/components/simulations/simulation/reports/SimulationReportPage'));
const Challenges = lazy(() => import('./public/components/challenges/ChallengesPlayer'));
const ExerciseViewLessons = lazy(() => import('./public/components/lessons/ExerciseViewLessons'));
const ScenarioViewLessons = lazy(() => import('./public/components/lessons/ScenarioViewLessons'));
const SimulationChallengesPreview = lazy(() => import('./admin/components/simulations/simulation/challenges/SimulationChallengesPreview'));
const ScenarioChallengesPreview = lazy(() => import('./admin/components/scenarios/scenario/challenges/ScenarioChallengesPreview'));

const Root = () => {
  const { logged, me, settings } = useHelper((helper: LoggedHelper) => {
    return {
      logged: helper.logged(),
      me: helper.getMe(),
      settings: helper.getPlatformSettings(),
    };
  });
  const dispatch = useAppDispatch();

  // User tenant state
  const [userTenants, setUserTenants] = useState<UserTenantOutput[]>([]);
  const [currentUserTenant, setCurrentUserTenant] = useState<UserTenantOutput | null>(null);

  useEffect(() => {
    dispatch(fetchMe());
    dispatch(fetchPlatformParameters());
  }, []);

  // Load user tenants when user is logged in
  const loadUserTenants = useCallback(async () => {
    if (!me) return;

    try {
      const result = await fetchUserTenants();
      if (result && result.tenants) {
        setUserTenants(result.tenants);
        // TODO: at initial logging
        //  - either have a "preferred" tenant to land on (or do we persist last visited tenant)
        //  - land on the first tenant in the list (sorted by name for better UX) if no preferred tenant is set
        const current = result.tenants.find(t => t.tenant_is_current);
        setCurrentUserTenant(current || result.tenants[0] || null);
      }
    } catch (error) {
      console.error('Failed to load user tenants:', error);
    }
  }, [me]);

  useEffect(() => {
    if (me && logged) {
      loadUserTenants();
    }
  }, [me, logged, loadUserTenants]);

  const switchUserTenant = useCallback(async (tenantId: string) => {
    // If already on this tenant, just close
    if (tenantId === currentUserTenant?.tenant_id) {
      return;
    }

    // Reload page to refresh all data in new tenant context
    // Use setTimeout to ensure state updates complete before reload
    setTimeout(() => {
      const current = userTenants.find(t => (t.tenant_id === tenantId));
      if (current) {
        setCurrentUserTenant(current);
      }
      // TODO: tenant routing
      //window.location.replace(window.location.href);
    }, 0);

  }, [currentUserTenant]);

  const { isReachable } = useNetworkCheck(settings?.xtm_hub_url && `${settings?.xtm_hub_url}/health`);
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

  return (
    <PermissionsProvider capabilities={me.user_capabilities} grants={me.user_grants} isAdmin={me.user_admin}>
      <UserContext.Provider
        value={{
          me,
          settings,
          isXTMHubAccessible: isReachable,
          userTenants,
          currentUserTenant,
          switchUserTenant,
        }}
      >
        <StyledEngineProvider injectFirst>
          <ConnectedIntlProvider>
            <ConnectedThemeProvider>
              <EnterpriseEditionProvider>
                <CssBaseline />
                <Message />
                <ErrorHandler />
                <EnterpriseEditionAgreementDialog />
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
                    <Route path="admin/*" element={errorWrapper(IndexAdmin)()} />
                    {/* Routes from /public/Index that need to be accessible for logged user are duplicated here */}
                    <Route path="comcheck/:statusId" element={errorWrapper(Comcheck)()} />
                    <Route path="channels/:exerciseId/:channelId" element={errorWrapper(Channel)()} />
                    <Route path="challenges/:exerciseId" element={errorWrapper(Challenges)()} />
                    <Route path="lessons/simulation/:exerciseId" element={errorWrapper(ExerciseViewLessons)()} />
                    <Route path="lessons/scenario/:scenarioId" element={errorWrapper(ScenarioViewLessons)()} />
                    <Route path="reports/:reportId/exercise/:exerciseId" element={errorWrapper(SimulationReport)()} />

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
