import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

const Players = lazy(() => import('./Players'));
const Teams = lazy(() => import('./Teams'));
const Organizations = lazy(() => import('./Organizations'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to="players" replace={true} />} />
          <Route path="players" element={errorWrapper(Players)()} />
          <Route path="teams" element={errorWrapper(Teams)()} />
          <Route
            path="organizations"
            element={(
              <ProtectedRoute
                checks={[{
                  action: ACTIONS.ACCESS,
                  subject: SUBJECTS.PLATFORM_SETTINGS,
                }]}
                Component={errorWrapper(Organizations)()}
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
