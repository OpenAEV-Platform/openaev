import { Button, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { type CSSProperties, type SyntheticEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../../common/entreprise_edition/EEChip';

interface Props {
  onDeployBtnClick: (e: SyntheticEvent) => void;
  style?: CSSProperties;
  deploymentCount: number;
  /** 'sm' in the card grid, where the footer shares its row with the use-case chips. */
  size?: 'sm' | 'md';
  buttonStyle?: CSSProperties;
}

const DeployButton = ({ onDeployBtnClick, style = {}, buttonStyle, deploymentCount, size = 'md' }: Props) => {
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
      <Button type="button" priority={isEnterpriseEdition ? 'primary' : 'secondary'} size={size} style={buttonStyle} startIcon={isEnterpriseEdition ? null : <RocketLaunchOutlined fontSize="small" />} onClick={onDeployClickAction}>
        {t('Deploy')}
        {!isEnterpriseEdition && <EEChip />}
      </Button>
    </div>
  );
};

export default DeployButton;
