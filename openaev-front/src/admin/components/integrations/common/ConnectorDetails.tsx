import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useOutletContext } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { type CatalogContextType } from '../catalog_connectors/CatalogLayout';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import ConnectorCatalogInfo from './ConnectorCatalogInfo';
import ConnectorDetailHero from './ConnectorDetailHero';
import DeployButton from './DeployButton';

/** Catalog connector detail page (the "Available" tab drill-down). */
const ConnectorDetails = () => {
  const theme = useTheme();
  const ability = useAbility();
  const { t } = useFormatter();

  const { catalogConnector, isXtmComposerUp } = useOutletContext<CatalogContextType>();

  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);

  if (!catalogConnector) {
    return <Loader />;
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <ConnectorDetailHero
        title={catalogConnector.catalog_connector_title}
        logoSrc={catalogConnector.catalog_connector_logo_url
          ? `/api/images/catalog/connectors/logos/${catalogConnector.catalog_connector_logo_url}`
          : undefined}
        type={catalogConnector.catalog_connector_type}
        useCases={catalogConnector.catalog_connector_use_cases}
        verified={catalogConnector.catalog_connector_verified === true}
        external={catalogConnector.catalog_connector_manager_supported === true}
        actions={ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS) && (
          <DeployButton
            onDeployBtnClick={() => setOpenCreateConnectorInstanceDrawer(true)}
            deploymentCount={catalogConnector.instance_deployed_count ?? 0}
          />
        )}
      />
      <ConnectorCatalogInfo catalogConnector={catalogConnector} />
      <CreateConnectorInstanceDrawer
        open={openCreateConnectorInstanceDrawer}
        catalogConnectorId={catalogConnector.catalog_connector_id}
        catalogConnectorSlug={catalogConnector.catalog_connector_slug}
        connectorTitle={catalogConnector.catalog_connector_title}
        onClose={() => setOpenCreateConnectorInstanceDrawer(false)}
        connectorType={catalogConnector.catalog_connector_type}
        disabled={!isXtmComposerUp && catalogConnector.catalog_connector_manager_supported}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
      />
    </div>
  );
};

export default ConnectorDetails;
