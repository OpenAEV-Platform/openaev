import { Radio, RadioGroup, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useId } from 'react';

export type ConflictSide = 'theirs' | 'mine';

interface Props {
  title: ReactNode;
  theirsLabel: string;
  mineLabel: string;
  theirs: ReactNode;
  mine: ReactNode;
  selected?: ConflictSide;
  onSelect: (side: ConflictSide) => void;
}

// One conflict of an update: both values side by side, one to keep.
const ConflictChoice = ({ title, theirsLabel, mineLabel, theirs, mine, selected, onSelect }: Props) => {
  const titleId = useId();
  const theme = useTheme();
  const side = (value: ConflictSide, label: string, content: ReactNode) => (
    <div style={{
      minWidth: 0,
      padding: theme.spacing(1.5),
      borderRadius: 'var(--radius-sm)',
      border: `1px solid ${selected === value ? 'var(--color-filigran-brand-primary)' : 'var(--border-elevation-subtle-soft)'}`,
    }}
    >
      <Radio value={value} label={label} description={content} />
    </div>
  );
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-base-medium" id={titleId}>{title}</Text>
      <RadioGroup
        aria-labelledby={titleId}
        value={selected ?? ''}
        onValueChange={value => onSelect(value as ConflictSide)}
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: theme.spacing(1.5),
        }}
      >
        {side('theirs', theirsLabel, theirs)}
        {side('mine', mineLabel, mine)}
      </RadioGroup>
    </div>
  );
};

export default ConflictChoice;
