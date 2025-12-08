import { VerifiedOutlined } from '@mui/icons-material';
import { Button, Chip, Tooltip, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { updateRequestedStatus } from '../../../../actions/connector_instances/connector-instance-actions';
import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstance, type UpdateConnectorInstanceRequestedStatus } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';
import { type ConnectorMainInfo } from './ConnectorCard';
import ConnectorPopover from './ConnectorPopover';

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
  instanceCurrentStatus?: ConnectorInstance['connector_instance_current_status'];
  showDeployButton?: boolean;
  showUpdateButtons?: boolean;
  onDeployBtnClick?: () => void;
  disabledUpdateButtons?: boolean;
};

const ConnectorTitle = ({
  connector,
  detailsTitle = false,
  instanceCurrentStatus,
  showDeployButton = false,
  showUpdateButtons = false,
  disabledUpdateButtons = false,
  onDeployBtnClick = () => {},
}: ConnectorHeaderProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const onUpdateRequestedStatusClick = () => {
    if (connector.instanceId) {
      const newStatus: UpdateConnectorInstanceRequestedStatus = { connector_instance_requested_status: instanceCurrentStatus === 'started' ? 'stopping' : 'starting' };
      dispatch(updateRequestedStatus(connector.instanceId, newStatus));
    }
  };

  const onDeployClickAction = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onDeployBtnClick();
    }
  };

  return (
    <div className={classes.content}>
      <img
        src={connector.connectorLogoUrl}
        alt={connector.connectorLogoName}
        className={classes.img}
      />
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
            { instanceCurrentStatus && (
              <Chip
                variant="filled"
                className={classes.chipVerified}
                style={instanceCurrentStatus == 'started' ? colorStyles.green : colorStyles.red}
                label={instanceCurrentStatus == 'started' ? t('Started') : t('Stopped')}
              />
            )}
            {showUpdateButtons && connector?.instanceId && (
              <ConnectorPopover
                connectorInstanceId={connector.instanceId}
                connectorName={connector.connectorName}
                disabled={disabledUpdateButtons}
              />
            )}
            {showDeployButton && (
              <Button
                variant="outlined"
                sx={{
                  marginLeft: 'auto',
                  color: isEnterpriseEdition ? 'primary' : 'action.disabled',
                  borderColor: isEnterpriseEdition ? 'primary' : 'action.disabledBackground',
                }}
                size="small"
                onClick={onDeployClickAction}
                endIcon={isEnterpriseEdition ? <></> : <span><EEChip /></span>}
              >
                {t('Deploy')}
              </Button>
            )}
            {showUpdateButtons && (
              <Button
                variant="outlined"
                color={instanceCurrentStatus == 'started' ? 'error' : 'success'}
                size="small"
                onClick={onUpdateRequestedStatusClick}
                disabled={disabledUpdateButtons}
              >
                {instanceCurrentStatus == 'started' ? t('Stop') : t('Start')}
              </Button>
            )}
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
