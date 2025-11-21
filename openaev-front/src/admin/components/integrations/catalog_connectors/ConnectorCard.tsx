import { VerifiedOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Chip, Grid, Tooltip, Typography } from '@mui/material';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnector } from '../../../../utils/api-types';
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
  content: { padding: 20 },

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

type ConnectorCardProps = { connector: CatalogConnector };

const ConnectorCard = ({ connector }: ConnectorCardProps) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  return (
    <Grid key={connector.connector_id} size={{ xs: 4 }}>
      <Card classes={{ root: classes.card }} variant="outlined">
        <CardActionArea
          classes={{ root: classes.area }}
          component={Link}
          to={`/admin/integrations/catalog/${connector.connector_id}`}
        >
          <CardContent className={classes.content}>
            <div className={classes.customizable}>
              <Tooltip title={t('Verified')}>
                <VerifiedOutlined color="success" />
              </Tooltip>
            </div>
            <ConnectorTitle
              connectorId={connector.connector_id}
              connectorLogo={connector.catalog_connector_logo_url}
              connectorTitle={connector.connector_title}
              connectorType={connector.catalog_connector_type}
            />
            <div>
              {connector.catalog_connector_short_description}
            </div>

          </CardContent>
        </CardActionArea>
      </Card>
    </Grid>
  );
};

export default ConnectorCard;
