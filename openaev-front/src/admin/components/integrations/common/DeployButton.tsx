import { Button, Chip, Icon } from '@filigran/design-system';
import { RocketLaunchOutlined } from '@mui/icons-material';
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
  /**
   * Card footer (Figma Design_System_2026, node 8461:13814): a primary button without the rocket, disabled
   * without the Enterprise Edition. The deployed chip moves to the start of the footer (DeployedChip).
   */
  inCard?: boolean;
}

// "Deployed" / "N deployed": the connector already runs on the connected
// instances. Shown in full, so no tooltip.
export const DeployedChip = ({ count }: { count: number }) => {
  const { t } = useFormatter();
  return (
    <Chip
      startIcon={<Icon name="circle-check" size={16} aria-hidden />}
      label={count > 1 ? String(t('{count} deployed', { count })) : t('Deployed')}
      severity="low"
    />
  );
};

const DeployButton = ({ onDeployBtnClick, style = {}, buttonStyle, deploymentCount, size = 'md', inCard = false }: Props) => {
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

  if (inCard) {
    // Card footer: without the Enterprise Edition the button is disabled and
    // the EE chip, drawn inside it after the label, stays clickable (it opens
    // the Enterprise Edition dialog). A disabled button swallows the clicks of
    // what it contains, so the chip is a sibling laid over the space an
    // invisible twin reserves in the button's end slot.
    return (
      <div style={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        ...style,
      }}
      >
        <Button
          type="button"
          priority="primary"
          size={size}
          style={buttonStyle}
          disabled={!isEnterpriseEdition}
          endIcon={isEnterpriseEdition ? null : (
            <span aria-hidden style={{ visibility: 'hidden' }}>
              <Chip label={t('EE')} severity="ee" size="sm" />
            </span>
          )}
          onClick={onDeployClickAction}
        >
          {t('Deploy')}
        </Button>
        {!isEnterpriseEdition && (
          <span style={{
            position: 'absolute',
            right: 8,
            top: '50%',
            transform: 'translateY(-50%)',
            display: 'inline-flex',
          }}
          >
            <EEChip clickable featureDetectedInfo={t('Connectors deployment')} />
          </span>
        )}
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      ...style,
    }}
    >
      {deploymentCount > 0 && <DeployedChip count={deploymentCount} />}
      <Button
        type="button"
        priority={isEnterpriseEdition ? 'primary' : 'secondary'}
        size={size}
        style={buttonStyle}
        startIcon={isEnterpriseEdition ? null : <RocketLaunchOutlined fontSize="small" />}
        onClick={onDeployClickAction}
      >
        {t('Deploy')}
        {!isEnterpriseEdition && <EEChip />}
      </Button>
    </div>
  );
};

export default DeployButton;
