import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../../../components/Error';
import ProtectedRoute from '../../../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { USERS_CAPABILITIES_PATH } from '../users-capabilities-constants';

const UsersCapabilitiesIndex = lazy(() => import('./../UsersCapabilitiesIndex'));

const UsersCapabilitiesRoutes = (
  <Route
    path={`${USERS_CAPABILITIES_PATH}/*`}
    element={(
      <ProtectedRoute
        checks={[{
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.PLATFORM_GROUPS_AND_ROLES,
        }]}
        requireEE
        Component={errorWrapper(UsersCapabilitiesIndex)()}
      />
    )}
  />
);

export default UsersCapabilitiesRoutes;
