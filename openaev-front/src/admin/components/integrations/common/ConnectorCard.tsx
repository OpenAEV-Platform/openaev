import { Card, CardActionArea, CardContent, Chip, Grid, Typography } from '@mui/material';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnector } from '../../../../utils/api-types';
import ConnectorTitle from '../catalog_connectors/ConnectorTitle';

const useStyles = makeStyles()(theme => ({
  card: {
    position: 'relative',
    overflow: 'hidden',
    display: 'flex',
    height: '100%',
  },
  content: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
    gap: theme.spacing(2),
  },
  description: {
    color: theme.palette.grey['500'],
    maxHeight: '100px',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    display: '-webkit-box',
    WebkitLineClamp: 3,
    WebkitBoxOrient: 'vertical',
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    textTransform: 'uppercase',
    borderRadius: 4,
  },
  dotContainer: {
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  dot: {
    height: 15,
    width: 15,
    borderRadius: '50%',
    backgroundColor: theme.palette.success.main,
  },
  green: { backgroundColor: theme.palette.success.main },
  red: { backgroundColor: theme.palette.error.main },
  footer: {
    marginTop: 'auto',
    display: 'flex',
    justifyContent: 'space-between',
  },
}));

export type ConnectorMainInfo = {
  connectorName: string;
  connectorType: CatalogConnector['catalog_connector_type'];
  connectorLogoName: string;
  connectorLogoUrl?: string;
  connectorDescription?: string;
  lastUpdatedAt?: string;
  isExternal?: boolean;
  isVerified?: boolean;
  connectorUseCases?: string[];
};

type ConnectorCardProps = {
  cardActionUrl: string;
  showLastUpdatedAt?: boolean;
  isNotClickable?: boolean;
  connector: ConnectorMainInfo;
};

const ConnectorCard = ({
  connector,
  cardActionUrl,
  showLastUpdatedAt = false,
  isNotClickable = false,
}: ConnectorCardProps) => {
  const { classes } = useStyles();
  const { t, nsdt } = useFormatter();

  return (
    <Card className={classes.card} variant="outlined">
      <CardActionArea
        component={Link}
        to={cardActionUrl}
        disabled={isNotClickable}
      >
        <CardContent className={classes.content}>
          <ConnectorTitle connector={connector} />
          {connector.connectorDescription && (
            <Typography className={classes.description}>
              {connector.connectorDescription}
            </Typography>
          )}
          <div className={classes.footer}>
            <Chip
              variant="outlined"
              className={classes.chipInList}
              color="default"
              label={connector.isExternal ? t('External') : t('Built-in')}
            />
            {showLastUpdatedAt
              && (
                <div className={classes.dotContainer}>
                  <div
                    className={`${classes.dot} ${connector.lastUpdatedAt ? classes.green : classes.red}`}
                  />
                  <Typography variant="h4" style={{ margin: 0 }}>
                    {`${t('Updated at')} ${nsdt(connector.lastUpdatedAt)}`}
                  </Typography>
                </div>
              )}
          </div>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default ConnectorCard;
