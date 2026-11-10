import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

interface Props {
  /** e.g. "Before · v2.0.0" */
  beforeLabel: string;
  /** e.g. "After · v3.0.0" */
  afterLabel: string;
}

const SIGN = {
  removed: {
    sign: '−',
    color: 'var(--icon-error)',
  },
  added: {
    sign: '+',
    color: 'var(--icon-success)',
  },
} as const;

// "Before · v2.0.0": the word in the secondary colour, the version (what the reader compares) in bold.
const keyText = (label: string) => {
  const cut = label.lastIndexOf(' · ');
  if (cut < 0) return <Text variant="content-compact" className="text-default-secondary">{label}</Text>;
  return (
    <Text variant="content-compact" className="text-default-secondary">
      {label.slice(0, cut + 3)}
      <span className="text-default-primary" style={{ fontWeight: 600 }}>{label.slice(cut + 3)}</span>
    </Text>
  );
};

/**
 * The key of a before / after: "− Before · v2.0.0   + After · v3.0.0", shown once above all the changed
 * fields of a version (Law of Common Region: one key for the whole group, not repeated on every field),
 * each sign beside its own version (Law of Proximity) and 16px between the two keys.
 */
const DiffLegend: FunctionComponent<Props> = ({ beforeLabel, afterLabel }) => (
  <div style={{
    display: 'flex',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 16,
  }}
  >
    {([['removed', beforeLabel], ['added', afterLabel]] as const).map(([kind, label]) => (
      <span
        key={kind}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
        }}
      >
        <span
          aria-hidden
          style={{
            color: SIGN[kind].color,
            fontWeight: 600,
          }}
        >
          {SIGN[kind].sign}
        </span>
        {keyText(label)}
      </span>
    ))}
  </div>
);

export default DiffLegend;
