import { useContext, useState } from 'react';
import { useOutletContext } from 'react-router';

import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ConnectorCatalogInfo from '../common/ConnectorCatalogInfo';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import ConnectorTitle from './ConnectorTitle';

type CatalogContextType = { connector: CatalogConnectorOutput };

const ConnectorDetails = () => {
  // Standard hooks
  const ability = useContext(AbilityContext);

  const { connector } = useOutletContext<CatalogContextType>();

  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  return (
    <>
      <ConnectorTitle
        connector={{
          connectorName: connector.catalog_connector_title,
          connectorType: connector.catalog_connector_type,
          connectorLogoName: `connector-logo-${connector.catalog_connector_id}`,
          connectorLogoUrl: `/api/images/catalog/connectors/logos/${connector.catalog_connector_logo_url}`,
          connectorDescription: connector.catalog_connector_short_description,
          isExternal: connector.catalog_connector_manager_supported,
          isVerified: true,
          connectorUseCases: connector.catalog_connector_use_cases,
        }}
        detailsTitle
        showDeployButton={ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
        onDeployBtnClick={onOpenCreateConnectorInstanceDrawer}
      />
      <CreateConnectorInstanceDrawer
        open={openCreateConnectorInstanceDrawer}
        catalogConnectorId={connector.catalog_connector_id}
        catalogConnectorSlug={connector.catalog_connector_slug}
        onClose={onCloseCreateConnectorInstanceDrawer}
        connectorType={connector.catalog_connector_type}
      />
      <ConnectorCatalogInfo catalogConnector={connector} />
    </>
  );
};

export default ConnectorDetails;
