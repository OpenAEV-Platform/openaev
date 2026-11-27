import { Radio, RadioGroup, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useId } from 'react';

import { type ScenarioUpdateInput } from '../../../utils/api-types';

// How the user settles a conflict, for a payload or a scenario: the server's own names.
export type MergeChoice = NonNullable<ScenarioUpdateInput['scenario_update_choices']>[string];
export type ConflictSide = Exclude<MergeChoice, 'EDIT'>;

interface Props {
  title: ReactNode;
  appliedLabel: string;
  localLabel: string;
  applied: ReactNode;
  local: ReactNode;
  selected?: MergeChoice;
  onSelect: (side: ConflictSide) => void;
}

// One conflict of an update: both values side by side, one to keep.
const ConflictChoice = ({ title, appliedLabel, localLabel, applied, local, selected, onSelect }: Props) => {
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
        {side('TAKE_APPLIED', appliedLabel, applied)}
        {side('KEEP_MINE', localLabel, local)}
      </RadioGroup>
    </div>
  );
};

export default ConflictChoice;
