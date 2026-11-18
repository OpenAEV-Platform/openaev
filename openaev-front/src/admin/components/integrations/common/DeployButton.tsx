import { Button, Chip, Icon } from '@filigran/design-system';
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
   * Card footer: the same button as the connector page header, so the two read alike. Only the deployed
   * chip moves, to the start of the footer (DeployedChip).
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

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      ...style,
    }}
    >
      {/* In a card, the deployed chip sits at the start of the footer instead. */}
      {!inCard && deploymentCount > 0 && <DeployedChip count={deploymentCount} />}
      <Button
        type="button"
        priority={isEnterpriseEdition ? 'primary' : 'secondary'}
        size={size}
        style={buttonStyle}
        onClick={onDeployClickAction}
      >
        {t('Deploy')}
        {!isEnterpriseEdition && <EEChip />}
      </Button>
    </div>
  );
};

export default DeployButton;
