import { Outlet, useParams } from 'react-router';

import { fetchCatalogConnectors, fetchConnector } from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

const CatalogLayout = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { connectorId } = useParams() as { connectorId: CatalogConnectorOutput['catalog_connector_id'] };

  const { connector, catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({
    connector: helper.getCatalogConnector(connectorId),
    catalogConnectors: helper.getCatalogConnectors(),
  }));

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors());
    if (connectorId) {
      dispatch(fetchConnector(connectorId));
    }
  });

  const breadcrumbElements
    = connectorId
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
