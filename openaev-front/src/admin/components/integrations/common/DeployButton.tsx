import { CheckCircleOutlined } from '@mui/icons-material';
import { Button, Chip, Tooltip } from '@mui/material';
import { alpha } from '@mui/material/styles';
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
    // The button may live inside a CardActionArea link: never let the click
    // bubble up and trigger a navigation (would close the EE dialog).
    e.preventDefault();
    e.stopPropagation();
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onDeployBtnClick(e);
    }
  };

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      ...style,
    }}
    >
      {deploymentCount > 0 && (
        <Tooltip title={t('This connector has {count} deployed instance(s). Manage them from the Deployed tab.', { count: deploymentCount })}>
          <Chip
            icon={<CheckCircleOutlined sx={{ fontSize: 14 }} />}
            label={deploymentCount > 1 ? t('{count} deployed', { count: deploymentCount }) : t('Deployed')}
            size="small"
            variant="outlined"
            sx={theme => ({
              'height': 24,
              'fontSize': 11,
              'fontWeight': 600,
              'borderRadius': 1,
              'color': theme.palette.success.main,
              'borderColor': alpha(theme.palette.success.main, 0.4),
              'backgroundColor': alpha(theme.palette.success.main, 0.08),
              '& .MuiChip-icon': { color: theme.palette.success.main },
            })}
          />
        </Tooltip>
      )}
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
    </div>
  );
};

export default DeployButton;
