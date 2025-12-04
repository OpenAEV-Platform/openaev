import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useOutletContext } from 'react-router';

import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import CatalogFilters from './CatalogFilters';
import ConnectorCard from '../common/ConnectorCard';

type CatalogContextType = { catalogConnectors: CatalogConnectorOutput[] };

const Catalog = () => {
  // Standard hooks
  const theme = useTheme();

  const { catalogConnectors } = useOutletContext<CatalogContextType>();

  const [filteredConnectors, setFilteredConnectors] = useState<CatalogConnectorOutput[]>(catalogConnectors);

  return (
    <>
      <CatalogFilters
        connectors={catalogConnectors}
        onFiltered={setFilteredConnectors}
      />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
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
                }}
                cardActionUrl={`/admin/integrations/catalog/${connector.catalog_connector_id}`}
              />
            </Grid>
          );
        })}
      </Grid>
    </>
  );
};

export default Catalog;
