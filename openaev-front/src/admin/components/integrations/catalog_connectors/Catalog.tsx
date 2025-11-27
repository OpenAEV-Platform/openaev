import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { fetchCatalogConnectors } from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import CatalogFilters from './CatalogFilters';
import ConnectorCard from './ConnectorCard';

const Catalog = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  const { catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({ catalogConnectors: helper.getCatalogConnectors() }));
  const [filteredConnectors, setFilteredConnectors] = useState<CatalogConnectorOutput[]>(catalogConnectors);

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors());
  });

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Catalog') }, {
          label: t('Connectors'),
          current: true,
        }]}
      />
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
