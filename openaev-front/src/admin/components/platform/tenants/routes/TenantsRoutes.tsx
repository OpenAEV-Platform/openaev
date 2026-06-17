import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../../../components/Error';
import NoAccess from '../../../../../utils/permissions/NoAccess';
import ProtectedRoute from '../../../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../../utils/utils';

const TenantsIndex = lazy(() => import('./../TenantsIndex'));
const isMultiTenancyEnabled = isFeatureEnabled('MULTI_TENANCY');

export const TENANTS_PATH = 'tenants';

const TenantRoutes = (
  <Route
    path={`${TENANTS_PATH}/*`}
    element={(
      isMultiTenancyEnabled
        ? (
            <ProtectedRoute
              checks={[{
                action: ACTIONS.ACCESS,
                subject: SUBJECTS.TENANTS,
              }]}
              requireEE
              Component={errorWrapper(TenantsIndex)()}
            />
          )
        : <NoAccess />
    )}
  />
);

export default TenantRoutes;
