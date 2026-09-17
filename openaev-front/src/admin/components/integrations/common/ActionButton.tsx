import { Button } from '@filigran/design-system';

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
      <Button variant="destructive" priority="secondary" size="sm" onClick={onUpdate} disabled={disabled}>
        {t('Stop')}
      </Button>
    );
  }

  return (
    <Button priority={isEnterpriseEdition ? 'primary' : 'secondary'} size="sm" onClick={onClickAction} disabled={disabled}>
      {t('Start')}
      {!isEnterpriseEdition && <EEChip style={{ marginLeft: 4 }} />}
    </Button>
  );
};

export default ActionButton;
