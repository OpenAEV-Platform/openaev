import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import VersionsTabNote from './VersionsTabNote';

interface Props {
  usageCount: number;
  // The older versions the injects are still on, named in the note: "1 inject still uses v1.8.0".
  versions: string[];
  // Right after a save that added this version: the note starts with it.
  savedVersion?: string;
  kept: boolean;
  onToggle?: () => void;
}

const OlderVersionsNote: FunctionComponent<Props> = ({ usageCount, versions, savedVersion, kept, onToggle }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const named = versions.map(version => `v${version}`).join(', ');
  const stillUse = usageCount === 1
    ? t('1 inject still uses {versions}.', { versions: named })
    : t('{count} injects still use {versions}.', {
        count: String(usageCount),
        versions: named,
      });
  const keepLabel = versions.length === 1
    ? t(usageCount === 1 ? 'Keep it on v{version}' : 'Keep them on v{version}', { version: versions[0] })
    : t('Keep them on their version');
  const saved = savedVersion && !kept ? `${t('Saved as v{version}.', { version: savedVersion })} ` : '';

  return (
    <VersionsTabNote
      text={kept
        ? t('Kept on their version. The card\'s banner returns with the next version.')
        : `${saved}${stillUse} ${counted(usageCount, 'Move it below, or keep it where it is.', 'Move them below, or keep them where they are.')}`}
      saved={!!saved}
      actionLabel={kept ? t('Show the banner again') : keepLabel}
      priority={kept ? 'tertiary' : 'secondary'}
      onAction={onToggle}
    />
  );
};

export default OlderVersionsNote;
