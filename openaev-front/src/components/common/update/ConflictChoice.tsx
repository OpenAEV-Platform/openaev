import { Text } from '@filigran/design-system';
import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { type ReactNode } from 'react';

export type ConflictSide = 'theirs' | 'mine';

interface Props {
  title: ReactNode;
  theirsLabel: ReactNode;
  mineLabel: ReactNode;
  theirs: ReactNode;
  mine: ReactNode;
  selected?: ConflictSide;
  onSelect: (side: ConflictSide) => void;
  // Under the two sides, e.g. a third way out.
  footer?: ReactNode;
}

// One conflict of an update: both versions side by side, a click keeps one.
const ConflictChoice = ({ title, theirsLabel, mineLabel, theirs, mine, selected, onSelect, footer }: Props) => {
  const side = (value: ConflictSide, label: ReactNode, content: ReactNode) => {
    const active = selected === value;
    return (
      <button
        type="button"
        aria-pressed={active}
        onClick={() => onSelect(value)}
        style={{
          all: 'unset',
          boxSizing: 'border-box',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          padding: 12,
          minWidth: 0,
          cursor: 'pointer',
          borderRadius: 4,
          border: `1px solid ${active ? 'var(--color-filigran-brand-primary)' : 'var(--border-elevation-subtle-soft)'}`,
        }}
      >
        <span style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          {active
            ? <CheckCircleOutlined fontSize="small" style={{ color: 'var(--color-filigran-brand-primary)' }} />
            : <RadioButtonUncheckedOutlined fontSize="small" style={{ color: 'var(--text-default-secondary)' }} />}
          <Text variant="content-base">{label}</Text>
        </span>
        {content}
      </button>
    );
  };
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
    }}
    >
      <Text variant="title-xs">{title}</Text>
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 12,
      }}
      >
        {side('theirs', theirsLabel, theirs)}
        {side('mine', mineLabel, mine)}
      </div>
      {footer}
    </div>
  );
};

export default ConflictChoice;
