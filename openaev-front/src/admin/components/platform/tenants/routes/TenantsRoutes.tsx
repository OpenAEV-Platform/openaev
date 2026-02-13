import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../../../components/Error';
import ProtectedRoute from '../../../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';

const TenantsIndex = lazy(() => import('./../TenantsIndex'));

const TenantRoutes = (
  <Route
    path="tenants/*"
    element={(
      <ProtectedRoute
        checks={[{
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.PLATFORM_SETTINGS,
        }]}
        Component={errorWrapper(TenantsIndex)()}
      />
    )}
  />
);

export default TenantRoutes;
