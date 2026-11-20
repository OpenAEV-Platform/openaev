import { Radio, RadioGroup, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useId } from 'react';

import TextDiff from './TextDiff';

export type ConflictSide = 'theirs' | 'mine';

interface Props {
  title: ReactNode;
  theirsLabel: string;
  mineLabel: string;
  theirs: string;
  mine: string;
  selected?: ConflictSide;
  onSelect: (side: ConflictSide) => void;
}

// One conflict of an update: both values, the words that differ highlighted, one to keep.
const ConflictChoice = ({ title, theirsLabel, mineLabel, theirs, mine, selected, onSelect }: Props) => {
  const titleId = useId();
  const theme = useTheme();
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
      >
        <Radio value="theirs" label={theirsLabel} description={<TextDiff text={theirs} other={mine} />} />
        <Radio value="mine" label={mineLabel} description={<TextDiff text={mine} other={theirs} />} />
      </RadioGroup>
    </div>
  );
};

export default ConflictChoice;
