import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';

const Catalog = lazy(() => import('./catalog_connectors/Catalog'));
const CatalogLayout = lazy(() => import('./catalog_connectors/CatalogLayout'));
import ConnectorDetails from './catalog_connectors/ConnectorDetails';
const Injectors = lazy(() => import('./injectors/Injectors'));
const IndexInjector = lazy(() => import('./injectors/Index'));
const Executors = lazy(() => import('./Executors'));
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
            <Route path=":connectorId" element={<ConnectorDetails />} />
          </Route>

          <Route path="injectors" element={errorWrapper(Injectors)()} />
          <Route path="injectors/:injectorId/*" element={errorWrapper(IndexInjector)()} />

          <Route path="collectors" element={errorWrapper(CollectorsLayout)()}>
            <Route index element={<Collectors />} />
            <Route path=":collectorId" element={<CollectorPage />} />
          </Route>

          <Route path="executors" element={errorWrapper(Executors)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
