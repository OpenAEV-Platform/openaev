import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';

const Reportings = lazy(() => import('./Reportings'));
const ReportingPage = lazy(() => import('./ReportingPage'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const ReportingIndex = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={errorWrapper(Reportings)()} />
          {/* Trailing wildcard: the active tab is a routed URL segment
              (/:reportingId[/generations|/schedules]), see useRoutedTabs. */}
          <Route path="/:reportingId/*" element={errorWrapper(ReportingPage)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};
export default ReportingIndex;
