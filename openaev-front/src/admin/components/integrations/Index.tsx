import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import ConnectorDetails from './common/ConnectorDetails';
import InjectorPage from './injectors/InjectorPage';

const Catalog = lazy(() => import('./catalog_connectors/Catalog'));
const CatalogLayout = lazy(() => import('./catalog_connectors/CatalogLayout'));

const InjectorsLayout = lazy(() => import('./injectors/InjectorsLayout'));
const ExecutorsLayout = lazy(() => import('./executors/ExecutorsLayout'));
const CollectorsLayout = lazy(() => import('./collectors/CollectorsLayout'));
const ConnectorList = lazy(() => import('./common/ConnectorList'));
const ConnectorPage = lazy(() => import('./common/ConnectorPage'));

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
            <Route index element={<ConnectorList />} />
            <Route path=":injectorId" element={<InjectorPage />} />
          </Route>

          <Route path="collectors" element={errorWrapper(CollectorsLayout)()}>
            <Route index element={<ConnectorList />} />
            <Route path=":collectorId" element={<ConnectorPage />} />
          </Route>

          <Route path="executors" element={errorWrapper(ExecutorsLayout)()}>
            <Route index element={<ConnectorList />} />
            <Route path=":executorId" element={<ConnectorPage />} />
          </Route>

          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
