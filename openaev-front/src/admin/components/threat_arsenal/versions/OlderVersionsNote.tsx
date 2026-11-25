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
  const keepLabel = versions.length === 1
    ? counted(usageCount, 'Keep it on v{version}', 'Keep them on v{version}', { version: versions[0] })
    : t('Keep them on their version');
  // One translation per note, never sentences glued together.
  let text = savedVersion
    ? counted(
        usageCount,
        'Saved as v{version}. 1 inject still uses {versions}. Move it below, or keep it where it is.',
        'Saved as v{version}. {count} injects still use {versions}. Move them below, or keep them where they are.',
        {
          version: savedVersion,
          versions: named,
        },
      )
    : counted(
        usageCount,
        '1 inject still uses {versions}. Move it below, or keep it where it is.',
        '{count} injects still use {versions}. Move them below, or keep them where they are.',
        { versions: named },
      );
  if (kept) text = t('Kept on their version. The card\'s banner returns with the next version.');

  return (
    <VersionsTabNote
      text={text}
      saved={!!savedVersion && !kept}
      actionLabel={kept ? t('Show the banner again') : keepLabel}
      priority={kept ? 'tertiary' : 'secondary'}
      onAction={onToggle}
    />
  );
};

export default OlderVersionsNote;
