import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { errorWrapper } from '../../../components/Error';
import Loader from '../../../components/Loader';
import NotFound from '../../../components/NotFound';
import ConnectorDetails from './common/ConnectorDetails';
import InjectorPage from './injectors/InjectorPage';

const Integrations = lazy(() => import('./Integrations'));
const CatalogLayout = lazy(() => import('./catalog_connectors/CatalogLayout'));

const InjectorsLayout = lazy(() => import('./injectors/InjectorsLayout'));
const ExecutorsLayout = lazy(() => import('./executors/ExecutorsLayout'));
const SecretsProviderLayout = lazy(() => import('./secrets_providers/SecretsProvidersLayout'));
const CollectorsLayout = lazy(() => import('./collectors/CollectorsLayout'));
const ConnectorPage = lazy(() => import('./common/ConnectorPage'));

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const Index = () => {
  const { classes } = useStyles();
  return (
    <div className={classes.root}>
      <Suspense fallback={<Loader />}>
        <Routes>
          <Route path="" element={<Navigate to="deployed" replace={true} />} />

          {/* Detail pages keep their historical URLs; the old list URLs
              redirect to the corresponding tab of the merged page. */}
          <Route path="catalog" element={errorWrapper(CatalogLayout)()}>
            <Route index element={<Navigate to="../available" replace={true} />} />
            <Route path=":catalogConnectorId" element={<ConnectorDetails />} />
          </Route>

          <Route path="injectors" element={errorWrapper(InjectorsLayout)()}>
            <Route index element={<Navigate to="../deployed" replace={true} />} />
            <Route path=":injectorId" element={<InjectorPage />} />
          </Route>

          <Route path="collectors" element={errorWrapper(CollectorsLayout)()}>
            <Route index element={<Navigate to="../deployed" replace={true} />} />
            <Route path=":collectorId" element={<ConnectorPage />} />
          </Route>

          <Route path="executors" element={errorWrapper(ExecutorsLayout)()}>
            <Route index element={<Navigate to="../deployed" replace={true} />} />
            <Route path=":executorId" element={<ConnectorPage />} />
          </Route>

          <Route path="secrets-providers" element={errorWrapper(SecretsProviderLayout)()}>
            <Route index element={<Navigate to="../deployed" replace={true} />} />
            <Route path=":secrets_providerId" element={<ConnectorPage />} />
          </Route>

          {/* deployed / available tabs of the merged integrations page */}
          <Route path=":tab" element={errorWrapper(Integrations)()} />

          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </div>
  );
};

export default Index;
