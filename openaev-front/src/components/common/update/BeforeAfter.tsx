import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { tint } from '../../../utils/tint';
import { useFormatter } from '../../i18n';
import TextDiff from './TextDiff';

interface Props {
  /** Name of the field, above the two rows. */
  label: string;
  before: string;
  after: string;
  /** e.g. "Before · v1.0.0" */
  beforeLabel: string;
  /** e.g. "After · v1.1.0" */
  afterLabel: string;
}

/**
 * One changed field as two labelled rows: Before (grey) and After, the changed words on an amber
 * highlighter on both. The labels carry the meaning; colours are DS tokens and only support them.
 */
const BeforeAfter: FunctionComponent<Props> = ({ label, before, after, beforeLabel, afterLabel }) => {
  const { t } = useFormatter();
  const row = {
    display: 'grid',
    gridTemplateColumns: '112px minmax(0, 1fr)',
    gap: 12,
    alignItems: 'baseline',
  };
  const empty = <Text variant="content-caption" className="text-default-secondary" style={{ fontStyle: 'italic' }}>{t('(empty)')}</Text>;
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
        background: tint('var(--text-default-primary)', 4),
      }}
    >
      <Text variant="content-caption" className="text-default-secondary" style={{ fontWeight: 600 }}>{label}</Text>
      <div style={row}>
        <Text variant="content-caption" className="text-default-secondary">{beforeLabel}</Text>
        {before ? <TextDiff text={before} other={after} side="before" /> : empty}
      </div>
      <div style={row}>
        <Text variant="content-caption" style={{ fontWeight: 600 }}>{afterLabel}</Text>
        {after ? <TextDiff text={after} other={before} side="after" /> : empty}
      </div>
    </div>
  );
};

export default BeforeAfter;
