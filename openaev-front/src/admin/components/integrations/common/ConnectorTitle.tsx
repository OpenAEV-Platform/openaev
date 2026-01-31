import { HelpCenterOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Chip, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { updateRequestedStatus } from '../../../../actions/connector_instances/connector-instance-actions';
import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstanceOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import ActionButton from './ActionButton';
import { type ConnectorMainInfo } from './ConnectorCard';
import ConnectorPopover from './ConnectorPopover';
import ConnectorStatus from './ConnectorStatus';
import DeployButton from './DeployButton';

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'grid',
    gridTemplateColumns: 'auto 1fr',
    columnGap: theme.spacing(2),
    rowGap: theme.spacing(0.5),
    alignItems: 'start',
    width: '100%',
  },
  img: {
    gridRow: 'span 2',
    width: 60,
    height: 60,
    borderRadius: 4,
  },
  firstLine: {
    display: 'flex',
    overflow: 'hidden',
    gap: theme.spacing(2),
  },
  autoMarginLeft: { marginLeft: 'auto' },
  cardTitle: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxHeight: '100%',
  },
  pageTitle: {
    whiteSpace: 'normal',
    overflow: 'visible',
    textOverflow: 'unset',
    margin: '0px',
  },
  chipInList: {
    margin: theme.spacing(0.25),
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    justifySelf: 'start',
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  chipVerified: {
    padding: theme.spacing(2),
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  verifiedOutlined: {
    position: 'absolute',
    top: 10,
    right: 10,
  },
}));

type ConnectorHeaderProps = {
  connector: ConnectorMainInfo;
  detailsTitle?: boolean;
  instanceCurrentStatus?: ConnectorInstanceOutput['connector_instance_current_status'];
  instanceRequestedStatus?: ConnectorInstanceOutput['connector_instance_requested_status'];
  showDeployButton?: boolean;
  showUpdateButtons?: boolean;
  onDeployBtnClick?: () => void;
  disabledUpdateButtons?: boolean;
};

const ConnectorTitle = ({
  connector,
  detailsTitle = false,
  instanceCurrentStatus,
  instanceRequestedStatus,
  showDeployButton = false,
  showUpdateButtons = false,
  disabledUpdateButtons = false,
  onDeployBtnClick = () => {
  },
}: ConnectorHeaderProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const onUpdateRequestedStatusClick = () => {
    if (!connector.instanceId) return;

    // If we're already in a transition (starting or stopping),
    // user intention is to reverse the current requested action.
    let next: 'starting' | 'stopping';

    if (instanceRequestedStatus === 'starting') {
      next = 'stopping';
    } else if (instanceRequestedStatus === 'stopping') {
      next = 'starting';
    } else {
      next = instanceCurrentStatus === 'started' ? 'stopping' : 'starting';
    }
    dispatch(updateRequestedStatus(connector.instanceId, { connector_instance_requested_status: next }));
  };

  const [isStatusLoading, setIsStatusLoading] = useState<boolean>(false);

  useEffect(() => {
    const isLoading = (instanceCurrentStatus === 'started' && instanceRequestedStatus === 'stopping')
      || (instanceCurrentStatus === 'stopped' && instanceRequestedStatus === 'starting');

    setIsStatusLoading(isLoading);
  }, [instanceCurrentStatus, instanceRequestedStatus]);

  return (
    <div className={classes.content}>
      {connector.connectorLogoName.includes('dummy') ? (
        <HelpCenterOutlined className={classes.img} />
      )
        : (
            <img
              src={connector.connectorLogoUrl}
              alt={connector.connectorLogoName}
              className={classes.img}
            />
          )}

      <div className={classes.firstLine}>
        <Tooltip title={connector.connectorName}>
          <Typography
            variant="h1"
            className={detailsTitle ? classes.pageTitle : classes.cardTitle}
          >
            {connector.connectorName}
          </Typography>
        </Tooltip>

        {connector.isVerified && detailsTitle && (
          <>
            <Chip
              variant="filled"
              className={classes.chipVerified}
              style={colorStyles.green}
              icon={<VerifiedOutlined color="success" />}
              label={t('Verified')}
            />
            {
              instanceCurrentStatus
              && <ConnectorStatus variant={isStatusLoading ? 'loading' : instanceCurrentStatus} />
            }

            <div style={{
              display: 'flex',
              gap: theme.spacing(1),
              alignItems: 'center',
              marginLeft: 'auto',
            }}
            >
              {showUpdateButtons && connector?.instanceId && (
                <ConnectorPopover
                  connectorInstanceId={connector.instanceId}
                  connectorName={connector.connectorName}
                />
              )}

              {showDeployButton ? (
                <DeployButton
                  onDeployBtnClick={onDeployBtnClick}
                  deploymentCount={connector.connectorInstancesCount ?? 0}
                />
              )
                : (
                    <ActionButton
                      onUpdate={onUpdateRequestedStatusClick}
                      disabled={showUpdateButtons || disabledUpdateButtons}
                      status={instanceRequestedStatus}
                    />
                  )}
            </div>
          </>
        )}
      </div>

      <div>
        <Chip
          variant="outlined"
          className={classes.chipInList}
          color="primary"
          label={connector.connectorType}
        />
        {connector.connectorUseCases && connector.connectorUseCases.map((useCase: string) => (
          <Chip
            key={useCase}
            variant="outlined"
            className={classes.chipInList}
            color="default"
            label={useCase}
          />
        ))}
      </div>
      {connector.isVerified && (
        <Tooltip title={t('Verified')} className={classes.verifiedOutlined}>
          <VerifiedOutlined color="success" />
        </Tooltip>
      )}
    </div>
  );
};

export default ConnectorTitle;
