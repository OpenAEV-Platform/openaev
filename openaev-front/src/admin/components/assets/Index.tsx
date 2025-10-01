import { lazy, Suspense, useContext } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import { AbilityContext } from '../../../utils/permissions/PermissionsProvider';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const Endpoints = lazy(() => import('./endpoints/Endpoints'));
const IndexEndpoint = lazy(() => import('./endpoints/endpoint/Index'));
const AssetGroups = lazy(() => import('./asset_groups/AssetGroups'));
const SecurityPlatforms = lazy(() => import('./security_platforms/SecurityPlatforms'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  const ability = useContext(AbilityContext);

  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to={ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS) ? 'endpoints' : 'security_platforms'} replace={true} />} />
          <Route
            path="endpoints"
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
          <Route
            path="endpoints/:endpointId/*"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.ASSETS,
                }]}
                Component={errorWrapper(IndexEndpoint)()}
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
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
