import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../components/Error';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const PlatformIndex = lazy(() => import('./PlatformIndex'));

const PlatformRoutes = (
  <Route
    path="platform/*"
    element={(
      <ProtectedRoute
        checks={[{
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.PLATFORM_SETTINGS,
        }, {
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.TENANTS,
        }, {
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.PLATFORM_GROUPS_AND_ROLES,
        }]}
        Component={errorWrapper(PlatformIndex)()}
      />
    )}
  />
);

export default PlatformRoutes;
