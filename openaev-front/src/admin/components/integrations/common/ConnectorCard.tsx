import { Card, CardActionArea, CardContent, Chip, Typography } from '@mui/material';
import type { SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnector, type ConnectorInstanceOutput } from '../../../../utils/api-types';
import ConnectorStatus from './ConnectorStatus';
import ConnectorTitle from './ConnectorTitle';
import DeployButton from './DeployButton';

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
    alignItems: 'center',
  },
}));

export type ConnectorMainInfo = {
  instanceId?: string;
  connectorName: string;
  connectorType: CatalogConnector['catalog_connector_type'];
  connectorLogoName: string;
  connectorLogoUrl?: string;
  connectorDescription?: string;
  lastUpdatedAt?: string;
  isExternal?: boolean;
  isVerified?: boolean;
  connectorUseCases?: string[];
  connectorInstancesCount?: number;
  connectorCurrentStatus?: ConnectorInstanceOutput['connector_instance_current_status'];
};

type ConnectorCardProps = {
  cardActionUrl: string;
  showStatusOrLastUpdatedAt?: boolean;
  isNotClickable?: boolean;
  connector: ConnectorMainInfo;
  onDeployBtnClick?: (e: SyntheticEvent) => void;
};

const ConnectorCard = ({
  connector,
  cardActionUrl,
  showStatusOrLastUpdatedAt = false,
  isNotClickable = false,
  onDeployBtnClick,
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
            {showStatusOrLastUpdatedAt && connector.connectorCurrentStatus === null
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
            {showStatusOrLastUpdatedAt && connector.connectorCurrentStatus !== null && <ConnectorStatus variant={connector.connectorCurrentStatus} /> }
            {onDeployBtnClick
              && <DeployButton onDeployBtnClick={onDeployBtnClick} deploymentCount={connector.connectorInstancesCount ?? 0} />}
          </div>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default ConnectorCard;
