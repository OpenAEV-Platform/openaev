import { Badge, Button, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
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

  const onDeployClickAction = (e: SyntheticEvent) => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onDeployBtnClick(e);
    }
  };
  return (
    <Tooltip title={t('Can not deploy more than one instance')}>
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
          disabled={deploymentCount > 0}
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
    </Tooltip>

  );
};

export default DeployButton;
