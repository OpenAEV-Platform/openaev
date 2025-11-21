import { lazy, useEffect } from 'react';
import { Route, Routes, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchConnector } from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type CatalogConnector } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

const useStyles = makeStyles()(() => ({ root: { flexGrow: 1 } }));

const ConnectorDetails = lazy(() => import('./ConnectorDetails'));

const Index = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { connectorId } = useParams() as { connectorId: CatalogConnector['connector_id'] };

  const { connector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(connectorId) }));

  useDataLoader(() => {
    dispatch(fetchConnector(connectorId));
  });

  if (connector) {
    return (
      <div className={classes.root}>
        <Breadcrumbs
          variant="list"
          elements={[
            { label: t('Catalog') },
            {
              label: t('Connectors'),
              link: '/admin/integrations/catalog',
            },
            {
              label: connector.connector_title,
              current: true,
            },
          ]}
        />
        <div className="clearfix" />
        <Routes>
          <Route path="" element={errorWrapper(ConnectorDetails)()} />
          {/* Not found */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    );
  }
  return <Loader />;
};

export default Index;
