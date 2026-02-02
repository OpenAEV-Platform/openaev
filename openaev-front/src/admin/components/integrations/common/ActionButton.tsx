import { Button } from '@mui/material';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

interface Props {
  onUpdate: () => void;
  disabled: boolean;
  status?: 'starting' | 'stopping';
}

const ActionButton = ({ onUpdate, disabled, status }: Props) => {
  const { t } = useFormatter();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const onClickAction = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Starting connectors'));
      openEnterpriseEditionDialog();
    } else {
      onUpdate();
    }
  };

  if (status === 'starting') {
    return (
      <Button
        variant="outlined"
        color="error"
        onClick={onUpdate}
        disabled={disabled}
      >
        {t('Stop')}
      </Button>
    );
  }

  return (
    <Button
      variant={isEnterpriseEdition ? 'contained' : 'outlined'}
      sx={{
        color: isEnterpriseEdition ? 'primary' : 'action.disabled',
        borderColor: isEnterpriseEdition ? 'primary' : 'action.disabledBackground',
      }}
      onClick={onClickAction}
      endIcon={isEnterpriseEdition ? null : <span><EEChip /></span>}
      disabled={disabled}
    >
      { t('Start')}
    </Button>
  );
};

export default ActionButton;
