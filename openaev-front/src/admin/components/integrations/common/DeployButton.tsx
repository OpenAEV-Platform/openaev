import { Badge, Button, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../../utils/utils';
import EEChip from '../../common/entreprise_edition/EEChip';

interface Props {
  onDeployBtnClick: (e: SyntheticEvent) => void;
  style?: CSSProperties;
  deploymentCount: number;
}

const DeployButton = ({ onDeployBtnClick, style = {}, deploymentCount }: Props) => {
  const { t } = useFormatter();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const multiConnectorEnabled = isFeatureEnabled('MULTI_CONNECTOR');
  const isDeployDisabled = !multiConnectorEnabled && deploymentCount > 0;

  const onDeployClickAction = (e: SyntheticEvent) => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onDeployBtnClick(e);
    }
  };

  const button = (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <Button
        variant={isEnterpriseEdition ? 'contained' : 'outlined'}
        sx={{
          color: isEnterpriseEdition ? 'primary' : 'action.disabled',
          borderColor: isEnterpriseEdition ? 'primary' : 'action.disabledBackground',
        }}
        size="small"
        onClick={onDeployClickAction}
        disabled={isDeployDisabled}
        endIcon={isEnterpriseEdition ? null : <span><EEChip /></span>}
      >
        {t('Deploy')}
      </Button>
      <Badge
        badgeContent={deploymentCount}
        color="warning"
        sx={{
          position: 'absolute',
          top: '10px',
          right: 0,
        }}
      />
    </div>
  );

  if (isDeployDisabled) {
    return (
      <Tooltip title={t('Can not deploy more than one instance')}>
        {button}
      </Tooltip>
    );
  }

  return button;
};

export default DeployButton;
