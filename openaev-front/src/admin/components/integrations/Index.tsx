import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import Catalog from './catalog_connectors/Catalog';
import ConnectorDetails from './catalog_connectors/ConnectorDetails';

const CatalogLayout = lazy(() => import('./catalog_connectors/CatalogLayout'));
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
          <Route path="" element={<Navigate to="catalog" replace={true} />} />

          <Route path="catalog" element={errorWrapper(CatalogLayout)()}>
            <Route index element={<Catalog />} />
            <Route path=":connectorId" element={<ConnectorDetails />} />
          </Route>

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
