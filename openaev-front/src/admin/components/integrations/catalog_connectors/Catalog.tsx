import { Grid } from '@mui/material';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchCatalogConnectors } from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CatalogConnector } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import CatalogFilters from './CatalogFilters';
import ConnectorCard from './ConnectorCard';

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(3),
  },
}));

const Catalog = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { classes } = useStyles();

  const { catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({ catalogConnectors: helper.getCatalogConnectors() }));
  const [filteredConnectors, setFilteredConnectors] = useState(catalogConnectors);

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
      <div className={classes.content}>
        <CatalogFilters
          connectors={catalogConnectors}
          onFiltered={setFilteredConnectors}
        />
        <Grid container={true} spacing={3}>
          {filteredConnectors.map((connector: CatalogConnector) => {
            return (
              <ConnectorCard key={connector.connector_id} connector={connector} />
            );
          })}
        </Grid>
      </div>
    </>
  );
};

export default Catalog;
