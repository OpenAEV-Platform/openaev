import { Card, CardActionArea, CardContent, Chip, Grid, Typography } from '@mui/material';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import ConnectorTitle from './ConnectorTitle';

const useStyles = makeStyles()(theme => ({
  card: {
    position: 'relative',
    overflow: 'hidden',
    height: '100%',
  },
  area: {
    height: '100%',
    width: '100%',
  },
  content: {
    height: '100%',
    width: '100%',
    display: 'grid',
    gridTemplateRows: 'auto auto 1fr auto',
    gap: theme.spacing(2),
  },
  description: {
    color: theme.palette.grey['500'],
    gridRow: 2,
  },
  chipInList: {
    gridRow: 4,
    justifySelf: 'start',
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    alignSelf: 'flex-start',
    textTransform: 'uppercase',
    borderRadius: 4,
  },
  dotGreen: {
    height: 15,
    width: 15,
    backgroundColor: theme.palette.success.main,
    borderRadius: '50%',
  },
  customizable: {
    position: 'absolute',
    top: 10,
    right: 10,
  },
}));

type ConnectorCardProps = { connector: CatalogConnectorOutput };

const ConnectorCard = ({ connector }: ConnectorCardProps) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <Grid key={connector.catalog_connector_id} size={{ xs: 4 }}>
      <Card className={classes.card} variant="outlined">
        <CardActionArea
          className={classes.area}
          component={Link}
          to={`/admin/integrations/catalog/${connector.catalog_connector_id}`}
        >
          <CardContent className={classes.content}>
            <ConnectorTitle
              connectorId={connector.catalog_connector_id}
              connectorLogo={connector.catalog_connector_logo_url}
              connectorTitle={connector.catalog_connector_title}
              connectorType={connector.catalog_connector_type}
              connectorUseCases={connector.catalog_connector_use_cases}
            />
            <Typography>
              {connector.catalog_connector_short_description}
            </Typography>
            <Chip
              variant="outlined"
              className={classes.chipInList}
              color="default"
              label={connector.catalog_connector_manager_supported ? t('External') : t('Built-in')}
            />
          </CardContent>
        </CardActionArea>
      </Card>
    </Grid>
  );
};

export default ConnectorCard;
