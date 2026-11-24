import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import VersionsTabNote from './VersionsTabNote';

interface Props {
  usageCount: number;
  kept: boolean;
  onToggle?: () => void;
}

const OlderVersionsNote: FunctionComponent<Props> = ({ usageCount, kept, onToggle }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();

  return (
    <VersionsTabNote
      text={kept
        ? t('Kept on their version. The card\'s banner returns with the next version.')
        : `${counted(usageCount, '1 inject still uses an older version.', '{count} injects still use older versions.')} ${t('Move them below, or keep them where they are.')}`}
      actionLabel={kept ? t('Show the banner again') : t('Keep them on their version')}
      priority={kept ? 'tertiary' : 'secondary'}
      onAction={onToggle}
    />
  );
};

export default OlderVersionsNote;
