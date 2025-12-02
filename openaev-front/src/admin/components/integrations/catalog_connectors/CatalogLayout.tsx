import { Outlet, useParams } from 'react-router';

import { fetchCatalogConnectors, fetchConnector } from '../../../../actions/catalog/catalog-actions';
import { getCatalogConnectorSelector, getCatalogConnectorsSelector } from '../../../../actions/selectors';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useSelectorHelper } from '../../../../store';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

const CatalogLayout = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { connectorId } = useParams() as { connectorId: CatalogConnectorOutput['catalog_connector_id'] };

  const connector = useSelectorHelper(state => getCatalogConnectorSelector(connectorId, state));
  const catalogConnectors = useSelectorHelper(getCatalogConnectorsSelector);

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors());
    if (connectorId) {
      dispatch(fetchConnector(connectorId));
    }
  });

  const breadcrumbElements
    = connectorId && connector
      ? [
          { label: t('Catalog') },
          {
            label: t('Connectors'),
            link: '/admin/integrations/catalog',
          },
          {
            label: connector.catalog_connector_title,
            current: true,
          },
        ]
      : [
          { label: t('Catalog') },
          {
            label: t('Connectors'),
            link: '/admin/integrations/catalog',
            current: true,
          },
        ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={breadcrumbElements}
      />
      <Outlet context={{
        connector,
        catalogConnectors,
      }}
      />
    </>
  );
};

export default CatalogLayout;
