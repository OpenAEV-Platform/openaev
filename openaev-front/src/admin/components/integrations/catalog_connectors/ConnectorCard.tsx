import { VerifiedOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Grid, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
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
    width: '100%',
    height: '100%',
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    marginLeft: theme.spacing(1),
  },
  content: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
  },
  chipInList: {
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
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();

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
            />
            <Typography style={{ color: theme.palette.grey['500'] }}>
              {connector.catalog_connector_short_description}
            </Typography>

          </CardContent>
        </CardActionArea>
      </Card>
    </Grid>
  );
};

export default ConnectorCard;
