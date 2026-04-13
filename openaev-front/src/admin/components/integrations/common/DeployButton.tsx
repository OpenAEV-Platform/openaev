import { Badge, Button } from '@mui/material';
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
    <div style={style}>
      <Badge
        badgeContent={deploymentCount}
        color="warning"
      >
        <Button
          variant={isEnterpriseEdition ? 'contained' : 'outlined'}
          sx={{
            color: isEnterpriseEdition ? 'primary' : 'action.disabled',
            borderColor: isEnterpriseEdition ? 'primary' : 'action.disabledBackground',
          }}
          size="small"
          onClick={onDeployClickAction}
          endIcon={isEnterpriseEdition ? null : <span><EEChip /></span>}
        >
          {t('Deploy')}
        </Button>
      </Badge>
    </div>
  );
};

export default DeployButton;
