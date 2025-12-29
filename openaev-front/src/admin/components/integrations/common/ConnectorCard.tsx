import { Card, CardActionArea, CardContent, Chip, Tooltip, Typography } from '@mui/material';
import type { SyntheticEvent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnector } from '../../../../utils/api-types';
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
};

type ConnectorCardProps = {
  cardActionUrl: string;
  showLastUpdatedAt?: boolean;
  isNotClickable?: boolean;
  connector: ConnectorMainInfo;
  onDeployBtnClick?: (e: SyntheticEvent) => void;
  disabled?: boolean;
};

const ConnectorCard = ({
  connector,
  cardActionUrl,
  showLastUpdatedAt = false,
  isNotClickable = false,
  onDeployBtnClick,
  disabled = false,
}: ConnectorCardProps) => {
  const { classes } = useStyles();
  const { t, nsdt } = useFormatter();

  return (
    <Tooltip title={disabled ? t('Deletion is being processed') : ''} followCursor>
      <Card
        sx={{
          opacity: disabled ? 0.7 : 1,
          filter: disabled ? 'grayscale(80%)' : 'none',
        }}
        className={classes.card}
        variant={disabled ? 'elevation' : 'outlined'}
      >
        <CardActionArea
          component={Link}
          to={cardActionUrl}
          disabled={isNotClickable || disabled}
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
              {onDeployBtnClick
                && <DeployButton onDeployBtnClick={onDeployBtnClick} deploymentCount={connector.connectorInstancesCount ?? 0} />}
            </div>
          </CardContent>
        </CardActionArea>
      </Card>
    </Tooltip>
  );
};

export default ConnectorCard;
