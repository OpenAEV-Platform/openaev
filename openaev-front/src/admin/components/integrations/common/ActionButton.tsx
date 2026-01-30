import { Button } from '@mui/material';
import type { CSSProperties } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

interface Props {
  onUpdate: () => void;
  disabled: boolean;
  status?: 'starting' | 'stopping';
  style?: CSSProperties;
}

const ActionButton = ({ onUpdate, disabled, status, style }: Props) => {
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
  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      {
        status === 'starting' ? (
          <Button
            variant="outlined"
            color="error"
            size="small"
            onClick={onUpdate}
            disabled={disabled}
          >
            {t('Stop')}
          </Button>
        )
          : (
              <Button
                variant={isEnterpriseEdition ? 'contained' : 'outlined'}
                sx={{
                  color: isEnterpriseEdition ? 'primary' : 'action.disabled',
                  borderColor: isEnterpriseEdition ? 'primary' : 'action.disabledBackground',
                }}
                size="small"
                onClick={onClickAction}
                endIcon={isEnterpriseEdition ? null : <span><EEChip /></span>}
                disabled={disabled}
              >
                { t('Start')}
              </Button>
            )
      }
    </div>
  );
};

export default ActionButton;
