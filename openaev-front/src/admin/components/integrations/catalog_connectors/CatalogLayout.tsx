import { useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import {
  fetchCatalogConnectors,
  fetchConnector,
  isXtmComposerIsReachable,
} from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

export type CatalogContextType = {
  catalogConnectors: CatalogConnectorOutput[];
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
};

const CatalogLayout = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const { catalogConnectorId } = useParams() as { catalogConnectorId: CatalogConnectorOutput['catalog_connector_id'] };
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  const { catalogConnector, catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({
    catalogConnector: helper.getCatalogConnector(catalogConnectorId),
    catalogConnectors: helper.getCatalogConnectors(),
  }));

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors()).finally(() => setLoading(false));
    if (catalogConnectorId) {
      dispatch(fetchConnector(catalogConnectorId)).finally(() => setLoading(false));
    }
  });
  useEffect(() => {
    isXtmComposerIsReachable().then(({ data }) => {
      setIsXtmComposerUp(data);
    });
  }, []);

  // Keep the trail short: "Integrations / <connector>" - the Integrations
  // crumb links back to the catalog tab the user came from.
  const breadcrumbElements = catalogConnectorId
    ? [
        {
          label: t('Integrations'),
          link: '/admin/integrations/available',
        },
        {
          label: catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        {
          label: t('Integrations'),
          current: true,
        },
      ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={breadcrumbElements}
      />
      {loading && <Loader />}
      <Outlet context={{
        catalogConnector,
        catalogConnectors,
        isXtmComposerUp,
      }}
      />
    </>
  );
};

export default CatalogLayout;
