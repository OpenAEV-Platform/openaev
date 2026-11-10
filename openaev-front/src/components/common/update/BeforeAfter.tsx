import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';
import CodeDiff from './CodeDiff';
import TextDiff from './TextDiff';
import { diffRuns } from './textDiffRuns';

interface Props {
  /** Name of the field, above the two rows. */
  label: string;
  before: string;
  after: string;
  /** e.g. "Before · v1.0.0" */
  beforeLabel: string;
  /** e.g. "After · v1.1.0" */
  afterLabel: string;
  /** Code (a command, arguments, prerequisites): shown line by line with + and −, like GitHub. */
  code?: boolean;
  /** Show the Before / After key on a code diff (default); off when a key is shown once above. */
  legend?: boolean;
}

/**
 * One changed field as two labelled rows: Before (grey) and After, the changed words highlighted on
 * both. When words were only removed, the After row says what went ("removed: …"), so a change is
 * always visible on After. The labels carry the meaning; colours are DS tokens as shipped.
 * Code, and any value on several lines, is compared line by line instead ({@link CodeDiff}): the
 * same in the Versions tab, the History tab and rolled-back versions.
 */
const BeforeAfter: FunctionComponent<Props> = ({ label, before, after, beforeLabel, afterLabel, code = false, legend = true }) => {
  const { t } = useFormatter();
  if (code || before.includes('\n') || after.includes('\n')) {
    return <CodeDiff label={label} before={before} after={after} beforeLabel={beforeLabel} afterLabel={afterLabel} legend={legend} />;
  }
  const row = {
    display: 'grid',
    gridTemplateColumns: '128px minmax(0, 1fr)',
    gap: 12,
    alignItems: 'baseline',
  };
  // Words of Before that are not in After; shown on After when After itself has nothing new.
  const removed = diffRuns(before, after).filter(run => run.changed).map(run => run.token.trim());
  const onlyRemoved = !!before && removed.length > 0 && !diffRuns(after, before).some(run => run.changed);
  const empty = <Text variant="content-compact" className="text-default-secondary" style={{ fontStyle: 'italic' }}>{t('(empty)')}</Text>;
  return (
    <div
      role="group"
      aria-label={label}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        padding: 12,
        borderRadius: 4,
        border: '1px solid var(--border-elevation-subtle-soft)',
      }}
    >
      <Text variant="content-base-bold">{label}</Text>
      <div style={row}>
        <Text variant="content-compact" className="text-default-secondary">{beforeLabel}</Text>
        {before ? <TextDiff text={before} other={after} side="before" size="base" /> : empty}
      </div>
      <div style={row}>
        <Text variant="content-compact-bold">{afterLabel}</Text>
        <div>
          {after ? <TextDiff text={after} other={before} side="after" size="base" /> : empty}
          {onlyRemoved && (
            <Text variant="content-compact" className="text-default-secondary">
              {t('removed: {text}', { text: removed.join(' … ') })}
            </Text>
          )}
        </div>
      </div>
    </div>
  );
};

export default BeforeAfter;
