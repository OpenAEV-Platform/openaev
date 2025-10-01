import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';

const Injectors = lazy(() => import('./Injectors'));
const IndexInjector = lazy(() => import('./injectors/Index'));
const Collectors = lazy(() => import('./Collectors'));
const Executors = lazy(() => import('./Executors'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to="injectors" replace={true} />} />
          <Route path="injectors" element={errorWrapper(Injectors)()} />
          <Route path="injectors/:injectorId/*" element={errorWrapper(IndexInjector)()} />
          <Route path="collectors" element={errorWrapper(Collectors)()} />
          <Route path="executors" element={errorWrapper(Executors)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
