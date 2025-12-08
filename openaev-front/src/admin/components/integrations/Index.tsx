import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import ConnectorDetails from './common/ConnectorDetails';
import ExecutorPage from './executors/ExecutorPage';
import InjectorPage from './injectors/InjectorPage';

const Catalog = lazy(() => import('./catalog_connectors/Catalog'));
const CatalogLayout = lazy(() => import('./catalog_connectors/CatalogLayout'));

const Injectors = lazy(() => import('./injectors/Injectors'));

const InjectorsLayout = lazy(() => import('./injectors/InjectorsLayout'));

const Executors = lazy(() => import('./executors/Executors'));
const ExecutorsLayout = lazy(() => import('./executors/ExecutorsLayout'));

const Collectors = lazy(() => import('./collectors/Collectors'));
const CollectorPage = lazy(() => import('./collectors/CollectorPage'));
const CollectorsLayout = lazy(() => import('./collectors/CollectorsLayout'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to="catalog" replace={true} />} />

          <Route path="catalog" element={errorWrapper(CatalogLayout)()}>
            <Route index element={<Catalog />} />
            <Route path=":catalogConnectorId" element={<ConnectorDetails />} />
          </Route>

          <Route path="injectors" element={errorWrapper(InjectorsLayout)()}>
            <Route index element={<Injectors />} />
            <Route path=":injectorId" element={<InjectorPage />} />
          </Route>

          <Route path="collectors" element={errorWrapper(CollectorsLayout)()}>
            <Route index element={<Collectors />} />
            <Route path=":collectorId" element={<CollectorPage />} />
          </Route>

          <Route path="executors" element={errorWrapper(ExecutorsLayout)()}>
            <Route index element={<Executors />} />
            <Route path=":executorId" element={<ExecutorPage />} />
          </Route>

          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
