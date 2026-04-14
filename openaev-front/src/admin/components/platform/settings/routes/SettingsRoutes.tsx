import { lazy } from 'react';
import { Route } from 'react-router';

import { errorWrapper } from '../../../../../components/Error';
import ProtectedRoute from '../../../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';

const Parameters = lazy(() => import('../Parameters'));

export const SETTINGS_PATH = 'settings';

const SettingsRoutes = (
  <Route
    path={SETTINGS_PATH}
    element={(
      <ProtectedRoute
        checks={[{
          action: ACTIONS.ACCESS,
          subject: SUBJECTS.PLATFORM_SETTINGS,
        }]}
        Component={errorWrapper(Parameters)()}
      />
    )}
  />
);

export default SettingsRoutes;
