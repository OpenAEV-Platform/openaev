import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const Players = lazy(() => import('./Players'));
const PersonDetail = lazy(() => import('./persons/PersonDetail'));
const Teams = lazy(() => import('./Teams'));
const TeamDetail = lazy(() => import('./teams/TeamDetail'));
const Organizations = lazy(() => import('./OrganizationsList'));
const OrganizationDetail = lazy(() => import('./organizations/OrganizationDetail'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to="persons" replace={true} />} />
          <Route path="persons" element={errorWrapper(Players)()} />
          <Route path="persons/:userId" element={errorWrapper(PersonDetail)()} />
          {/* Back-compat alias for the previous Players route. */}
          <Route path="players" element={<Navigate to="../persons" replace={true} />} />
          <Route path="teams" element={errorWrapper(Teams)()} />
          <Route path="teams/:teamId" element={errorWrapper(TeamDetail)()} />
          <Route
            path="organizations"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.TENANT_SETTINGS,
                }]}
                Component={errorWrapper(Organizations)()}
              />
            )}
          />
          <Route
            path="organizations/:organizationId"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.TENANT_SETTINGS,
                }]}
                Component={errorWrapper(OrganizationDetail)()}
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
