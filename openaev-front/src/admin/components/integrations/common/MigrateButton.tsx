import { Button, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
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
    // The button may live inside a CardActionArea / row link: never let the
    // click bubble up and trigger a navigation (would close the EE dialog).
    e.preventDefault();
    e.stopPropagation();
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Connectors deployment'));
      openEnterpriseEditionDialog();
    } else {
      onMigrateBtnClick(e);
    }
  };
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div style={{
          ...style,
          position: 'relative',
        }}
        >
          <Button type="button" priority={isEnterpriseEdition ? 'primary' : 'secondary'} size="sm" onClick={onMigrateClickAction}>
            {t('Migrate')}
            {!isEnterpriseEdition && <EEChip style={{ marginLeft: 4 }} />}
          </Button>
        </div>
      </TooltipTrigger>
      <TooltipContent>{t('Migrate a manually-deployed connector to the Integration Manager to manage its settings from the interface')}</TooltipContent>
    </Tooltip>

  );
};

export default MigrateButton;
