import { lazy, Suspense, useContext } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const Endpoints = lazy(() => import('./endpoints/Endpoints'));
const AssetDetail = lazy(() => import('./asset/AssetDetail'));
const AssetGroups = lazy(() => import('./asset_groups/AssetGroups'));
const AssetGroupDetail = lazy(() => import('./asset_groups/AssetGroupDetail'));
const SecurityPlatforms = lazy(() => import('./security_platforms/SecurityPlatforms'));
const SecurityPlatformDetail = lazy(() => import('./security_platforms/SecurityPlatformDetail'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  const ability = useContext(AbilityContext);

  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to={ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS) ? 'inventory' : 'security_platforms'} replace={true} />} />
          <Route
            path="inventory"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(Endpoints)()}
              />
            )}
          />
          {/* Back-compat aliases for the previous Endpoints / AI targets list routes. */}
          <Route path="endpoints" element={<Navigate to="../inventory" replace={true} />} />
          <Route path="ai_targets" element={<Navigate to="../inventory" replace={true} />} />
          {/* Generic asset detail page for every asset type. The legacy endpoints/:endpointId path
              renders the same page (id resolved from either param) so existing deep links keep working. */}
          <Route
            path="details/:assetId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(AssetDetail)()}
              />
            )}
          />
          <Route
            path="endpoints/:endpointId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(AssetDetail)()}
              />
            )}
          />
          <Route
            path="asset_groups"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(AssetGroups)()}
              />
            )}
          />
          <Route
            path="asset_groups/:assetGroupId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(AssetGroupDetail)()}
              />
            )}
          />
          <Route
            path="security_platforms"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.SECURITY_PLATFORMS,
                }]}
                Component={errorWrapper(SecurityPlatforms)()}
              />
            )}
          />
          <Route
            path="security_platforms/:securityPlatformId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.SECURITY_PLATFORMS,
                }]}
                Component={errorWrapper(SecurityPlatformDetail)()}
              />
            )}
          />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
