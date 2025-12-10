import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useOutletContext } from 'react-router';

import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import CatalogFilters from './CatalogFilters';
import ConnectorCard from './ConnectorCard';

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
            <ConnectorCard key={connector.catalog_connector_id} connector={connector} />
          );
        })}
      </Grid>
    </>
  );
};

export default Catalog;
