import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import {
  fetchConnector,
  fetchUndeployedCatalogConnectors,
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
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';

export type CatalogContextType = {
  catalogConnectors: CatalogConnectorOutput[];
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
};

const CatalogLayout = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const { catalogConnectorId } = useParams() as { catalogConnectorId: CatalogConnectorOutput['catalog_connector_id'] };
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  const { catalogConnector, catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({
    catalogConnector: helper.getCatalogConnector(catalogConnectorId),
    catalogConnectors: helper.getUnDeployedCatalogConnectors(),
  }));

  useDataLoader(() => {
    dispatch(fetchUndeployedCatalogConnectors()).finally(() => setLoading(false));
    if (catalogConnectorId) {
      dispatch(fetchConnector(catalogConnectorId)).finally(() => setLoading(false));
    }
  });
  useEffect(() => {
    isXtmComposerIsReachable().then(({ data }) => {
      setIsXtmComposerUp(data);
    });
  }, []);

  const breadcrumbElements = catalogConnectorId
    ? [
        { label: t('Catalog') },
        {
          label: t('Connectors'),
          link: '/admin/integrations/catalog',
        },
        {
          label: catalogConnector?.catalog_connector_title || 'Loading...',
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
      {loading && <Loader />}
      {!isEnterpriseEdition
        && <Alert variant="outlined" style={{ marginBottom: theme.spacing(2) }} severity="info">{t('The deployment from this catalog requires an Enterprise Edition license.')}</Alert>}
      {isEnterpriseEdition && !isXtmComposerUp && !catalogConnectorId
        && (
          <Alert
            severity="warning"
            style={{ marginBottom: theme.spacing(2) }}
          >
            {t('Some deployment requires the installation of our')}
            &nbsp;
            <a
              href="https://docs.openaev.io/latest/deployment/ecosystem/integration-manager/overview/"
              target="_blank"
              rel="noreferrer"
            >
              {t('Integration Manager')}
            </a>
          </Alert>
        )}
      {isEnterpriseEdition && !isXtmComposerUp && catalogConnectorId && catalogConnector?.catalog_connector_manager_supported
        && (
          <Alert severity="warning" style={{ marginBottom: theme.spacing(2) }}>
            {t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
          </Alert>
        )}
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
