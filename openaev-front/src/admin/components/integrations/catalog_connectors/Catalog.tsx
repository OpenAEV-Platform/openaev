import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useState } from 'react';
import { useOutletContext } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import ConnectorCard from '../common/ConnectorCard';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import CatalogFilters from './CatalogFilters';
import { type CatalogContextType } from './CatalogLayout';

const Catalog = () => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const { catalogConnectors, isXtmComposerUp } = useOutletContext<CatalogContextType>();
  const [filteredConnectors, setFilteredConnectors] = useState<CatalogConnectorOutput[]>(catalogConnectors);

  const [selectedConnector, setSelectedConnector] = useState<CatalogConnectorOutput>();
  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  const onDeployBtnClick = (e: SyntheticEvent, catalogConnector: CatalogConnectorOutput) => {
    e.preventDefault();
    e.stopPropagation();
    setSelectedConnector(catalogConnector);
    onOpenCreateConnectorInstanceDrawer();
  };

  return (
    <div style={{
      display: 'grid',
      gap: theme.spacing(2),
    }}
    >
      <CatalogFilters
        connectors={catalogConnectors}
        onFiltered={setFilteredConnectors}
      />
      <Grid container={true} spacing={3}>
        {filteredConnectors.map((connector: CatalogConnectorOutput) => {
          return (
            <Grid key={connector.catalog_connector_id} size={{ xs: 4 }}>
              <ConnectorCard
                connector={{
                  connectorName: connector.catalog_connector_title,
                  connectorType: connector.catalog_connector_type,
                  connectorLogoName: `connector-logo-${connector.catalog_connector_id}`,
                  connectorLogoUrl: `/api/images/catalog/connectors/logos/${connector.catalog_connector_logo_url}`,
                  connectorDescription: connector.catalog_connector_short_description,
                  isExternal: connector.catalog_connector_manager_supported,
                  isVerified: true,
                  connectorUseCases: connector.catalog_connector_use_cases,
                  connectorInstancesCount: connector.instance_deployed_count,
                }}
                cardActionUrl={`/admin/integrations/catalog/${connector.catalog_connector_id}`}
                onDeployBtnClick={e => onDeployBtnClick(e, connector)}
              />
            </Grid>
          );
        })}
      </Grid>
      {selectedConnector && openCreateConnectorInstanceDrawer && (
        <CreateConnectorInstanceDrawer
          open={openCreateConnectorInstanceDrawer}
          catalogConnectorId={selectedConnector.catalog_connector_id}
          catalogConnectorSlug={selectedConnector.catalog_connector_slug}
          onClose={onCloseCreateConnectorInstanceDrawer}
          connectorType={selectedConnector.catalog_connector_type}
          disabled={!isXtmComposerUp && selectedConnector.catalog_connector_manager_supported}
          disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: selectedConnector.catalog_connector_type.toLowerCase() })}
        />
      )}
    </div>
  );
};

export default Catalog;
