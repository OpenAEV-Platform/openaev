import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../../../components/Error';
import ProtectedRoute from '../../../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';

const TenantsIndex = lazy(() => import('./../TenantsIndex'));

export const TENANTS_PATH = 'tenants';

const TenantRoutes = (
  <Route
    path={`${TENANTS_PATH}/*`}
    element={(
      <ProtectedRoute
        checks={[{
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.TENANTS,
        }]}
        requireEE
        Component={errorWrapper(TenantsIndex)()}
      />
    )}
  />
);

export default TenantRoutes;
