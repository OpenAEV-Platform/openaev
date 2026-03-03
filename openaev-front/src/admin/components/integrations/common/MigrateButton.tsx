import { Button, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

interface Props {
  onMigrateBtnClick: (e: SyntheticEvent) => void;
  style?: CSSProperties;
}

const MigrateButton = ({ onMigrateBtnClick, style = {} }: Props) => {
  const { t } = useFormatter();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const onMigrateClickAction = (e: SyntheticEvent) => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onMigrateBtnClick(e);
    }
  };
  return (
    <Tooltip title={t('Migrate a manually-deployed connector to the XTM composer to manage its settings from the interface')}>
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
          onClick={onMigrateClickAction}
          endIcon={isEnterpriseEdition ? null : <span><EEChip /></span>}
        >
          {t('Migrate')}
        </Button>
      </div>
    </Tooltip>

  );
};

export default MigrateButton;
