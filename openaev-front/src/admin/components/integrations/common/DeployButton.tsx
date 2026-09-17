import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
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
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip
              startIcon={<CheckCircleOutlined sx={{ fontSize: 14 }} />}
              label={deploymentCount > 1 ? String(t('{count} deployed', { count: deploymentCount })) : t('Deployed')}
              severity="low"
            />
          </TooltipTrigger>
          <TooltipContent>{t('This connector has {count} deployed instance(s). Manage them from the Deployed tab.', { count: deploymentCount })}</TooltipContent>
        </Tooltip>
      )}
      <Button
        // Same anatomy as the OpenCTI marketplace CTA: compact 26px button,
        // sentence-case label, contained when EE is active, outlined with the
        // rocket icon + EE chip otherwise - never greyed-out.
        variant={isEnterpriseEdition ? 'contained' : 'outlined'}
        sx={{
          'height': 26,
          'textTransform': 'none',
          // The marker is a flex item of the end slot: centred on the label, not on a line box.
          '& .MuiButton-endIcon': { alignItems: 'center' },
        }}
        size="small"
        onClick={onDeployClickAction}
        startIcon={isEnterpriseEdition ? null : <RocketLaunchOutlined />}
        endIcon={isEnterpriseEdition ? null : <EEChip />}
      >
        {t('Deploy')}
      </Button>
    </div>
  );
};

export default DeployButton;
