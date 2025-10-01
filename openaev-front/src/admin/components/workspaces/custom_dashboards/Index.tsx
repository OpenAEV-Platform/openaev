import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../../components/Error';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';

const CustomDashboards = lazy(() => import('./CustomDashboards'));
const CustomDashboard = lazy(() => import('./CustomDashboard'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const CustomDashboardIndex = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={errorWrapper(CustomDashboards)()} />
          <Route path="/:customDashboardId" element={errorWrapper(CustomDashboard)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};
export default CustomDashboardIndex;
