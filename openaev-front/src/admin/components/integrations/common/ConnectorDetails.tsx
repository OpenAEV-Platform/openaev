import { useContext, useState } from 'react';
import { useOutletContext } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { type CatalogContextType } from '../catalog_connectors/CatalogLayout';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import ConnectorCatalogInfo from './ConnectorCatalogInfo';
import ConnectorTitle from './ConnectorTitle';

const ConnectorDetails = () => {
  // Standard hooks
  const ability = useContext(AbilityContext);
  const { t } = useFormatter();

  const { catalogConnector, isXtmComposerUp } = useOutletContext<CatalogContextType>();

  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  if (!catalogConnector) {
    return <Loader />;
  }

  return (
    <>
      <ConnectorTitle
        connector={{
          instanceId: catalogConnector.catalog_connector_id,
          connectorName: catalogConnector.catalog_connector_title,
          connectorType: catalogConnector.catalog_connector_type,
          connectorLogoName: `connector-logo-${catalogConnector.catalog_connector_id}`,
          connectorLogoUrl: `/api/images/catalog/connectors/logos/${catalogConnector.catalog_connector_logo_url}`,
          connectorDescription: catalogConnector.catalog_connector_short_description,
          isExternal: catalogConnector.catalog_connector_manager_supported,
          isVerified: true,
          connectorUseCases: catalogConnector.catalog_connector_use_cases,
          connectorInstancesCount: catalogConnector.instance_deployed_count,
        }}
        detailsTitle
        showDeployButton={ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
        onDeployBtnClick={onOpenCreateConnectorInstanceDrawer}
      />
      <CreateConnectorInstanceDrawer
        open={openCreateConnectorInstanceDrawer}
        catalogConnectorId={catalogConnector.catalog_connector_id}
        catalogConnectorSlug={catalogConnector.catalog_connector_slug}
        onClose={onCloseCreateConnectorInstanceDrawer}
        connectorType={catalogConnector.catalog_connector_type}
        disabled={!isXtmComposerUp && catalogConnector.catalog_connector_manager_supported}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
      />
      <ConnectorCatalogInfo catalogConnector={catalogConnector} />
    </>
  );
};

export default ConnectorDetails;
