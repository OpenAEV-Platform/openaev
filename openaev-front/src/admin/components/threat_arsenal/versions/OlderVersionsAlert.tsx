import { Alert, Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useCountLabel from '../../../../utils/hooks/useCountLabel';

interface Props {
  usageCount: number;
  kept: boolean;
  onToggle?: () => void;
}

const OlderVersionsAlert: FunctionComponent<Props> = ({ usageCount, kept, onToggle }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();

  return (
    <Alert
      severity={kept ? 'info' : 'warning'}
      title={kept
        ? t('Kept on their version. The card\'s banner returns with the next version.')
        : counted(usageCount, '1 inject uses an older version', '{count} injects use an older version')}
      description={kept ? undefined : t('Move them below, or keep them where they are.')}
      action={onToggle && (
        <Button size="sm" priority="secondary" onClick={onToggle}>
          {kept ? t('Show the banner again') : t('Keep them on their version')}
        </Button>
      )}
    />
  );
};

export default OlderVersionsAlert;
